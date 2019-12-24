import ANSIColors.ANSI_GREEN_155
import ANSIColors.ANSI_RESET
import ANSIColors.ANSI_YELLOW_229

abstract class Command {
    lateinit var name: String
    lateinit var usage: String
    lateinit var description: String
    lateinit var arguments: List<String>

    abstract fun execute(vararg args: String)

    abstract fun name(): String

    fun showUsage() {
        println(" ${ANSI_YELLOW_229}• Usage:$ANSI_RESET")

        println("   $ANSI_GREEN_155${name.toLowerCase()}$ANSI_RESET $usage")
    }

    open fun showDescription() {
        println(" $ANSI_GREEN_155>>$ANSI_RESET$ANSI_YELLOW_229 $description \n")
    }

    open fun showArguments() {
        println(" ${ANSI_YELLOW_229}• Arguments:$ANSI_RESET")

        for (argument in this.arguments) {
            println("   $ANSI_GREEN_155$argument$ANSI_RESET")
        }
    }
}
