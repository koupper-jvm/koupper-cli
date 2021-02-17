package com.koupper.cli.commands

import com.koupper.cli.ANSIColors
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_WHITE
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
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
        super.usage =
                "koupper ${ANSIColors.ANSI_GREEN_155}$name${ANSI_RESET} [${ANSIColors.ANSI_GREEN_155}kotlinScriptName${ANSI_RESET}]"
        super.description = "run a kotlin script"
        super.arguments = emptyMap()
    }

    override fun execute(vararg args: String) {
        if (args.isNotEmpty() && args[0].isNotEmpty()) {
            if (".kts" !in args[0]) {
                println("\n${ANSI_RED} The file should be an [kts] extension.\n")

                exitProcess(7)
            }

            if (args.size > 1) {
                val params = args[1]

                this.execute(args[0], params)

                return
            }

            this.execute(args[0])

            return
        }

        val directories = Files.list(Paths.get(".")).collect(Collectors.partitioningBy { Files.isDirectory(it) })

        val initFile = directories[false]?.filter {
            it.toString() == "./init.kts"
        }

        if (initFile?.isEmpty()!!)
            println("\n ${ANSI_WHITE}'init.kts' not exist. Create one typing: ${ANSI_YELLOW_229}koupper new file:init${ANSI_WHITE} or specify a script name.\n")
        else {
            val finalInitPath = Paths.get("").toAbsolutePath().toString() + "/init.kts"

            this.execute(finalInitPath)
        }
    }

    private fun execute(fileName: String, params: String = "EMPTY_PARAMS") {
        var finalFilePath = ""

        finalFilePath += if (isSingleFileName(fileName)) {
            Paths.get("").toAbsolutePath().toString() + "/$fileName "
        } else {
            fileName
        }.trim()

        val userPath = System.getProperty("user.home")

        val file = File("$userPath/.koupper/helpers/octopus-parameters.txt")
        file.writeText("$finalFilePath $params")
        file.createNewFile()
        file.setExecutable(true)
        file.setReadable(true)
        file.setWritable(true)
    }

    override fun name(): String {
        return AvailableCommands.RUN
    }
}
