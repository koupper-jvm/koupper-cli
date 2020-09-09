package com.koupper.installer.commands

import com.koupper.installer.ANSIColors
import com.koupper.installer.ANSIColors.ANSI_RED
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.ANSIColors.ANSI_YELLOW_229
import java.io.IOException
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
            println("\n${ANSI_YELLOW_229} There is no 'init.kts' file, create one using [koupper new file:init] command.\n")
        else {
            val finalInitPath = Paths.get("").toAbsolutePath().toString() + "/init.kts"

            this.execute(finalInitPath)
        }
    }

    private fun execute(fileName: String, params: String = "EMPTY_PARAMS") {
        var finalInitPath = ""

        finalInitPath += if (isSingleFileName(fileName)) {
            Paths.get("").toAbsolutePath().toString() + "/$fileName "
        } else {
            fileName
        }.trim()

        try {
            val userPath = System.getProperty("user.home")

            val process = Runtime.getRuntime()
                    .exec("$userPath/.koupper/helpers/octopusBootstrapper.sh $finalInitPath $params")

            process.waitFor()

            val errors = process.errorStream.bufferedReader().readText()

            if (errors.isNotEmpty()) {
                print("$ANSI_RED$errors$ANSI_RESET")

                exitProcess(7)
            }

            val output = process.inputStream.bufferedReader().readText()

            print(output)
        } catch (exception: IOException) {
            println()
            println(ANSI_RED + exception.printStackTrace())
            println()
        }
    }

    override fun name(): String {
        return AvailableCommands.RUN
    }
}
