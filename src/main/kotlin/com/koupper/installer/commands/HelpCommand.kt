package com.koupper.installer.commands

import com.koupper.installer.ANSIColors
import com.koupper.installer.ANSIColors.ANSI_GREEN_155
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.CommandManager
import com.koupper.installer.commands.AvailableCommands.HELP
import com.koupper.installer.commands.AvailableCommands.commands

class HelpCommand : Command() {
    override fun name(): String {
        return HELP
    }

    init {
        super.name = HELP
        super.usage = "koupper ${ANSI_GREEN_155}$name$ANSI_RESET [${ANSI_GREEN_155}command$ANSI_RESET]"
        super.description = "Show the help for a command"
        super.arguments = commands()
    }

    override fun execute(vararg args: String) {
        if (args.isEmpty()) {
            super.showDescription()

            super.showUsage()

            this.showArguments()

            return
        }

        val command = CommandManager().getCommandObjectFrom(args[0])

        if (command is UndefinedCommand) {
            command.execute(args[0])

            return
        }

        command.showDescription()
        command.showUsage()

        if (command.arguments.isNotEmpty()) command.showArguments()
    }

    override fun showArguments() {
        println(" ${ANSIColors.ANSI_YELLOW_229}• Arguments:$ANSI_RESET")

        this.arguments.forEach { (commandName, _) ->
            println("   $ANSI_GREEN_155$commandName$ANSI_RESET")
        }

        println()
    }
}
