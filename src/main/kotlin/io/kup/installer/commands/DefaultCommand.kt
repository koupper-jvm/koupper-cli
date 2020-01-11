package io.kup.installer.commands

import io.kup.installer.ANSIColors.ANSI_GREEN_155
import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.Command

class DefaultCommand : Command() {
    override fun name(): String {
        return "default"
    }

    init {
        super.name = "kup"
        super.usage = "$name [${ANSI_GREEN_155}command$ANSI_RESET]"
        super.description = "KUp installer ${ANSI_GREEN_155}1.0.0$ANSI_RESET"
        super.arguments = arrayListOf("new", "help")
    }

    override fun execute(vararg args: String) {
        super.showDescription()

        super.showUsage()

        this.showArguments()
    }

    override fun showArguments() {
        println(" ${ANSI_YELLOW_229}• Available commands:")

        for (commands in super.arguments) {
            println("   $ANSI_GREEN_155$commands$ANSI_RESET")
        }

        println()
    }
}
