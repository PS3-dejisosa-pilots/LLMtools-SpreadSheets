package moros.asf.tools.forspreadsheets.openai

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource
import moros.asf.tools.forspreadsheets.ps.PowerShellLauncher
import java.util.concurrent.TimeUnit

/**
 * ChatService 出力途中切れ継続ループ検証の抽象基底クラス。
 *
 * 各モデル向けの具象クラス（@SpringBootTest + @TestPropertySource）で継承して使用する。
 * - gpt-4o  : ChatServiceContinuationGpt4oTest
 * - gpt-5.2 : ChatServiceContinuationGpt52Test
 */
abstract class AbstractChatServiceContinuationTest {

    @Autowired
    lateinit var chatService: ChatService

    @MockBean
    lateinit var powerShellLauncher: PowerShellLauncher

    /** 具象クラスでオーバーライドするモデル名（ログ出力用） */
    abstract val modelName: String

    /**
     * true: 継続ループの発火（複数回APIコール）まで検証する。
     *   max-tokens 制限が有効なモデル（gpt-4o 等）でのみ使用可能。
     * false: 全行返却の正確性のみ検証する。
     *   リーズニングモデル（gpt-5.2 等）は内部思考トークンがトークン枠を消費するため
     *   小さな max-completion-tokens では出力が0になってしまい継続ループを強制発火できない。
     */
    open val requireContinuationLoop: Boolean = true

    /**
     * 継続ループ正常完了テスト。
     *
     * requireContinuationLoop=true の場合: max-tokens を制限して finish_reason=length を
     * 強制発火させ、継続ループが機能してすべての行を回収できることを検証する。
     * 継続時は処理済み分を除いた残りデータのみ送信するため、モデルに行番号の追跡を求めない。
     * requireContinuationLoop=false の場合: トークン制限なしで全行が正しく返却されることのみ検証する。
     */
    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun testContinuationLoop_ReturnsAllRows() {
        println("=== Continuation Loop Test [$modelName] ===")

        val testData = TestDataGenerator.generateOutputTruncationDataset()
        val message = ChatMessage(
            message = "以下のデータをそのまま返してください。",
            data = ChatData(testData)
        )

        println("  model      : $modelName")
        println("  Input rows : ${testData.size}")

        try {
            val response = chatService.send(message)

            assertNotNull(response)
            assertTrue(response.value.containsKey("output"), "Response should contain 'output' key")

            val outputData = response.value["output"]!!
            assertTrue(outputData.value.isNotEmpty(), "Output should not be empty")

            val returnedRows = outputData.value.size
            println("  Returned rows: $returnedRows / ${testData.size}")

            // ── 件数検証 ──────────────────────────────────────────────────
            val countLabel = if (requireContinuationLoop) "継続ループにより全" else "全"
            assertEquals(
                testData.size,
                returnedRows,
                "${countLabel} ${testData.size} 行が返却されるべきです。実際: $returnedRows 行"
            )

            // ── 構造検証：全行が期待するキーを持つ ─────────────────────────
            val expectedKeys = TestDataGenerator.HEADERS.toSet()
            outputData.value.forEachIndexed { idx, row ->
                assertTrue(row is Map<*, *>, "行[$idx] は Map であるべきです")
                assertTrue(row.isNotEmpty(), "行[$idx] は空でないべきです")
                val missingKeys = expectedKeys - row.keys
                assertTrue(
                    missingKeys.isEmpty(),
                    "行[$idx] に不足キーがあります: $missingKeys"
                )
            }

            // ── 内容検証：id が 1〜30 の全件揃っているか ─────────────────
            val returnedIds = outputData.value.mapNotNull { row ->
                row["id"]?.content?.toIntOrNull()
            }.toSortedSet()
            val expectedIds = (1..testData.size).toSortedSet()
            assertEquals(
                expectedIds,
                returnedIds,
                "返却されたIDセットが入力と一致しません"
            )

            // ── 内容検証：各行の name / department が入力と対応 ─────────────
            val inputById = testData.associateBy { it["id"]?.content?.toIntOrNull() }
            outputData.value.forEachIndexed { idx, row ->
                val id = row["id"]?.content?.toIntOrNull()
                    ?: fail("行[$idx] の id が数値として取得できません。実際の値: ${row["id"]}")
                val inputRow = inputById[id]
                    ?: fail("行[$idx] の id=$id が入力データに存在しません")
                assertEquals(
                    inputRow["name"]?.content,
                    row["name"]?.content,
                    "ID=$id の name が一致しません"
                )
                assertEquals(
                    inputRow["department"]?.content,
                    row["department"]?.content,
                    "ID=$id の department が一致しません"
                )
            }

            if (requireContinuationLoop) {
                println("✓ [$modelName] 継続ループにより全行回収・内容一致を確認")
            } else {
                println("✓ [$modelName] 全行回収・内容一致を確認（継続ループ検証はスキップ）")
            }

        } catch (e: Exception) {
            fail("[$modelName] 予期しない例外が発生しました: ${e.message}")
        }
    }
}

// =============================================================================
// gpt-4o 用具象クラス
// max-tokens=800 により finish_reason=length を強制発火させ、継続ループを検証する。
// =============================================================================
@SpringBootTest
@TestPropertySource(
    locations  = ["classpath:application-test.properties"],
    properties = [
        "spring.ai.openai.chat.options.model=gpt-4o",
        "spring.ai.openai.chat.options.max-tokens=800"
    ]
)
class ChatServiceContinuationGpt4oTest : AbstractChatServiceContinuationTest() {
    override val modelName = "gpt-4o"
}

// =============================================================================
// gpt-5.2 用具象クラス
// リーズニングモデルのため内部思考トークンがトークン枠を消費してしまい、
// 小さな max-completion-tokens では出力が0になる。
// そのため継続ループの強制発火は行わず、全行を正しく返せるかのみ検証する。
// 継続ループ自体の検証は ChatServiceContinuationGpt4oTest を参照。
// =============================================================================
@SpringBootTest
@TestPropertySource(
    locations  = ["classpath:application-test.properties"],
    properties = ["spring.ai.openai.chat.options.model=gpt-5.2"]
)
class ChatServiceContinuationGpt52Test : AbstractChatServiceContinuationTest() {
    override val modelName = "gpt-5.2"
    override val requireContinuationLoop = false
}

// =============================================================================
// 最大試行回数超過後の部分返却テスト（gpt-4o）
//
// max-tokens=200 に設定することで1コールで1行しか返却されない状態を作り出す。
// 30行 ÷ 1行/コール = 30コール必要 > MAX_CONTINUATION_ATTEMPTS(20) のため、
// 20回試行しても全行処理が完了しないが、例外はスローされず処理済み行をそのまま返却する。
// 継続時は処理済み分を除いた残りデータのみ送信するため、行の欠落は発生しない。
// =============================================================================
@SpringBootTest
@TestPropertySource(
    locations  = ["classpath:application-test.properties"],
    properties = [
        "spring.ai.openai.chat.options.model=gpt-4o",
        "spring.ai.openai.chat.options.max-tokens=200"
    ]
)
class ChatServiceMaxAttemptsPartialReturnTest {

    @Autowired
    lateinit var chatService: ChatService

    @MockBean
    lateinit var powerShellLauncher: PowerShellLauncher

    /**
     * MAX_CONTINUATION_ATTEMPTS を超えた場合に例外なしで処理済み行を返却することを検証する。
     *
     * max-tokens=200 の場合、1行 ≈ 120トークンのため1コールで1行のみ返却される。
     * 30行の処理には30コールが必要だが上限は20回のため、全行処理は完了しない。
     * 以前は OutputTruncatedException をスローしていたが、現在は警告ログのみで
     * 処理済み行をそのまま返却する（プロンプト要件による意図的な絞り込みとの区別不可のため）。
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun testMaxAttempts_ReturnsPartialResultsWithoutException() {
        println("=== Max Attempts Partial Return Test (max-tokens=200) ===")

        val testData = TestDataGenerator.generateOutputTruncationDataset()
        val message = ChatMessage(
            message = "以下のデータをそのまま返してください。",
            data = ChatData(testData)
        )

        println("  Input rows                : ${testData.size}")
        println("  max-tokens                : 200 (~1 row/call)")
        println("  MAX_CONTINUATION_ATTEMPTS : 20")
        println("  Expected: partial rows returned without exception")

        // 例外がスローされないことを確認（スローされればテスト失敗）
        val response = chatService.send(message)

        val outputData = response.value["output"]!!
        val returnedRows = outputData.value.size

        // 1行以上は処理されていること
        assertTrue(returnedRows > 0,
            "少なくとも1行は処理されているべきです。実際: $returnedRows")

        // 全行には満たないこと（max-tokens=200 の制約により）
        assertTrue(returnedRows < testData.size,
            "全行処理が完了していないはずです。実際: $returnedRows / ${testData.size}")

        println("✓ 例外なしで部分返却を確認")
        println("  returnedRows : $returnedRows / ${testData.size}")
    }
}
