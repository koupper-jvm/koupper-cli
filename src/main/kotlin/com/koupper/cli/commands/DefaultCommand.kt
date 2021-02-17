package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.commands.AvailableCommands.DEFAULT
import com.koupper.cli.commands.AvailableCommands.commands

class DefaultCommand : Command() {
    override fun name(): String {
        return DEFAULT
    }

    init {
        super.name = "koupper"
        super.usage = "$name [${ANSI_GREEN_155}command$ANSI_RESET]"
        super.description = "koupper cli ${ANSI_GREEN_155}3.0.0$ANSI_RESET"
        super.arguments = commands()
    }

    override fun execute(vararg args: String) {
        super.showDescription()

        super.showUsage()

        super.showArguments()
    }
}
