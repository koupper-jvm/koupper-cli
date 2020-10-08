package com.koupper.installer.commands

import com.koupper.installer.ANSIColors.ANSI_GREEN_155
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.commands.AvailableCommands.DEFAULT
import com.koupper.installer.commands.AvailableCommands.commands

class DefaultCommand : Command() {
    override fun name(): String {
        return DEFAULT
    }

    init {
        super.name = "koupper"
        super.usage = "$name [${ANSI_GREEN_155}command$ANSI_RESET]"
        super.description = "koupper installer ${ANSI_GREEN_155}1.5.1$ANSI_RESET"
        super.arguments = commands()
    }

    override fun execute(vararg args: String) {
        super.showDescription()

        super.showUsage()

        super.showArguments()
    }
}
