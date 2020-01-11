package io.kup.installer

import io.kup.installer.commands.*
import io.kup.installer.commands.AvailableCommands.HELP
import io.kup.installer.commands.AvailableCommands.NEW
import io.kup.installer.commands.AvailableCommands.RUN

class CommandManager {

    fun initWith(arg: Array<String>) {
        if (arg.isEmpty()) {
            DefaultCommand().execute()

            return
        }

        if (this.isFlagVersion(arg[0])) {
            DefaultCommand().showDescription()

            return
        }

        val command = getCommandObjectFrom(arg[0])

        if (command is UndefinedCommand) {
            command.execute(arg[0])

            return
        }

        val args = this.getCommandArgsFrom(arg)

        command.execute(*args)
    }

    fun getCommandObjectFrom(input: String): Command {
        return when (input) {
            HELP -> HelpCommand()
            NEW -> NewCommand()
            RUN -> RunCommand()
            else -> UndefinedCommand()
        }
    }

    private fun isFlagVersion(input: String): Boolean {
        return input == "-v" || input == "--v" || input == "--version"
    }

    private fun getCommandArgsFrom(arg: Array<String>): Array<String> {
        return if (arg.size > 1) arg.sliceArray(1 until arg.size) else emptyArray()
    }
}

fun main(args: Array<String>) {
    CommandManager().initWith(args)
}
