package com.koupper.cli.commands

object AvailableCommands {
    const val NEW = "new"
    const val HELP = "help"
    const val BUILD = "build"
    const val RUN = "run"
    const val UNDEFINED = "undefined"
    const val DEFAULT = "default"

    fun commands(): Map<String, String> = mapOf(
        NEW to "Creates a module or environment",
        HELP to "Shows the commands description",
        RUN to "Run a kotlin script"
    )
}
