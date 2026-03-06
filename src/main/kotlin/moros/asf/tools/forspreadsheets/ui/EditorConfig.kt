package moros.asf.tools.forspreadsheets.ui

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class EditorConfig (
    @Value("\${asf.title}")
    val title: String,

    @Value("\${asf.version}")
    val version: String,

    @Value("\${asf.auto-completion.enabled}")
    val autoCompletionEnabled: Boolean,

    @Value("\${asf.auto-completion.columns}")
    private val _autoCompletionColumns: List<String>,

    @Value("\${asf.auto-completion.gpt-ignore-columns}")
    val autoCompletionGptIgnoreColumns: List<String>,

    @Value("\${asf.auto-update.enabled}")
    val autoUpdateEnabled: Boolean,

    @Value("\${asf.auto-update.columns}")
    private val _autoUpdateColumns: List<String>,

    @Value("\${asf.auto-update.gpt-ignore-columns}")
    val autoUpdateGptIgnoreColumns: List<String>
){
    val autoCompletionColumns: List<Pair<String,String>> =
        _autoCompletionColumns.map { it.split(":").map(String::trim) }.map { it[0] to it[1] }
    val autoUpdateColumns: List<Pair<String,String>> =
        _autoUpdateColumns.map { it.split(":").map(String::trim) }.map { it[0] to it[1] }
}

enum class CompletionValueType( val label: String, val proc: ()->String ){
    CURRENT_DATETIME("current-datetime", { LocalDateTime.now().toString() }),
    UUID("uuid", { java.util.UUID.randomUUID().toString() }),
    ;

    fun derive( type: String ): String {
        return entries.find { it.label == type }?.let { return it.proc() }
            ?: throw IllegalArgumentException("Unknown type: $type")
    }
}
