package moros.asf.tools.forspreadsheets

import com.formdev.flatlaf.FlatDarkLaf
import moros.asf.tools.forspreadsheets.openai.ChatService
import moros.asf.tools.forspreadsheets.ui.*
import moros.asf.tools.forspreadsheets.utils.ExcelApplication
import org.springframework.ai.chat.model.ChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Profile
import javax.swing.JFrame
import javax.swing.UIManager

@SpringBootApplication
class LLMToolsForSpreadsheetsJavaEdition {
    @Profile("LLMToolbox")
    @Bean
    fun buildSimpleChatWindow (
        config: EditorConfig,
        excel: ExcelApplication,
        chat: ChatService
    ): SimpleChat {
        return SimpleChat(config, excel, chat).apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            isVisible = true
        }
    }
}

fun main(args: Array<String>) {
    System.setProperty("sun.java2d.uiScale", "1.5")
    UIManager.put("TabbedPane.tabHeight", 20)
    FlatDarkLaf.setup()

    runApplication<LLMToolsForSpreadsheetsJavaEdition>(*args)
}
