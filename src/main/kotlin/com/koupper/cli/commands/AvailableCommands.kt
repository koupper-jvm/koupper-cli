package com.koupper.cli.commands

object AvailableCommands {
    const val NEW = "new"
    const val HELP = "help"
    const val BUILD = "build"
    const val RUN = "run"
    const val UNDEFINED = "undefined"
    const val DEFAULT = "default"
    const val MODULE = "module"

    fun commands(): Map<String, String> = mapOf(
        NEW to "Creates a module or script",
        RUN to "Runs a kotlin script",
        HELP to "Displays information about a command",
        MODULE to "Operates about a module"
    )
}
