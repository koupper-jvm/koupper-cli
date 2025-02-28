package com.koupper.cli.commands

import com.koupper.cli.ANSIColors
import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.CommandManager
import com.koupper.cli.commands.AvailableCommands.HELP
import com.koupper.cli.commands.AvailableCommands.commands

class HelpCommand : Command() {
    override fun name(): String {
        return HELP
    }

    init {
        super.name = HELP
        super.usage = "koupper ${ANSI_GREEN_155}$name$ANSI_RESET [${ANSI_GREEN_155}command$ANSI_RESET]"
        super.description = "Shows help for commands"
        super.arguments = commands()
    }

    override fun execute(vararg args: String): String {
        return when {
            args.isEmpty() -> {
                super.showDescription()
                super.showUsage()
                this.showArguments()
                ""
            }

            else -> {
                val command = CommandManager().getCommandByName(args[0])
                if (command is UndefinedCommand) {
                    command.execute(args[0])
                    ""
                } else {
                    command.showDescription()
                    command.showUsage()
                    command.showAdditionalInformation()
                    if (command.arguments.isNotEmpty()) command.showArguments()
                    ""
                }
            }
        }
    }

    override fun showArguments() {
        println("\n ${ANSIColors.ANSI_YELLOW_229}* Arguments:$ANSI_RESET")

        this.arguments.forEach { (commandName, _) ->
            println("   $ANSI_GREEN_155$commandName$ANSI_RESET")
        }

        println()
    }
}
