@file:UseSerializers(RectangleSerializer::class)

package moros.asf.tools.forspreadsheets.ui

import com.formdev.flatlaf.extras.FlatSVGIcon
import jakarta.annotation.PreDestroy
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import moros.asf.tools.forspreadsheets.openai.ChatData
import moros.asf.tools.forspreadsheets.openai.ChatMessage
import moros.asf.tools.forspreadsheets.openai.ChatService
import moros.asf.tools.forspreadsheets.openai.TokenLimitExceededException
import moros.asf.tools.forspreadsheets.ui.common.ExcelTableModel
import moros.asf.tools.forspreadsheets.ui.common.AlwaysOnTopButton
import moros.asf.tools.forspreadsheets.ui.common.DiffTableCellRenderer
import moros.asf.tools.forspreadsheets.ui.common.SyncScrollBar
import moros.asf.tools.forspreadsheets.utils.*
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

@Serializable
data class SimpleChatConfig (
    var alwaysOnTop: Boolean = true,
    var windowRectangle: Rectangle? = null,
)

class ChatForExcelMainPanel(
    private val excel: ExcelApplication,
    private val chat: ChatService,
    private val window: SimpleChat
) : JPanel() {
    private val logAndDialog = LogAndDialogUtils(this::class.java)

    var workbooks : List<String> = emptyList()
        set( value ){
            field = value
            selectWorkbooks.model = DefaultComboBoxModel(value.toTypedArray())
        }
    var sheets: List<String> = emptyList()
        set( value ){
            field = value
            selectSheets.model = DefaultComboBoxModel(value.toTypedArray())
        }

    var isLocked: Boolean = false
        set( value ){
            if( field != value ){
                field = value
                selectWorkbooks.isEnabled = !value
                selectSheets.isEnabled = !value
            }
            if( locked.isSelected != value ){
                locked.isSelected = value
            }
        }

    private val alwaysOnTopButton = AlwaysOnTopButton(window)

    private val locked: JToggleButton = JToggleButton().apply {
        toolTipText = "操作対象のワークブック及びシートをプルダウンから変更できないようにします。"
        isFocusable = false
        isSelected = false
        icon = FlatSVGIcon("icons/pin_dark.svg")
        addChangeListener { evt ->
            isLocked = this@apply.isSelected
        }
    }

    private val refreshButton: JButton = JButton().apply {
        toolTipText = "現在開いているExcelファイルの一覧を再取得してプルダウンメニューの内容を更新します。"
        isFocusable = false
        icon = FlatSVGIcon("icons/refresh_dark.svg")
        addActionListener {
            reloadWorkbooksAndSheets()
        }
    }

    private val importButton: JButton = JButton().apply {
        toolTipText = "<html>現在最前面のワークブックおよびシートを操作対象として選択します。<br>さらに変更できないようピン止めを行います。"
        isFocusable = false
        icon = FlatSVGIcon("icons/moveToAnotherChangelog_dark.svg")
        addActionListener {
            // 以下の状態に対応するため、workbook or sheet を読み直す
            //   - EXCEL ファイルを開いたまたは閉じた
            //   - sheet が追加または削除された
            reloadWorkbooksAndSheets()

            locked.isSelected = true
        }
    }

    private val selectWorkbooks: JComboBox<String> = JComboBox<String>().apply {
        toolTipText = "<html>開いているワークブックの一覧より操作対象のワークブックを選択します。<br>合わせて選択したワークブックをアクティブにします。"
        addActionListener { evt ->
            val selected = selectedItem as? String ?: return@addActionListener
            sheets = excel.listSheetsOfSelectedWorkbook(selected)

            val active = excel.activeSheet(selected)
            if( active.isNotEmpty() ) {
                selectSheets.selectedItem = active
            }
        }
    }
    private val selectSheets: JComboBox<String> = JComboBox<String>().apply {
        toolTipText = "<html>選択されたワークブックのシート一覧より操作対象のシートを選択します。<br>合わせて選択したシートをアクティブにします。"
        addActionListener { evt ->
            if( selectWorkbooks.selectedItem != null && selectedItem != null ){
                excel.selectSheet(
                    selectWorkbooks.selectedItem!!.toString(),
                    selectedItem as String
                )
            }
        }
    }

    private var lastInputs: ExcelTable? = null
    private var lastOutputs: ExcelTable? = null

    private val undoButton: JButton = JButton().apply {
        toolTipText = "<html>シートの選択範囲をプロンプト実行前の状態に戻します。<br>選択範囲を変更していると誤った値で更新されます。"
        isFocusable = false
        icon = FlatSVGIcon("icons/undo_dark.svg")
        addActionListener {
            lastInputs?.let { excel.setSelectedArea(ExcelFile.toList(it)) }
        }
    }

    private val redoButton: JButton = JButton().apply {
        toolTipText = "<html>シートの選択範囲をプロンプト実行後の状態に戻します。<br>選択範囲を変更していると誤った値で更新されます。"
        isFocusable = false
        icon = FlatSVGIcon("icons/redo_dark.svg")
        addActionListener {
            lastOutputs?.let { excel.setSelectedArea(ExcelFile.toList(it)) }
        }
    }

    private val chatArea: JTextArea = JTextArea().apply {
        lineWrap = true
        document.addDocumentListener( object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                enableChatButton = (text.isNotEmpty() && workbooks.isNotEmpty())
            }
            override fun removeUpdate(e: DocumentEvent) {
                enableChatButton = (text.isNotEmpty() && workbooks.isNotEmpty())
            }
            override fun changedUpdate(e: DocumentEvent) {
                enableChatButton = (text.isNotEmpty() && workbooks.isNotEmpty())
            }
        })
    }
    var enableChatButton: Boolean = false
        set( value ){
            field = value
            if( chatButton.isEnabled != value ){
                chatButton.isEnabled = value
            }
        }

    private val chatButton: JButton = JButton("chat").apply {
        toolTipText = "選択されたシートの選択範囲の内容と、入力されたテキストをOpenAIのChat APIに送信し、結果を反映します。"
        isEnabled = enableChatButton
        addActionListener { evt ->
            try{
                // Check for exist opened Excel file
                val selectedWorkbookName = selectWorkbooks.selectedItem as? String
                val selectedSheetName = selectSheets.selectedItem as? String

                if( selectedWorkbookName.isNullOrEmpty() ){
                    throw IllegalArgumentException("Not exist excel file")
                }

                window.cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)

                try{
                    val data = ExcelFile.toExcelTable(excel.getSelectedArea(selectedWorkbookName, selectedSheetName))
                    val text = chatArea.text
                    val response = chat.send(ChatMessage(text, ChatData(data.data)))

                    lastInputs = data

                    response.value["output"]?.value?.let { output ->
                        val diff = ExcelFile.diff(data.data, output)
                        println(diff)

                        val result = ExcelFile.toList(ExcelTable(data.headers, output))
                        println(result)

                        excel.setSelectedArea(result)

                        lastOutputs = ExcelTable(data.headers, output)
                    }

                }catch(e: TokenLimitExceededException ){
                    logAndDialog.error(
                        "トークン制限エラー\n\n${e.message}"
                    )

                }catch(e: Exception ){
                    logAndDialog.error("Caught Exception (${e.message})")

                }finally{
                    window.cursor = Cursor.getDefaultCursor()
                }

            }catch( e: IllegalArgumentException ){
                logAndDialog.error("${e.message}")

            }finally{
                window.cursor = Cursor.getDefaultCursor()
            }
        }
    }

    // 現在の workbook と sheet 一覧を取得する
    //   - workbook と sheet の選択は、最前面の workbook と sheet を選択状態にする
    //   - workbook がない時は chat ボタンを disable にする
    fun reloadWorkbooksAndSheets() {
        val selected = excel.getActiveWorkbookAndSheet()
        val selectedWorkbook: String? = selected.first
        val selectedSheet: String? = selected.second

        if( selectedWorkbook == null || selectedSheet == null ){
            workbooks = emptyList()
            sheets = emptyList()

            enableChatButton = false

        }else{
            // Update workbooks and sheets combobox
            workbooks = excel.listWorkbookAndActiveSheets().map { it.first }
            sheets = excel.listSheetsOfSelectedWorkbook(selectedWorkbook)

            selectWorkbooks.selectedItem = selectedWorkbook
            selectSheets.selectedItem = selected.second

            // Enable/Disable Chat button
            enableChatButton = (chatArea.text.isNotEmpty() && workbooks.isNotEmpty())
        }
    }

    // component configuration
    init {
        layout = BorderLayout(5, 5)

        // tools header
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            // layout = FlowLayout(FlowLayout.LEFT)
            add(alwaysOnTopButton)
            add(Box.createHorizontalStrut(3))
            add(refreshButton)
            add(Box.createHorizontalStrut(3))
            add(importButton)
            add(Box.createHorizontalStrut(3))
            add(selectWorkbooks)
            add(Box.createHorizontalStrut(3))
            add(selectSheets)
            add(Box.createHorizontalStrut(3))
            add(locked)
            add(Box.createHorizontalGlue())
            add(undoButton)
            add(Box.createHorizontalStrut(3))
            add(redoButton)
        }.also {
            add(it, BorderLayout.NORTH)
        }

        // contents area
        JPanel().apply {
            layout = BorderLayout(5, 5)
            add(JScrollPane(chatArea).apply {
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }).also {
                add(it, BorderLayout.CENTER)
            }
            add(chatButton, BorderLayout.EAST)
        }.also {
            add(it, BorderLayout.CENTER)
        }
    }

    fun loadConfig( config: SimpleChatConfig ){
        alwaysOnTopButton.isSelected = config.alwaysOnTop
    }
    fun storeConfig( config: SimpleChatConfig ){
        config.alwaysOnTop = alwaysOnTopButton.isSelected
    }
}

class SimpleChat (
    private val config: EditorConfig,
    private val excel: ExcelApplication,
    private val chat: ChatService,
): JFrame() {

    private val chatConfig = loadConfig<SimpleChatConfig>("simple-chat")
    private val chatPane: ChatForExcelMainPanel = ChatForExcelMainPanel(excel, chat, this)

    init {
        chatPane.loadConfig(chatConfig)
        // chatPane.workbooks = excel.listWorkbookAndActiveSheets().map { it.first }
        chatPane.reloadWorkbooksAndSheets()

        contentPane.add(chatPane, BorderLayout.CENTER)
    }

    // window configuration
    init {
        title = "${config.title} - ${config.version} - SimpleChat"
        if( chatConfig.windowRectangle != null ){
            bounds = chatConfig.windowRectangle!!
        } else {
            size = Dimension(300, 200)
        }
        defaultCloseOperation = EXIT_ON_CLOSE
        isVisible = true
    }


    @PreDestroy
    fun saveConfig(){
        chatConfig.windowRectangle = bounds
        chatPane.storeConfig(chatConfig)
        storeConfig("simple-chat", chatConfig)
    }

}

