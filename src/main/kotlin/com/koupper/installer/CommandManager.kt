package com.koupper.installer

import com.koupper.installer.commands.*
import com.koupper.installer.commands.AvailableCommands.HELP
import com.koupper.installer.commands.AvailableCommands.NEW
import com.koupper.installer.commands.AvailableCommands.RUN

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

        val command = getCommandByName(arg[0])

        if (command is UndefinedCommand) {
            command.execute(arg[0])

            return
        }

        val args = this.getArgsFrom(arg)

        command.execute(*args)
    }

    fun getCommandByName(input: String): Command {
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

    private fun getArgsFrom(arg: Array<String>): Array<String> {
        return if (arg.size > 1) arg.sliceArray(1 until arg.size) else emptyArray()
    }
}

fun main(args: Array<String>) {
    CommandManager().initWith(args)
}
