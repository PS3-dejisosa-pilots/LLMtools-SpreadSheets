package moros.asf.tools.forspreadsheets.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.swing.JOptionPane


// ログ出力 & (場合によって) ダイアログを表示するクラス
class LogAndDialogUtils(
    private val clazz: Class<*>
){
    private val logger: Logger = LoggerFactory.getLogger(clazz)

    // Show Debug mesage
    fun debug( message: String, useDialog: Boolean = false ){
        logger.debug("[${getCallerMethodName()}] $message")
    }

    // Show Info mesage
    fun info( message: String, useDialog: Boolean = false ){
        logger.info("[${getCallerMethodName()}] $message")
    }

    // Show Warning mesage
    fun warn( message: String, useDialog: Boolean = true ){
        logger.warn("[${getCallerMethodName()}] $message")

        // Show warning diaglog
        if( useDialog == true ){
            JOptionPane.showMessageDialog(
                    null,
                    message,
                    "WARN",
                    JOptionPane.WARNING_MESSAGE)
        }
    }

    // Show Error mesage
    fun error( message: String, useDialog: Boolean = true ){
        logger.error("[${getCallerMethodName()}] $message")

        // Show error diaglog
        if( useDialog == true ){
            JOptionPane.showMessageDialog(
                    null,
                    message,
                    "ERROR",
                    JOptionPane.ERROR_MESSAGE)
        }
    }

    // 呼び出し元クラスメソッド名を取得する
    private fun getCallerMethodName(): String{
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace[4].methodName
    }
}
