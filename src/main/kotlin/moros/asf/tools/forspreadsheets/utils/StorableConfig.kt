package moros.asf.tools.forspreadsheets.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Component
import java.io.File
import kotlin.reflect.KProperty

val json = Json {
    prettyPrint = true
    encodeDefaults = true
}

inline fun <reified T> loadConfig( name: String ): T {
    val file = File("config/$name.json")
    if( file.exists() ){
        val json = file.readText()
        return Json.decodeFromString<T>(json)
    } else {
        return T::class.java.getDeclaredConstructor().newInstance()
    }
}

inline fun <reified T> storeConfig( name: String, config: T ){
    val file = File("config/$name.json")
    if( !file.parentFile.exists() ){
        file.parentFile.mkdirs()
    }
    val json = json.encodeToString(config)
    file.writeText(json)
}

