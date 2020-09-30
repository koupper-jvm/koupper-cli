package com.koupper.installer.commands

import com.koupper.installer.ANSIColors
import com.koupper.installer.ANSIColors.ANSI_WHITE
import com.koupper.installer.commands.AvailableCommands.BUILD
import com.koupper.installer.commands.AvailableCommands.HELP
import com.koupper.installer.commands.AvailableCommands.commands
import java.io.File

class BuildCommand : Command() {
    override fun name(): String {
        return HELP
    }

    init {
        super.name = BUILD
        super.usage = "koupper ${ANSIColors.ANSI_GREEN_155}$name${ANSIColors.ANSI_RESET} [${ANSIColors.ANSI_GREEN_155}command${ANSIColors.ANSI_RESET}]"
        super.description = commands()[super.name].toString()
        super.arguments = emptyMap()
    }

    override fun execute(vararg args: String) {
        if (args.isEmpty()) {
            val currentDirectory = System.getProperty("user.dir")

            if (!File("$currentDirectory/init.kts").exists()) {
                println("\n ${ANSIColors.ANSI_YELLOW_229}No init file exist. Create one typing -> ${ANSI_WHITE}koupper new file:init${ANSIColors.ANSI_RESET}\n")

                return
            }

            RunCommand().execute("$currentDirectory/init.kts")

            return
        }


    }

    override fun showArguments() {
        println(" ${ANSIColors.ANSI_YELLOW_229}• Arguments:${ANSIColors.ANSI_RESET}")

        this.arguments.forEach { (commandName, _) ->
            println("   ${ANSIColors.ANSI_GREEN_155}$commandName${ANSIColors.ANSI_RESET}")
        }

        println()
    }
}