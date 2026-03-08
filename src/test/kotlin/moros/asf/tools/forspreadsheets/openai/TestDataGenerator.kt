package moros.asf.tools.forspreadsheets.openai

import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/**
 * テストデータ生成ユーティリティ
 *
 * 20列のExcelデータを模したテストデータ
 */
object TestDataGenerator {

    val HEADERS = listOf(
        "id", "name", "age", "email", "phone",
        "address", "city", "country", "postalCode", "department",
        "position", "salary", "joinDate", "manager", "status",
        "notes", "projectId", "skills", "certifications", "emergencyContact"
    )

    private val japaneseNames = listOf(
        "田中太郎", "鈴木花子", "佐藤一郎", "高橋美咲", "渡辺健太",
        "伊藤さくら", "山本大輔", "中村由美", "小林誠", "加藤愛"
    )

    private val departments = listOf(
        "営業部", "開発部", "人事部", "経理部", "総務部",
        "マーケティング部", "カスタマーサポート部", "企画部"
    )

    private val positions = listOf(
        "部長", "課長", "主任", "一般社員", "契約社員",
        "シニアエンジニア", "エンジニア", "マネージャー"
    )

    private val cities = listOf(
        "東京", "大阪", "名古屋", "福岡", "札幌",
        "横浜", "神戸", "京都", "広島", "仙台"
    )

    private val statuses = listOf("在籍", "休職中", "退職予定")

    private val skills = listOf(
        "Java/Kotlin/Python", "JavaScript/TypeScript", "Go/Rust",
        "プロジェクトマネジメント", "データ分析", "UI/UXデザイン",
        "営業スキル", "財務会計", "人事労務"
    )

    /**
     * 小規模データセット（10行）を生成
     * 通常量のテスト用
     */
    fun generateSmallDataset(): List<Map<String, JsonPrimitive?>> {
        return generateDataset(10)
    }

    /**
     * 大規模データセット（2000行）を生成
     * トークンオーバーフロー確認用
     */
    fun generateLargeDataset(): List<Map<String, JsonPrimitive?>> {
        return generateDataset(2000)
    }

    /**
     * 出力途中切れ継続テスト用データセット（30行）を生成。
     *
     * notes フィールドにテキストを持たせて出力トークンを増大させる。
     * @TestPropertySource に spring.ai.openai.chat.options.max-completion-tokens=800 を設定すると
     * 1コールで約5行返却されるため、継続ループを確実にテストできる。
     * （ChatServiceContinuationTest 参照）
     */
    fun generateOutputTruncationDataset(): List<Map<String, JsonPrimitive?>> {
        return (1..30).map { i ->
            val nameIndex   = (i - 1) % japaneseNames.size
            val deptIndex   = (i - 1) % departments.size
            val posIndex    = (i - 1) % positions.size
            val cityIndex   = (i - 1) % cities.size
            val statusIndex = (i - 1) % statuses.size
            val skillIndex  = (i - 1) % skills.size

            mapOf(
                "id"               to JsonPrimitive(i),
                "name"             to JsonPrimitive(japaneseNames[nameIndex]),
                "age"              to JsonPrimitive(22 + (i % 40)),
                "email"            to JsonPrimitive("${japaneseNames[nameIndex].take(2)}${i}@example.com"),
                "phone"            to JsonPrimitive("090-${2000 + i}-${3000 + i}"),
                "address"          to JsonPrimitive("${cities[cityIndex]}区${i}丁目${(i % 20) + 1}-${(i % 10) + 1}"),
                "city"             to JsonPrimitive(cities[cityIndex]),
                "country"          to JsonPrimitive("日本"),
                "postalCode"       to JsonPrimitive("${100 + (i % 900)}-${2000 + i}"),
                "department"       to JsonPrimitive(departments[deptIndex]),
                "position"         to JsonPrimitive(positions[posIndex]),
                "salary"           to JsonPrimitive(300000 + i * 1000),
                "joinDate"         to JsonPrimitive("20${15 + (i % 10)}-${String.format("%02d", (i % 12) + 1)}-01"),
                "manager"          to JsonPrimitive("管理者${(i % 10) + 1}"),
                "status"           to JsonPrimitive(statuses[statusIndex]),
                // notes: 1文で出力トークンを適度に増大 (~30-50 tokens/row)
                "notes"            to JsonPrimitive(
                    "社員ID:${i}、入社${(i % 15) + 1}年目、${positions[posIndex]}、評価:S。"
                ),
                "projectId"        to JsonPrimitive("PRJ-${2000 + i}"),
                "skills"           to JsonPrimitive(skills[skillIndex]),
                "certifications"   to JsonPrimitive("情報処理安全確保支援士, AWS認定ソリューションアーキテクト"),
                "emergencyContact" to JsonPrimitive("03-${2000 + i}-${3000 + i}")
            )
        }
    }

    /**
     * 複雑なデータセット（5行、null値・特殊文字含む）を生成
     * シリアライゼーション検証用
     */
    fun generateComplexDataset(): List<Map<String, JsonPrimitive?>> {
        return (1..5).map { i ->
            mapOf(
                "id"               to JsonPrimitive(i),
                "name"             to if (i % 2 == 0) JsonPrimitive("テスト\n改行${i}") else null,
                "age"              to JsonPrimitive(20 + i * 5),
                "email"            to JsonPrimitive("test${i}@example.com"),
                "phone"            to if (i == 3) null else JsonPrimitive("090-1234-${2000 + i}"),
                "address"          to JsonPrimitive("東京都渋谷区#${i}-${i * 2}"),
                "city"             to JsonPrimitive("東京 (特別区)"),
                "country"          to JsonPrimitive("日本🇯🇵"),
                "postalCode"       to JsonPrimitive("150-000${i}"),
                "department"       to if (i == 2) null else JsonPrimitive("開発部・第${i}課"),
                "position"         to JsonPrimitive("Senior Engineer"),
                "salary"           to JsonPrimitive(500000 + i * 20000),
                "joinDate"         to JsonPrimitive("2020-0${i}-15"),
                "manager"          to if (i == 4) null else JsonPrimitive("Manager${i}"),
                "status"           to JsonPrimitive("在籍"),
                "notes"            to JsonPrimitive("特記事項:\n- 項目1\n- 項目2\n- 項目${i}"),
                "projectId"        to JsonPrimitive("PRJ-${i * 100}"),
                "skills"           to JsonPrimitive("Java, Kotlin, \"Spring Boot\""),
                "certifications"   to if (i == 1) null else JsonPrimitive("情報処理安全確保支援士"),
                "emergencyContact" to JsonPrimitive("03-1234-567${i}")
            )
        }
    }

    /**
     * 指定行数のデータセットを生成
     */
    private fun generateDataset(rows: Int): List<Map<String, JsonPrimitive?>> {
        return (1..rows).map { i ->
            val nameIndex   = (i - 1) % japaneseNames.size
            val deptIndex   = (i - 1) % departments.size
            val posIndex    = (i - 1) % positions.size
            val cityIndex   = (i - 1) % cities.size
            val statusIndex = (i - 1) % statuses.size
            val skillIndex  = (i - 1) % skills.size

            mapOf(
                "id"               to JsonPrimitive(i),
                "name"             to JsonPrimitive(japaneseNames[nameIndex]),
                "age"              to JsonPrimitive(22 + Random.nextInt(40)),
                "email"            to JsonPrimitive("${japaneseNames[nameIndex].take(2)}${i}@example.com"),
                "phone"            to JsonPrimitive("090-${Random.nextInt(9000) + 2000}-${Random.nextInt(9000) + 2000}"),
                "address"          to JsonPrimitive("${cities[cityIndex]}${Random.nextInt(10) + 1}丁目${Random.nextInt(20) + 1}-${Random.nextInt(20) + 1}"),
                "city"             to JsonPrimitive(cities[cityIndex]),
                "country"          to JsonPrimitive("日本"),
                "postalCode"       to JsonPrimitive("${100 + Random.nextInt(900)}-${Random.nextInt(9000) + 2000}"),
                "department"       to JsonPrimitive(departments[deptIndex]),
                "position"         to JsonPrimitive(positions[posIndex]),
                "salary"           to JsonPrimitive(300000 + Random.nextInt(700000)),
                "joinDate"         to JsonPrimitive("20${15 + Random.nextInt(10)}-${String.format("%02d", Random.nextInt(12) + 1)}-${String.format("%02d", Random.nextInt(28) + 1)}"),
                "manager"          to JsonPrimitive("管理者${Random.nextInt(10) + 1}"),
                "status"           to JsonPrimitive(statuses[statusIndex]),
                "notes"            to JsonPrimitive("備考: 社員${i}の特記事項です。"),
                "projectId"        to JsonPrimitive("PRJ-${Random.nextInt(2000) + 2000}"),
                "skills"           to JsonPrimitive(skills[skillIndex]),
                "certifications"   to if (Random.nextBoolean()) JsonPrimitive("資格${Random.nextInt(5) + 1}") else null,
                "emergencyContact" to JsonPrimitive("03-${Random.nextInt(9000) + 2000}-${Random.nextInt(9000) + 2000}")
            )
        }
    }
}
