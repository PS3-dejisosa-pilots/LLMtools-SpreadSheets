package moros.asf.tools.forspreadsheets.utils

import kotlinx.serialization.json.JsonPrimitive
import moros.asf.tools.forspreadsheets.ps.PowerShellLauncher
import org.springframework.stereotype.Component
import java.io.File


object ExcelFile {

    fun toExcelTable( values: List<List<String?>> ): ExcelTable {
        if( values.isEmpty() ) return ExcelTable()
        val headers = values[0] as List<String>
        val data = values.drop(1).map { row ->
            row.map { value ->
                if( value == null ) null else JsonPrimitive(value)
            }
        }.map { row ->
            headers.zip(row).toMap()
        }
        return ExcelTable(headers, data)
    }

    // 二つのExcelTableを比較して、差分を取得する
    fun diff( value1: List<Map<String,JsonPrimitive?>>, value2: List<Map<String,JsonPrimitive?>> ): List<Map<String,Pair<JsonPrimitive?, JsonPrimitive?>>?> {
        val keys = value1.flatMap { it.keys }.union(value2.flatMap { it.keys }).distinct()
        return value1.zip(value2).map { (row1, row2) ->
            keys.map { key ->
                val v1 = row1[key]
                val v2 = row2[key]
                if( v1 == v2 ) null else key to (v1 to v2)
            }.filterNotNull().toMap()
        }.map {
            if( it.isEmpty() ) null else it
        }
    }

    // ExcelTableをList<List<String?>>に変換する
    fun toList( table: ExcelTable ): List<List<String?>> {
        val headers = table.headers
        val data = table.data.map { row ->
            headers.map { header ->
                row[header]?.content
            }
        }
        return listOf(headers) + data
    }
}

data class ExcelTable (
    val headers: List<String> = emptyList(),
    val data: List<Map<String,JsonPrimitive?>> = emptyList(),
)

@Component
class ExcelApplication (
    private val shell: PowerShellLauncher,
){

    fun open( file: File ){
        val ps = File("bat\\OpenExcelFile.ps1")
        val commands = "powershell.exe -File ${ps.absolutePath} ${file.absolutePath}"
        Runtime.getRuntime().exec(commands)
    }

    fun listWorkbookAndActiveSheets(): List<Pair<String,String>> {
        return shell.eval("bat\\GetExcelWindowList.ps1").map {
            it.split("\t")
        }.map {
            it[0] to it[1]
        }
    }

    fun listSheetsOfSelectedWorkbook( workbookName: String ): List<String> {
        return shell.eval("bat\\GetSheetNames.ps1 \"$workbookName\"")
    }

    fun activeSheet( workbook: String ): String {
        return listWorkbookAndActiveSheets().find { it.first == workbook }?.second ?: ""
    }

    fun selectSheet( workbook: String, sheet: String ): Boolean {
        return shell.eval("bat\\SetSheetOnTop.ps1 \"$workbook\" \"$sheet\"")[0].toBoolean()
    }

    fun getSelectedArea( workbook: String? = null, sheet: String? = null ): List<List<String?>> {

        var command: String = "bat\\GetSelectedAreaData.ps1"

        if( !workbook.isNullOrEmpty() && !sheet.isNullOrEmpty() ){
            command += " \"${workbook}\" \"${sheet}\""
        }

        return shell.eval(command).map {
            it.split("\t").map {
                if( it == "<<__NULL__>>" ) null else it
            }.map {
                it?.replace("<<__RETURN__>>", "\n")
            }
        }
    }

    fun setSelectedArea( values: List<List<String?>> ): Boolean {
        return shell.eval("bat\\SetSelectedAreaData.ps1", values.map {
            it.map {
                it?.replace("\n", "<<__RETURN__>>") ?: "<<__NULL__>>"
            } .joinToString("\t")
        })[0].toBoolean()
    }

    fun getActiveWorkbookAndSheet(): Pair<String?,String?> {
        fun String?.orEmptyToNull(): String? = if (this.isNullOrEmpty()) null else this

        return shell.eval("bat\\GetActivateWorkbookAndSheet.ps1").let { lines ->
            // EXCEL ファイルが開かれてないとき、[null, null] を返す
            if( lines.isNullOrEmpty() ){
                return null to null
            }

            lines[0].split(",")
        }.let {
            // 空文字も null に変換して戻す
            it[0].orEmptyToNull() to it[1].orEmptyToNull()
        }
    }

    // ExcelTable が空かチェックする
    fun isExcelTableEmpty( table: ExcelTable ): Boolean {
        var flg = true

        table.headers.forEach loop@{ column ->
            if( ! column.isNullOrEmpty() ){
                flg = false
                return@loop
            }
        }

        return flg
    }
}

