package moros.asf.tools.forspreadsheets.ui.common

import kotlinx.serialization.json.JsonPrimitive
import java.awt.Component
import java.awt.Color
import java.awt.Font
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer
import javax.swing.table.TableModel


// ExcelTable 用 TableModel クラス
class ExcelTableModel(
    val headers: List<String>,
    val model: List<Map<String, JsonPrimitive?>>
): AbstractTableModel() {
    override fun getRowCount(): Int = model.size
    override fun getColumnCount(): Int = headers.size
    override fun getColumnName(column: Int): String = headers[column]
    override fun getValueAt(row: Int, column: Int): Any {
        return model[row][headers[column]]?.content ?: ""
    }
    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
}


// Diff 機能付与 CellRenderer
class DiffTableCellRenderer: DefaultTableCellRenderer() {
    var tableModel: TableModel? = null

    override fun getTableCellRendererComponent( table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        if( (tableModel != null) && (table != null) ){
            val oldColCount = tableModel!!.getColumnCount()
            val oldRowCount = tableModel!!.getRowCount()

            // column = 0,1,2,...   row = 0,1,2,....
            if( (oldColCount > column) && (oldRowCount > row) ){
                val old = tableModel!!.getValueAt(row, column)

                if( value != old ){
                    foreground = Color.RED
                    font = font.deriveFont(Font.BOLD)

                } else {
                    setForeground(table!!.getForeground())
                    setFont(table!!.getFont())
                }
            }
        }

        return this
    }
}

