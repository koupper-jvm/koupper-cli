package com.koupper.cli.commands

import com.koupper.cli.ANSIColors
import com.koupper.cli.ANSIColors.ANSI_BLACK
import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import com.koupper.cli.ANSIColors.YELLOW_BACKGROUND_222
import com.koupper.cli.commands.AvailableCommands.NEW
import com.koupper.cli.constructions.ModuleOption
import com.koupper.cli.constructions.ScriptOption
import com.koupper.cli.languages.KotlinOption
import java.io.File
import java.io.InputStream

class NewCommand : Command() {
    init {
        super.name = NEW
        super.usage = "koupper ${ANSI_GREEN_155}$name$ANSI_RESET ${ANSI_GREEN_155}module${ANSI_RESET}"
        super.description = "Creates a module"
        super.arguments = emptyMap()
        super.additionalInformation = """
   visit for more info: https://koupper.com/cli/commands/new
        """
    }

    override fun name(): String {
        return NEW
    }

    override fun execute(vararg args: String) {
        if (args.isNotEmpty()) {
            val currentDirectory = System.getProperty("user.dir")
            var moduleName = ""
            var moduleType = ""

            if (args[0].trim() == "module") {
                if (args.size > 1 && args[1].isNotEmpty()) {
                    moduleName = args[1].trim()
                }

                if (args.size > 2 && args[2].isNotEmpty()) {
                    moduleType = args[2].trim()
                }

                ModuleOption().init(
                        mapOf(
                                "moduleName" to moduleName,
                                "moduleType" to moduleType
                        )
                )

                return
            }

            if ("file:init" in args[0]) {
                this::class.java.classLoader.getResourceAsStream("init.txt").toFile("$currentDirectory/init.kts")

                return
            }

            if (".kts" in args[0].trim()) {
                this::class.java.classLoader.getResourceAsStream("script.txt").toFile("$currentDirectory/" + args[0])
            } else {
                println("\n${ANSI_YELLOW_229} The file must end with [kts] extension or use ${ANSIColors.ANSI_WHITE}koupper new module [${ANSI_GREEN_155}nameOfModule${ANSIColors.ANSI_WHITE}]$ANSI_YELLOW_229.$ANSI_RESET\n")

                return
            }
        } else {
            this.askForCreation()

            return
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

    private fun askForCreation() {
        this.showAdditionalInformation()
        print(
                """

            Choose one
            $ANSI_YELLOW_229
            1.- Module
            2.- Script (default)$ANSI_RESET
            
            option: 
        """.trimIndent()
        )

        val option = readLine()

        when {
            option!!.isEmpty() -> ScriptOption().init()
            option == "1" -> ModuleOption().init()
            option == "2" -> ScriptOption().init()
            else -> {
                println("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Option $option is not valid. Using default [module] option. $ANSI_RESET\n")

                KotlinOption().init()
            }
        }
    }
}
