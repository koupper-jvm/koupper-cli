package io.kup.installer

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

        val command = getCommandFrom(arg[0])

        if (command is UndefinedCommand) {
            command.execute(arg[0].split("\\s".toRegex())[0])

            return
        }

        val args = this.getCommandArgsFrom(arg[0])

        command.execute(args)
    }

    fun getCommandFrom(input: String?): Command {
        return when (input!!.split("\\s".toRegex())[0]) {
            "help" -> HelpCommand()
            "new" -> NewCommand()
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
    CommandManager().initWith(args)
}
