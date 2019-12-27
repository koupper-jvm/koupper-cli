package io.kup.installer

class HelpCommand : Command() {
    override fun name(): String {
        return "help"
    }

    override fun execute(vararg args: String) {
        val command = CommandManager().getCommandFrom(args[0])
        command.showDescription()
        command.showUsage()
        command.showArguments()
    }
}
