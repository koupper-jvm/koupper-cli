import ANSIColors.ANSI_RESET
import ANSIColors.ANSI_WHITE
import ANSIColors.RED_BACKGROUND_203

class UndefinedCommand : Command() {
    override fun name(): String {
        return "undefined"
    }

    override fun execute(vararg args: String) {
        print("$RED_BACKGROUND_203$ANSI_WHITE The command '${args[0]}' is undefined. $ANSI_RESET")
    }
}
