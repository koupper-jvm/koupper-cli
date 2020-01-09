package io.kup.installer.constructions

import io.kup.installer.ANSIColors
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.Wizard
import java.io.File
import java.io.InputStream

class ScriptOption : Wizard {
    override fun init() {
        var fileName: String?

        do {
            print("${ANSI_YELLOW_229}\nName of file: ${ANSIColors.ANSI_RESET}")

            fileName = readLine()

            if (fileName!!.isEmpty()) {
                print("\n${ANSIColors.YELLOW_BACKGROUND_222}${ANSIColors.ANSI_BLACK} The name of file can not be empty. ${ANSIColors.ANSI_RESET}\n")
            }
        } while (fileName!!.isEmpty())

        val finalFileName = this.sanitizeFileName(fileName.capitalize())

        val currentDirectory = System.getProperty("user.dir")

        print("\n${ANSIColors.ANSI_WHITE}file created with path: $currentDirectory/$finalFileName${ANSIColors.ANSI_RESET}\n")

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

    fun InputStream.toFile(path: String) {
        File(path).outputStream().use { this.copyTo(it) }
    }
}
