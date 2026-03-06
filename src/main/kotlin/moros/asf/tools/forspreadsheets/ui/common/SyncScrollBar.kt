package moros.asf.tools.forspreadsheets.ui.common

import javax.swing.JScrollBar
import javax.swing.BoundedRangeModel
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener


// Sync ScrollBar event class
class SyncScrollBar(
    private val otherScrollBar: JScrollBar
): ChangeListener {
    private var adjusting = false

    override fun stateChanged(e: ChangeEvent) {
        if( !adjusting ){
            adjusting = true
            otherScrollBar.value = (e.source as BoundedRangeModel).value
            adjusting = false
        }
    }
}
