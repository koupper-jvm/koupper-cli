package io.kup.installer

class CommandManager {
    companion object {
        @JvmStatic fun main() {
            CommandManager().init()
        }
    }

    fun init() {
        val input = readLine()

        if (this.isFlagVersion(input)) {
            DefaultCommand().showDescription()

            return
        }

        val command = getCommandFrom(input)

        if (command is UndefinedCommand) {
            command.execute(input!!.split("\\s".toRegex())[0])

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

        return input!!.substring(command.name().length).trim()
    }
}
