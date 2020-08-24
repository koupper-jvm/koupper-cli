package com.koupper.installer.commands

import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.ANSIColors.ANSI_WHITE
import com.koupper.installer.ANSIColors.RED_BACKGROUND_203
import com.koupper.installer.commands.AvailableCommands.UNDEFINED

class UndefinedCommand : Command() {
    override fun name(): String {
        return UNDEFINED
    }

    override fun execute(vararg args: String) {
        println("\n$RED_BACKGROUND_203$ANSI_WHITE The command '${args[0]}' is undefined. $ANSI_RESET \n")
    }
}
