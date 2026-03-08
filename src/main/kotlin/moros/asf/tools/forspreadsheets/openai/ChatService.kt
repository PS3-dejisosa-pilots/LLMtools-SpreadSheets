package moros.asf.tools.forspreadsheets.openai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.stereotype.Component

@Serializable
data class RequestMessage (
    val role: String,
    val format: String,
    val requestMessage: String,
    val input: List<Map<String, JsonPrimitive?>>,
)

@Serializable
data class ChatMessage (
    val message: String,
    val data: ChatData,
)

@Serializable
@JvmInline
value class ChatData (
    val value: List<Map<String, JsonPrimitive?>>
)

@Serializable
@JvmInline
value class ChatResponse (
    val value: Map<String, ChatData>
)

// OpenAI APIのトークン制限超過時にスローされる例外
class TokenLimitExceededException(
    val dataRows: Int,
    val messageLength: Int,
    cause: Throwable
) : RuntimeException(
    "データ量がトークン制限を超えました。\n\n" +
    "現在の状況:\n" +
    "  データ行数: ${dataRows}行\n" +
    "  プロンプト文字数: ${messageLength}文字\n\n" +
    "対策:\n" +
    "  ・Excelの選択範囲を縮小してください\n" +
    "  ・不要な列を選択範囲から除外してください\n" +
    "  ・プロンプト文を短くしてください\n" +
    "  ・application.properties でより大きなコンテキストウィンドウのモデルに変更してください",
    cause
)

@Component
class ChatService(val chat: ChatModel) {

    companion object {
        private val log = LoggerFactory.getLogger(ChatService::class.java)
        private const val MAX_CONTINUATION_ATTEMPTS = 20
        private val NDJSON_BASE_PROMPT = """
            結果を改行区切りJSON（NDJSON）形式で出力してください:
            - 1行につき1つのJSONオブジェクト
            - 各行は完全で有効なJSONオブジェクトであること
            - ラッパーオブジェクト・配列の角括弧・説明文は不要
            - 追加行がある場合は、入力行をすべて出力した後に末尾へ追記すること
            - 一度にすべての行を処理できない場合は、処理できた行まで完全な形で出力して停止すること
            出力例:
            {"id":1,"name":"Taro","status":"active"}
            {"id":2,"name":"Hanako","status":"inactive"}
        """.trimIndent()

        private const val CONTINUATION_USER_PROMPT = "続きを出力してください。"
    }

    fun send(message: ChatMessage): ChatResponse {
        val allData = message.data.value
        val accumulatedRows = mutableListOf<Map<String, JsonPrimitive?>>()
        var attempts = 0

        while (accumulatedRows.size < allData.size && attempts < MAX_CONTINUATION_ATTEMPTS) {
            attempts++
            val processedCount = accumulatedRows.size

            // 常に全データを送信してモデルが全体のコンテキストを参照できるようにする。
            // 継続時は出力済み NDJSON 行を AssistantMessage として注入し、
            // 「続きを出力してください」を追加 UserMessage として送ることで、
            // モデルが自分の直前の出力末尾を確認して次の行から自然に再開できる。
            val userMessage = UserMessage(
                Json.encodeToString(RequestMessage("user", "ndjson", message.message, allData))
            )
            val messages = if (processedCount == 0) {
                listOf(SystemMessage(NDJSON_BASE_PROMPT), userMessage)
            } else {
                val accumulatedNdjson = accumulatedRows.joinToString("\n") { Json.encodeToString(it) }
                listOf(
                    SystemMessage(NDJSON_BASE_PROMPT),
                    userMessage,
                    AssistantMessage(accumulatedNdjson),
                    UserMessage(CONTINUATION_USER_PROMPT)
                )
            }

            val prompt = Prompt(messages)

            val response = try {
                chat.call(prompt)
            } catch (e: Exception) {
                if (isTokenLimitError(e)) {
                    throw TokenLimitExceededException(
                        dataRows = allData.size,
                        messageLength = message.message.length,
                        cause = e
                    )
                }
                throw e
            }

            val responseText = response.result.output.text
                ?: throw IllegalStateException("Response text is null")

            log.debug("raw response:{}", responseText)

            val parsedRows = parseNdjsonLines(responseText)
            val newRows = deduplicateRows(parsedRows, accumulatedRows)
            accumulatedRows.addAll(newRows)
            // stop が返るにもかかわらず出力が実際には不完全なケースがある
            val finishReason = response.result.metadata.finishReason?.lowercase() ?: "stop"

            val duplicateCount = parsedRows.size - newRows.size
            log.info(
                "[attempt={}/{}] finishReason={}, parsedRows={}, newRows={}, duplicates={}, accumulated={}, remaining={}",
                attempts, MAX_CONTINUATION_ATTEMPTS,
                finishReason, parsedRows.size, newRows.size, duplicateCount,
                accumulatedRows.size, allData.size - accumulatedRows.size
            )
            log.debug("parsed:\n {}", newRows.joinToString("\n  ") { Json.encodeToString(it) })

            if (newRows.isEmpty()) break
        }

        if (accumulatedRows.size < allData.size) {
            log.warn(
                "AIモデルがインプットよりも少ない行を返却しました（最大試行回数: {}）: {}/{} 行。プロンプト要件による意図的な絞り込みの可能性があるため、処理済み行をそのまま返却します。",
                MAX_CONTINUATION_ATTEMPTS, accumulatedRows.size, allData.size
            )
        }

        // 暫定対応: APIレスポンスがインプット行数を超えた場合、超過分を切り捨てる。
        // 選択範囲を超える行数を書き込もうとすると SetSelectedAreaData.ps1 が失敗するため、ここで制限する。
        val outputRows = if (accumulatedRows.size > allData.size) {
            log.warn(
                "AIモデルがインプットよりも多い行を返却しました: {}/{} 行。超過分を切り捨てます。",
                accumulatedRows.size, allData.size
            )
            accumulatedRows.take(allData.size)
        } else {
            accumulatedRows
        }

        return ChatResponse(mapOf("output" to ChatData(outputRows)))
    }

    private fun deduplicateRows(
        parsedRows: List<Map<String, JsonPrimitive?>>,
        accumulatedRows: List<Map<String, JsonPrimitive?>>
    ): List<Map<String, JsonPrimitive?>> {
        if (accumulatedRows.isEmpty() || parsedRows.isEmpty()) return parsedRows

        // parsedRows の先頭 k 行が accumulatedRows の末尾 k 行と一致する最大の k を探す
        val maxOverlap = minOf(parsedRows.size, accumulatedRows.size)
        var overlap = 0
        for (k in 1..maxOverlap) {
            val tailOfAccumulated = accumulatedRows.subList(accumulatedRows.size - k, accumulatedRows.size)
            val headOfParsed = parsedRows.subList(0, k)
            if (tailOfAccumulated == headOfParsed) {
                overlap = k
            }
        }

        if (overlap > 0) {
            log.debug("継続レスポンスの先頭 {} 行が蓄積済み末尾と重複のため除去", overlap)
        }
        return parsedRows.subList(overlap, parsedRows.size)
    }

    private fun parseNdjsonLines(ndjsonText: String): List<Map<String, JsonPrimitive?>> {
        return ndjsonText
            .lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    Json.decodeFromString<Map<String, JsonPrimitive?>>(line)
                } catch (e: Exception) {
                    null
                }
            }
    }

    private fun isTokenLimitError(e: Exception): Boolean {
        val msg = collectExceptionMessages(e)
        val hasTokenKeyword = msg.contains("token")
        val hasLimitKeyword = msg.contains("limit") || msg.contains("exceed") ||
                              msg.contains("maximum") || msg.contains("context length") ||
                              msg.contains("context_length")
        return hasTokenKeyword && hasLimitKeyword
    }

    private fun collectExceptionMessages(e: Throwable?): String = buildString {
        var current = e
        while (current != null) {
            append(current.message?.lowercase() ?: "")
            current = current.cause
        }
    }
}
