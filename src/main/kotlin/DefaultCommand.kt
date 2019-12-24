class DefaultCommand : Command() {
    override fun name(): String {
        return "default"
    }

    init {
        super.name = "kup"
        super.usage = "[${ANSIColors.ANSI_GREEN_155}command${ANSIColors.ANSI_RESET}]"
        super.description = "KUp installer ${ANSIColors.ANSI_GREEN_155}1.0.0${ANSIColors.ANSI_RESET}"
        super.arguments = arrayListOf("new", "help")
    }

    override fun execute(vararg args: String) {
        super.showDescription()

        super.showUsage()

        this.showArguments()
    }

    override fun showArguments() {
        println(" ${ANSIColors.ANSI_YELLOW_229}• Available commands:")

        for (commands in super.arguments) {
            println("   ${ANSIColors.ANSI_GREEN_155}$commands${ANSIColors.ANSI_RESET}")
        }
    }
}
