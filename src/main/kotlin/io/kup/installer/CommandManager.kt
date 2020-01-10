package io.kup.installer

import io.kup.installer.commands.DefaultCommand
import io.kup.installer.commands.HelpCommand
import io.kup.installer.commands.NewCommand
import io.kup.installer.commands.UndefinedCommand

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

        command.execute(args)
    }

    fun getCommandObjectFrom(input: String): Command {
        return when (input) {
            "help" -> HelpCommand()
            "new" -> NewCommand()
            else -> UndefinedCommand()
        }
    }

    private fun isFlagVersion(input: String): Boolean {
        return input == "-v" || input == "--v" || input == "--version"
    }

    private fun getCommandArgsFrom(arg: Array<String>): String {
        return if (arg.size > 1) arg[1] else ""
    }
}

fun main(args: Array<String>) {
    CommandManager().initWith(args)
}
