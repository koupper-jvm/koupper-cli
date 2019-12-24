fun main() {
    CommandManager().init()
}

class CommandManager {
    fun init() {
        val input = readLine()

        if (this.isFlagVersion(input)) {
            DefaultCommand().showDescription()

            return
        }

        val command = getCommandFrom(input)

        if (command is UndefinedCommand) {
            command.execute()

            return
        }

        val args = this.getCommandArgsFrom(input)

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

        return input!!.substring(command!!.name().length).trim()
    }
}
