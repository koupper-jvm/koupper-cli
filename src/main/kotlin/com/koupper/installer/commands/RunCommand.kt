package com.koupper.installer.commands

import com.koupper.installer.ANSIColors
import com.koupper.installer.ANSIColors.ANSI_BLACK
import com.koupper.installer.ANSIColors.ANSI_RED
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.ANSIColors.YELLOW_BACKGROUND_222
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.system.exitProcess

class RunCommand : Command() {
    init {
        super.name = "run"
        super.usage =
                "koupper ${ANSIColors.ANSI_GREEN_155}$name${ANSI_RESET} [${ANSIColors.ANSI_GREEN_155}kotlinScriptName${ANSI_RESET}]"
        super.description = "run a kotlin script"
        super.arguments = emptyMap()
    }

    override fun execute(vararg args: String) {
        val directories = Files.list(Paths.get(".")).collect(Collectors.partitioningBy { Files.isDirectory(it) })

        val initFile = directories[false]?.filter { it.toString() == "./init.kts" }

        if (initFile?.isEmpty()!!)
            println("\n${YELLOW_BACKGROUND_222}${ANSI_BLACK} There is no 'init.kts' file, create one using [create] command.\n")
        else {
            val finalInitPath = Paths.get("").toAbsolutePath().toString() + "/init.kts"

            try {
                val userPath = System.getProperty("user.home")

                val process = Runtime.getRuntime()
                        .exec("$userPath/.koupper/helpers/octopusBootstrapper.sh $finalInitPath")

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
    }

    override fun name(): String {
        return AvailableCommands.RUN
    }
}
