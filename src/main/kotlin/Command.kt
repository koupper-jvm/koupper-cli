abstract class Command {
    lateinit var name: String
    lateinit var usage: String
    lateinit var description: String
    lateinit var arguments: List<String>

    abstract fun execute()

    fun showUsage() {
        println(" ${ANSIColors.ANSI_YELLOW_229}• Usage:${ANSIColors.ANSI_RESET}")

        println("   ${ANSIColors.ANSI_GREEN_155}${name.toLowerCase()}${ANSIColors.ANSI_RESET} $usage")
    }

    open fun showDescription() {
        println(" ${ANSIColors.ANSI_GREEN_155}>>${ANSIColors.ANSI_RESET}${ANSIColors.ANSI_YELLOW_229} $description \n")
    }

    open fun showArguments() {
        println(" ${ANSIColors.ANSI_YELLOW_229}• Arguments:${ANSIColors.ANSI_RESET}")

        for (argument in this.arguments) {
            println("   ${ANSIColors.ANSI_GREEN_155}$argument${ANSIColors.ANSI_RESET}")
        }
    }
}
