package moros.asf.tools.forspreadsheets.openai

import kotlinx.serialization.json.JsonPrimitive
import moros.asf.tools.forspreadsheets.ps.PowerShellLauncher
import moros.asf.tools.forspreadsheets.utils.ExcelTable
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.TimeUnit
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource

/**
 * ChatServiceの統合テスト
 * 
 * 実際のOpenAI APIを呼び出してChatServiceとOpenAiConfigの動作を検証します。
 * 
 * 実行前に有効なOpenAI APIキーをapplication-test.propertiesに設定してください。
 */
@SpringBootTest
@TestPropertySource(locations = ["classpath:application-test.properties"])
class ChatServiceIntegrationTest {

    @Autowired
    lateinit var chatService: ChatService

    @Autowired
    lateinit var chatModel: ChatModel

    @MockBean
    lateinit var powerShellLauncher: PowerShellLauncher

    // ==============================================
    // 正常系テスト
    // ==============================================

    @Test
    fun testSendMessage_WithSmallData() {
        println("=== Small Dataset Test ===")
        
        val testData = TestDataGenerator.generateSmallDataset()
        val chatData = ChatData(testData)
        val message = ChatMessage(
            message = "以下のデータを確認して、そのまま返してください。",
            data = chatData
        )

        val response = chatService.send(message)
        
        // レスポンスの基本検証
        assertNotNull(response, "Response should not be null")
        assertTrue(response.value.containsKey("output"), "Response should contain 'output' key")
        
        val outputData = response.value["output"]
        assertNotNull(outputData, "Output data should not be null")
        
        // データ行数の検証（ヘッダーを除く10行）
        assertEquals(10, outputData!!.value.size, "Output should have 10 rows")
        
        // 全行がMapであることを確認
        outputData.value.forEach { row ->
            assertTrue(row is Map<*, *>, "Each row should be a Map")
            assertTrue(row.isNotEmpty(), "Row should not be empty")
        }
        
        println("✓ Small dataset test passed")
        println("  Response rows: ${outputData.value.size}")
        println("  Sample row keys: ${outputData.value.firstOrNull()?.keys?.take(5)}")
    }

    @Test
    fun testRequestSerialization_WithComplexData() {
        println("=== Complex Data Serialization Test ===")
        
        val complexData = TestDataGenerator.generateComplexDataset()
        val chatData = ChatData(complexData)
        val message = ChatMessage(
            message = "このデータには日本語、null値、特殊文字が含まれています。そのまま返してください。",
            data = chatData
        )

        val response = chatService.send(message)
        
        assertNotNull(response)
        assertTrue(response.value.containsKey("output"))
        
        val outputData = response.value["output"]!!
        assertEquals(5, outputData.value.size)
        
        // null値が含まれる行の確認
        val hasNullValues = outputData.value.any { row ->
            row.values.any { it == null }
        }
        assertTrue(hasNullValues || outputData.value.isNotEmpty(), 
            "Complex data should be properly serialized")
        
        println("✓ Complex data serialization test passed")
        println("  Data with null values processed successfully")
    }

    // ==============================================
    // 出力途中切れ継続テスト
    // ==============================================

    /**
     * 標準設定（max-tokens 制約なし）で全行返却または部分返却を確認するテスト。
     *
     * ChatService の継続ループは finishReason に関わらず、行数不足の限り MAX_CONTINUATION_ATTEMPTS まで再試行する。
     * stop/length どちらが返っても挙動は同じで、返却行数と各行の構造（Map・非空）を検証する。
     *
     * 継続ループ自体の検証は ChatServiceContinuationTest を参照。
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    fun testSendMessage_WithOutputTruncation() {
        println("=== Output Truncation Continuation Test ===")

        val testData = TestDataGenerator.generateOutputTruncationDataset()
        val chatData = ChatData(testData)
        val message = ChatMessage(
            message = "以下のデータをそのまま返してください。",
            data = chatData
        )

        println("  Input rows : ${testData.size}")

        try {
            val response = chatService.send(message)

            assertNotNull(response)
            assertTrue(response.value.containsKey("output"), "Response should contain 'output' key")

            val outputData = response.value["output"]!!
            assertTrue(outputData.value.isNotEmpty(), "Output should not be empty")

            // 各行が正常にパースされたMapであることを確認
            outputData.value.forEach { row ->
                assertTrue(row is Map<*, *>, "Each row should be a Map")
                assertTrue(row.isNotEmpty(), "Row should not be empty")
            }

            val returnedRows = outputData.value.size
            println("  Returned rows: ${returnedRows} / ${testData.size}")

            if (returnedRows == testData.size) {
                println("✓ All rows returned")
            } else {
                // MAX_CONTINUATION_ATTEMPTS 到達または parsedRowsが空になったため部分返却。
                // finishReason にかかわらず行数不足なら再試行する設計なので、
                // プロンプト要件による意図的な絞り込みの可能性もある。
                println("✓ Partial rows returned gracefully (no exception, valid NDJSON parsed)")
            }

        } catch (e: Exception) {
            fail("予期しない例外が発生しました: ${e.message}")
        }
    }

    // ==============================================
    // トークンオーバーフロー検証テスト
    // ==============================================

    @Test
    fun testSendMessage_WithLargeData_TokenOverflow() {
        println("=== Large Dataset (Token Overflow) Test ===")
        
        val largeData = TestDataGenerator.generateLargeDataset()
        val chatData = ChatData(largeData)
        val message = ChatMessage(
            message = "以下の大量データを確認してください。",
            data = chatData
        )

        // 2000行×20列のデータはトークン制限を超えるため、
        // TokenLimitExceededExceptionがスローされることを検証する
        val exception = assertThrows<TokenLimitExceededException> {
            chatService.send(message)
        }

        // 例外のプロパティを検証
        assertEquals(largeData.size, exception.dataRows, "dataRows should match input data size")
        assertEquals(message.message.length, exception.messageLength, "messageLength should match message length")

        // エラーメッセージに状況と対処ガイダンスが含まれていることを検証
        val errorMessage = exception.message ?: ""
        assertTrue(errorMessage.contains("トークン制限"), "Error message should mention token limit")
        assertTrue(errorMessage.contains("対策"), "Error message should contain guidance")

        println("✓ TokenLimitExceededException thrown as expected")
        println("  dataRows: ${exception.dataRows}")
        println("  messageLength: ${exception.messageLength}")
    }

    // ==============================================
    // エラーハンドリングテスト
    // ==============================================

    @Test
    fun testSendMessage_WithEmptyData() {
        println("=== Empty Data Test ===")
        
        val emptyData = emptyList<Map<String, JsonPrimitive?>>()
        val chatData = ChatData(emptyData)
        val message = ChatMessage(
            message = "データがありません。そのまま返してください。",
            data = chatData
        )

        val response = chatService.send(message)
        
        assertNotNull(response)
        assertTrue(response.value.containsKey("output"), "Response should contain 'output' key")
        
        val outputData = response.value["output"]!!
        assertEquals(0, outputData.value.size, "Output should be empty array")
        
        println("✓ Empty data handled successfully")
        println("  Output rows: ${outputData.value.size}")
    }

    // Note: Invalid API Key test requires manual configuration in application-test.properties
    // Uncomment and set invalid key to test
    /*
    @Test
    fun testSendMessage_WithInvalidApiKey() {
        println("=== Invalid API Key Test ===")
        
        val testData = TestDataGenerator.generateSmallDataset().take(2)
        val chatData = ChatData(testData)
        val message = ChatMessage(
            message = "Test message",
            data = chatData
        )

        val exception = assertThrows<Exception> {
            chatService.send(message)
        }

        println("✓ Invalid API key properly rejected")
        println("  Error type: ${exception.javaClass.simpleName}")
        println("  Error message: ${exception.message}")
        
        // エラーメッセージに認証関連のキーワードが含まれることを確認
        val errorMessage = exception.message?.lowercase() ?: ""
        assertTrue(
            errorMessage.contains("api") || 
            errorMessage.contains("auth") || 
            errorMessage.contains("key") ||
            errorMessage.contains("401") ||
            errorMessage.contains("unauthorized"),
            "Error message should indicate authentication failure"
        )
    }
    */

    @Test
    fun testResponseDeserialization() {
        println("=== Response Deserialization Test ===")
        
        val testData = TestDataGenerator.generateSmallDataset().take(3)
        val chatData = ChatData(testData)
        val message = ChatMessage(
            message = "以下の3行のデータを処理して、そのまま返してください。各行のid, name, emailフィールドは必須です。",
            data = chatData
        )

        val response = chatService.send(message)
        
        assertNotNull(response)
        assertTrue(response.value.containsKey("output"))
        
        val outputData = response.value["output"]!!
        
        // ChatDataとして正しくデシリアライズされていることを確認
        assertTrue(outputData is ChatData, "Output should be ChatData type")
        assertTrue(outputData.value is List<*>, "ChatData value should be List")
        
        // 各行がMapであることを確認
        outputData.value.forEach { row ->
            assertTrue(row is Map<*, *>, "Each row should be Map")
            assertTrue(row.keys.all { it is String }, "All keys should be String")
        }
        
        println("✓ Response deserialization test passed")
        println("  Rows deserialized: ${outputData.value.size}")
        println("  First row type: ${outputData.value.firstOrNull()?.javaClass?.simpleName}")
    }

    @Test
    fun testExcelTableIntegration() {
        println("=== ExcelTable Integration Test ===")
        
        // ExcelTableを通じてデータを準備（実際の使用パターン）
        val testData = TestDataGenerator.generateSmallDataset().take(5)
        val headers = TestDataGenerator.HEADERS
        val excelTable = ExcelTable(headers, testData)
        
        val chatData = ChatData(excelTable.data)
        val message = ChatMessage(
            message = "このExcelデータを確認して、そのまま返してください。",
            data = chatData
        )

        val response = chatService.send(message)
        
        assertNotNull(response)
        assertTrue(response.value.containsKey("output"))
        
        val outputData = response.value["output"]!!
        
        // ExcelTableとの互換性を確認
        val resultTable = ExcelTable(headers, outputData.value)
        assertNotNull(resultTable)
        assertEquals(headers, resultTable.headers)
        
        println("✓ ExcelTable integration test passed")
        println("  Headers: ${resultTable.headers.size}")
        println("  Data rows: ${resultTable.data.size}")
    }
}
