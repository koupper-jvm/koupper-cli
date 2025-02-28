package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_WHITE
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.system.exitProcess

val isSingleFileName: (String) -> Boolean = {
    it.contains("^[a-zA-Z0-9]+.kts$".toRegex())
}

class RunCommand : Command() {
    init {
        super.name = "run"
        super.usage = "koupper ${ANSI_GREEN_155}$name${ANSI_RESET} [${ANSI_GREEN_155}kotlinScriptName${ANSI_RESET}]"
        super.description = "Run a kotlin script"
        super.arguments = emptyMap()
        super.additionalInformation = """
   visit for more info: https://koupper.com/cli/commands/run
        """
    }

    override fun execute(vararg args: String): String {
        if (args.isNotEmpty() && args[0].isNotEmpty()) {
            if (".kts" !in args[0]) {
                println("\n${ANSI_YELLOW_229} The file must end with [.kts] extension or use ${ANSI_WHITE}koupper new module [${ANSI_GREEN_155}nameOfModule${ANSI_WHITE}]$ANSI_YELLOW_229.$ANSI_RESET\n")
                exitProcess(7)
            }

            return if (args.size > 1) {
                execute(args[0], args[1])
            } else {
                execute(args[0])
            }
        }

        val directories = Files.list(Paths.get(".")).collect(Collectors.partitioningBy { Files.isDirectory(it) })
        val initFile = directories[false]?.filter { it.toString() == "./init.kts" }

        return if (initFile?.isEmpty()!!) {
            println("\n ${ANSI_WHITE}'init.kts' not found. Create one using: ${ANSI_YELLOW_229}koupper new file:init${ANSI_WHITE} or start creating a script.\n")
            ""
        } else {
            val finalInitPath = Paths.get("").toAbsolutePath().toString() + "/init.kts"
            execute(finalInitPath)
        }
    }

    private fun execute(filePath: String, params: String = "EMPTY_PARAMS"): String {
        val finalFilePath = if (isSingleFileName(filePath)) {
            Paths.get("").toAbsolutePath().toString() + "/$filePath "
        } else {
            filePath
        }.trim()

        return sendToOctopus("$finalFilePath $params")
    }

    private fun sendToOctopus(command: String): String {
        return try {
            val socket = Socket("localhost", 9998)
            val writer = socket.getOutputStream().bufferedWriter()
            val reader = socket.getInputStream().bufferedReader()

            println("🚀 Enviando comando a octopus: $command")
            writer.write(command)
            writer.newLine()
            writer.flush()

            val response = reader.readLine()
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
}
