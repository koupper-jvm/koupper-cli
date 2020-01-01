package io.kup.installer

class CommandManager {
    fun initWith(arg: String) {

        if (this.isFlagVersion(arg)) {
            DefaultCommand().showDescription()

            return
        }

        val command = getCommandFrom(arg)

        if (command is UndefinedCommand) {
            command.execute(arg.split("\\s".toRegex())[0])

            return
        }

        val args = this.getCommandArgsFrom(arg)

        command.execute(args)
    }

    fun getCommandFrom(input: String?): Command {
        return when (input!!.split("\\s".toRegex())[0]) {
            "help" -> HelpCommand()
            "new" -> NewCommand()
            "" -> DefaultCommand()
            else -> UndefinedCommand()
        }
    }

    private fun isFlagVersion(input: String?): Boolean {
        return input == "-v" || input == "--v" || input == "--version"
    }

    private fun getCommandArgsFrom(input: String?): String {
        val command = this.getCommandFrom(input)

        return input!!.substring(command.name().length).trim()
    }
}

fun main(args: Array<String>) {
    CommandManager().initWith(args[0])
}
