package com.koupper.cli.commands

import com.koupper.cli.ANSIColors
import com.koupper.cli.ANSIColors.ANSI_BLACK
import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import com.koupper.cli.ANSIColors.YELLOW_BACKGROUND_222
import com.koupper.cli.CommandManager
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
   For more info: https://koupper.com/cli/commands/new
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
                val paramsString = args.drop(2).joinToString(" ")
                val params = paramsString.split(",").mapNotNull { param ->
                    val parts = param.split("=").map { it.trim() }
                    if (parts.size == 2) {
                        val key = parts[0]
                        val value = parts[1].removeSurrounding("\"")
                        key to value
                    } else {
                        null
                    }
                }.toMap()

                val name = params["name"]
                val version = params["version"]
                val packageName = params["package"]
                val type = params["type"] ?: "EXECUTABLE"

                if (name.isNullOrBlank() || version.isNullOrBlank() || packageName.isNullOrBlank()) {
                    return "\n${ANSI_YELLOW_229}Missing required parameters. Required: name, version, package.$ANSI_RESET\n"
                }

                val finalInitFile = args[0] + File.separator + "init.kts"
                this::class.java.classLoader.getResourceAsStream("init.txt")?.toFile(finalInitFile)

                val finalScriptContent = File(finalInitFile).readText(Charsets.UTF_8)
                val replacedScript = finalScriptContent
                    .replace("%MODULE_NAME%", name)
                    .replace("%MODULE_VERSION%", version)
                    .replace("%MODULE_PACKAGE%", packageName)
                    .replace("%MODULE_TYPE%", type)
                    .replace("%HANDLER_NAME%", "executable")
                    .replace("%SCRIPT_NAME%", "script.kts")

                File(finalInitFile).writeText(replacedScript, Charsets.UTF_8)

                this::class.java.classLoader.getResourceAsStream("script.txt")!!.use { input ->
                    File(args[0], "script.kts").outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (type == "HANDLERS_CONTROLLERS_SCRIPTS") {
                    val yamlTemplate = this::class.java.classLoader.getResourceAsStream("templates/http-cfg.yml")!!
                        .bufferedReader().readText()

                    val replacedYaml = yamlTemplate
                        .replace("%DESCRIPTION%", "Sample configuration for a JERSEY-AWS-HANDLER API")
                        .replace("%PORT%", "8081")
                        .replace("%CONTEXT_PATH%", "/api/v1")
                        .replace("%API_NAME%", "My first API")
                        .replace("%API_PATH%", "/hello")
                        .replace("%API_METHOD%", "GET")
                        .replace("%API_HANDLER%", "executable")
                        .replace("%API_DESCRIPTION%", "This is an API example.")

                    File(args[0], "$name.yml").writeText(replacedYaml, Charsets.UTF_8)
                }

                CommandManager.commands["run"]?.execute(args[0], "init.kts") ?: ""

                "Module $name & config file generated successfully with type $type."
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
            result += "\n${ANSI_YELLOW_229}An file .env was created to keep the scripts configurations$ANSI_RESET\n"
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

    override fun showArguments(): String {
        return ""
    }
}
