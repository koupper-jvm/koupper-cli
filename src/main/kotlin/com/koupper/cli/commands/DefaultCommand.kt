package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_WHITE
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import com.koupper.cli.commands.AvailableCommands.DEFAULT
import com.koupper.cli.commands.AvailableCommands.commands

class DefaultCommand : Command() {
    override fun name(): String {
        return DEFAULT
    }

    init {
        super.name = "koupper"
        super.usage = "$name [${ANSI_GREEN_155}command$ANSI_RESET]"
        super.description = "koupper cli ${ANSI_GREEN_155}3.5.0$ANSI_RESET"
        super.arguments = commands()
        super.additionalInformation = """
   note: You should use the ${ANSI_WHITE}koupper ${ANSI_GREEN_155}help $ANSI_WHITE[${ANSI_GREEN_155}command$ANSI_WHITE]$ANSI_RESET ${ANSI_YELLOW_229}option to
   check the usage options.
        """
    }

    override fun execute(vararg args: String) {
        super.showDescription()

        super.showUsage()

        super.showAdditionalInformation()

        super.showArguments()
    }
}
