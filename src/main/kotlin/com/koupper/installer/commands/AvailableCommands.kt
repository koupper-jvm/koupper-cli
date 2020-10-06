package com.koupper.installer.commands

object AvailableCommands {
    const val NEW = "new"
    const val HELP = "help"
    const val BUILD = "build"
    const val RUN = "run"
    const val UNDEFINED = "undefined"
    const val DEFAULT = "default"

    fun commands(): Map<String, String> = mapOf(
        NEW to "Create a new resource",
        HELP to "Show a description for the specified command",
        RUN to "Run a kotlin script",
        BUILD to "Build a deployable project model"
    )
}
