package moros.asf.tools.forspreadsheets.ps

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.PrintWriter
import java.nio.charset.Charset
import java.lang.ProcessBuilder


@Component
class PowerShellLauncher {

    private var ready: Boolean = false
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var process: Process? = null

    @PostConstruct
    fun init(){
        // Change powershell execute policy (RemoteSigned)
        val commandChangePolicy = "powershell.exe Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser"
        val processBuilderChangePolicy = ProcessBuilder(commandChangePolicy.split(" "))

        processBuilderChangePolicy.redirectErrorStream(true)
        val processChangePolicy = processBuilderChangePolicy.start()
        val readerChangePolicy = processChangePolicy.inputStream.bufferedReader()
        val resChangePolicy = readerChangePolicy.readText()
        println("Set-ExecutionPolicy (Res): $resChangePolicy")

        // Generate powershell process
        process = Runtime.getRuntime().exec("powershell.exe -NoExit -NoLogo -NonInteractive")
        reader = process!!.inputStream.bufferedReader(Charset.defaultCharset())
        writer = PrintWriter(process!!.outputStream, true, Charset.forName("MS932"))

        ready = true
    }

    @PreDestroy
    fun close(){
        ready = false

        writer?.println("exit")
        reader?.close()
        reader = null
        writer?.close()
        writer = null
        process?.destroy()
        process = null
    }

    fun reset(){
        close()
        init()
    }

    fun eval( command: String, inputs: List<String> = listOf() ): List<String> {
        if( !ready ) throw IllegalStateException("PowerShellLauncher is not ready")

        writer!!.println(command)
        val tmp = reader!!.readLine() // skip command
        println(tmp)

        if( inputs.isNotEmpty() ){
            writer!!.println("${inputs.size}")
            inputs.forEach { writer!!.println(it) }
        }

        var line = ""
        do {
            line = reader!!.readLine()
            println(line)
        } while( line.isEmpty() )
        val count = line.toInt()

        val result = mutableListOf<String>()
        for( i in 0 until count ){
            result.add(reader!!.readLine() ?: "")
        }
        return result
    }

    fun <T> eval( command: String, proc: (List<String>)->T ): T {
        return eval(command).let(proc)
    }

}