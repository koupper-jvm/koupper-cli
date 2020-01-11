package io.kup.installer.commands

import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.ANSI_WHITE
import io.kup.installer.ANSIColors.RED_BACKGROUND_203
import io.kup.installer.Command
import io.kup.installer.commands.AvailableCommands.UNDEFINED

class UndefinedCommand : Command() {
    override fun name(): String {
        return UNDEFINED
    }

    override fun execute(vararg args: String) {
        println("\n$RED_BACKGROUND_203$ANSI_WHITE The command '${args[0]}' is undefined. $ANSI_RESET \n")
    }
}
