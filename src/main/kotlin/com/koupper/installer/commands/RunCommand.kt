package com.koupper.installer.commands

import com.koupper.installer.ANSIColors
import com.koupper.installer.ANSIColors.ANSI_BLACK
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.ANSIColors.YELLOW_BACKGROUND_222
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

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

        val initFile = directories[false]?.filter { it.equals("init.kts") }

        if (initFile?.isEmpty()!!) println("\n${YELLOW_BACKGROUND_222}${ANSI_BLACK} There is no 'init.kts' file. ${ANSI_RESET}\n")
        else {
            val process = Runtime.getRuntime()
                    .exec("octopus init.kts")

            process.waitFor()
        }
    }

    override fun name(): String {
        return AvailableCommands.RUN
    }
}
