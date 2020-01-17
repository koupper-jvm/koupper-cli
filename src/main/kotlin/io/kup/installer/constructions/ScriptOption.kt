package io.kup.installer.constructions

import io.kup.installer.ANSIColors.ANSI_BLACK
import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.ANSI_WHITE
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.ANSIColors.YELLOW_BACKGROUND_222
import io.kup.installer.Wizard
import java.io.File
import java.io.InputStream

class ScriptOption : Wizard {
    override fun init() {
        var fileName: String?

        do {
            print("$ANSI_YELLOW_229\nName of file: $ANSI_RESET")

            fileName = readLine()

            if (fileName!!.isEmpty()) {
                print("\n$YELLOW_BACKGROUND_222$ANSI_BLACK The name of file can not be empty. $ANSI_RESET\n")
            }
        } while (fileName!!.isEmpty())

        val finalFileName = this.sanitizeFileName(fileName)

        val currentDirectory = System.getProperty("user.dir")

        println("\n${ANSI_WHITE}file created with path: $currentDirectory/$finalFileName$ANSI_RESET\n")

        val scriptFile = this::class.java.classLoader.getResourceAsStream("Script.kt")

        scriptFile.toFile("$currentDirectory/$finalFileName")
    }

    private fun sanitizeFileName(fileName: String): String {
        val hasExtension = fileName.contains(".kt")

        if (hasExtension) {
            return fileName
        }

        return fileName.plus(".kt")
    }

    private fun InputStream.toFile(path: String) {
        File(path).outputStream().use { this.copyTo(it) }
    }
}
