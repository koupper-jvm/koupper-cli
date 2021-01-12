package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_BLACK
import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import com.koupper.cli.ANSIColors.YELLOW_BACKGROUND_222
import com.koupper.cli.commands.AvailableCommands.NEW
import com.koupper.cli.languages.JavaOption
import com.koupper.cli.languages.KotlinOption
import java.io.File
import java.io.InputStream

class NewCommand : Command() {
    init {
        super.name = NEW
        super.usage = "koupper ${ANSI_GREEN_155}$name$ANSI_RESET"
        super.description = "Initializes a wizard to create a new resource"
        super.arguments = emptyMap()
    }

    override fun name(): String {
        return NEW
    }

    override fun execute(vararg args: String) {
        if (args.isNotEmpty()) {
            val currentDirectory = System.getProperty("user.dir")

            if ("file:init" in args[0]) {
                this::class.java.classLoader.getResourceAsStream("init.txt").toFile("$currentDirectory/init.kts")

                return
            }

            if (".kts" in args[0].trim()) {
                this::class.java.classLoader.getResourceAsStream("script.txt").toFile("$currentDirectory/" + args[0])
            } else {
                println("\n ${ANSI_YELLOW_229}The file should contain the 'kts' extension.$ANSI_RESET\n")

                return
            }
        } else {
            this.askForLanguage()
        }

        val env = File(".env")

        if (!env.exists()) {
            println("\n ${ANSI_YELLOW_229}An file .env was created to keep the scripts configurations$ANSI_RESET\n")

            File(".env").createNewFile()
        }
    }

    private fun InputStream.toFile(path: String) {
        File(path).outputStream().use { this.copyTo(it) }
    }

    private fun askForLanguage() {
        print(
                """

            Select a language
            $ANSI_YELLOW_229
            1.- Kotlin (default)
            2.- Java $ANSI_RESET
            
            Choose an option: 
        """.trimIndent()
        )

        val option = readLine()

        when {
            option!!.isEmpty() -> {
                print("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Using default language. $ANSI_RESET\n")

                KotlinOption().init()
            }
            option == "1" -> KotlinOption().init()
            option == "2" -> JavaOption().init()
            else -> {
                println("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Option $option is not valid. Using default language. $ANSI_RESET\n")

                KotlinOption().init()
            }
        }
    }
}
