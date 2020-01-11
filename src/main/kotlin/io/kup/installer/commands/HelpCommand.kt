package io.kup.installer.commands

import io.kup.installer.ANSIColors.ANSI_GREEN_155
import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.Command
import io.kup.installer.CommandManager

class HelpCommand : Command() {
    override fun name(): String {
        return "help"
    }

    init {
        super.name = "help"
        super.usage = "kup ${ANSI_GREEN_155}$name$ANSI_RESET [${ANSI_GREEN_155}command$ANSI_RESET]"
        super.description = "Show the help for a command"
        super.arguments = arrayListOf("new", "help")
    }

    override fun execute(vararg args: String) {
        if (args.isEmpty() || args[0].isEmpty()) {
            super.showDescription()

            super.showUsage()

            this.showArguments()

            return
        }

        val command = CommandManager().getCommandObjectFrom(args[0])
        command.showDescription()
        command.showUsage()

        if (command.arguments.isNotEmpty()) command.showArguments()
    }
}
