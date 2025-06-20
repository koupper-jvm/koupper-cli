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
        super.usage = "\n   koupper ${ANSI_GREEN_155}$name$ANSI_RESET ${ANSI_GREEN_155}module name=\"auth-server\",version=\"1.0.0\",package=\"tdn.auth\"${ANSI_RESET}\n\n   koupper ${ANSI_GREEN_155}$name$ANSI_RESET ${ANSI_GREEN_155}script-name.kts${ANSI_RESET}\n"
        super.description = "\n   Creates a module or script\n"
        super.arguments = emptyMap()
        super.additionalInformation = """
   visit for more info: https://koupper.com/cli/commands/new
        """
    }

    override fun name(): String {
        return NEW
    }

    override fun execute(vararg args: String): String {
        var result = when {
            args.size < 2 -> {
                this.showNewInfo()
            }

            args[1].trim() == "module" -> {
                ""
            }

            "file:init" in args[1] -> {
                val currentDirectory = args[0]
                val finalScript = currentDirectory + File.separator + "init.kts"

                if (File(finalScript).exists()) {
                    return "\n${ANSI_YELLOW_229} The script ${File(finalScript).name} already exist.${ANSI_RESET}\n"
                }

                this::class.java.classLoader.getResourceAsStream("init.txt")?.toFile(finalScript)
                "init.kts file created."
            }

            ".kts" in args[1].trim() || ".kt" in args[1].trim() -> {
                val currentDirectory = args[0]
                val finalScript = currentDirectory + File.separator + args[1]

                if (File(finalScript).exists()) {
                    return "\n${ANSI_YELLOW_229} The script ${File(finalScript).name} already exist.${ANSI_RESET}\n"
                }

                this::class.java.classLoader.getResourceAsStream("script.txt")?.toFile(finalScript)
                "${args[1]} file created."
            }

            else -> {
                "\n${ANSI_YELLOW_229} The file must end with [.kts] extension or use: ${ANSIColors.ANSI_WHITE}koupper new module [${ANSI_GREEN_155}nameOfModule${ANSIColors.ANSI_WHITE}]$ANSI_YELLOW_229.$ANSI_RESET\n"
            }
        }

        val env = File(".env")

        if (!env.exists()) {
            env.createNewFile()

            result += "\nrm ${ANSI_YELLOW_229}An file .env was created to keep the scripts configurations$ANSI_RESET\n"
        }

        return result
    }

    private fun InputStream.toFile(path: String) {
        File(path).outputStream().use { this.copyTo(it) }
    }

    private fun showNewInfo(): String {
        val additionalInfo = this.showAdditionalInformation()

        val newInfo = """
            
               You can create:
            $ANSI_YELLOW_229
               1.- Module: A gradle project containing scripts, resources, and configurations to manage your development.  
               2.- Script: A simple script to do something.
            $ANSI_RESET
               Use the command$ANSI_YELLOW_229 koupper help new$ANSI_RESET for more information.
               
        """.trimIndent()

        return "$newInfo$additionalInfo$ANSI_RESET"
    }
}
