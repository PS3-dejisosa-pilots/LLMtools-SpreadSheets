package moros.asf.tools.forspreadsheets.ui.common

import com.formdev.flatlaf.extras.FlatSVGIcon
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JFrame
import javax.swing.JToggleButton

class AlwaysOnTopButton (
    val window: JFrame,
    val defaultValue: Boolean = true
): JToggleButton() {
    init {
        toolTipText = "このボタンをオンにすると、本ツールのウィンドウを常に最前面で表示するようにします。"
        isFocusable = false
        isSelected = defaultValue
        window.isAlwaysOnTop = defaultValue
        icon = FlatSVGIcon("icons/restore_dark.svg")
        addChangeListener { evt ->
            window.isAlwaysOnTop = isSelected
        }
    }
}
