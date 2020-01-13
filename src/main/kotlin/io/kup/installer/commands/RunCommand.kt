package io.kup.installer.commands

import io.kup.installer.ANSIColors
import io.kup.installer.Command

class RunCommand : Command() {
    init {
        super.name = "run"
        super.usage = "kup run ${ANSIColors.ANSI_GREEN_155}kotlinScriptName${ANSIColors.ANSI_RESET}"
        super.description = "run a kotlin script"
        super.arguments = emptyMap()
    }

    override fun execute(vararg args: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun name(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
