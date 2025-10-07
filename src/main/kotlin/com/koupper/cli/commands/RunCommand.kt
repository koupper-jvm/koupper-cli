package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_WHITE
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
import java.net.Socket

val isSingleFileName: (String) -> Boolean = {
    it.contains("^[a-zA-Z0-9]+.kts$".toRegex())
}

class RunCommand : Command() {
    init {
        super.name = "run"
        super.usage = "\n   koupper ${ANSI_GREEN_155}$name${ANSI_RESET} ${ANSI_GREEN_155}script-name.kts${ANSI_RESET}\n"
        super.description = "\n   Run a kotlin script\n"
        super.arguments = emptyMap()
        super.additionalInformation = """
   For more info: https://koupper.com/cli/commands/run
        """
    }

    override fun execute(vararg args: String): String {
        val context = args[0]

        if (args.size > 1 && args[1].isNotEmpty()) {
            if (".kts" !in args[1] && ".kt" !in args[1]) {
                return "\n${ANSI_YELLOW_229} The file must end with [.kts || .kt] extension.${ANSI_RESET}\n"
            }

            val executionArgs = args.sliceArray(2 until args.size)

            return if (executionArgs.isNotEmpty()) {
                execute(context = context, args[1], args.drop(2).joinToString(" "))
            } else {
                execute(context = context, args[1])
            }
        }

        val initFile = args[0] + File.separator + "init.kts"

        return if (!File(initFile).exists()) {
            return "\n ${ANSI_WHITE}'init.kts' file not found. Create one using: ${ANSI_YELLOW_229}koupper new file:init${ANSI_WHITE} or start writing a script.\n"
        } else {
            execute(context, "init.kts")
        }
    }

    private fun execute(context: String, filePath: String, params: String = "EMPTY_PARAMS"): String {
        if (!File(context + File.separator + filePath).exists()) {
            return "\n${ANSI_YELLOW_229} The script ${File(filePath).name} does not exist.${ANSI_RESET}\n"
        }

        return sendToOctopus(context, filePath, params)
    }

    private fun sendToOctopus(context: String, script: String, params: String): String {
        return try {
            val socket = Socket("localhost", 9998)
            val writer = socket.getOutputStream().bufferedWriter()
            val reader = socket.getInputStream().bufferedReader()


            println("🚀 Enviando comando a octopus: $context $script $params")
            writer.write("$context $script $params")
            writer.newLine()
            writer.flush()

            val response = reader.readText()
            println("📤 Respuesta de octopus: $response")

            socket.close()
            response
        } catch (e: Exception) {
            println("⚠️ Error al enviar comando a octopus: ${e.message}")
            "Error: ${e.message}"
        }
    }

    override fun name(): String {
        return AvailableCommands.RUN
    }

    override fun showArguments(): String {
        return ""
    }
}
