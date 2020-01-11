package io.kup.installer.commands

object AvailableCommands {
    const val NEW = "new"
    const val HELP = "help"
    const val RUN = "run"
    const val UNDEFINED = "undefined"
    const val DEFAULT = "default"

    fun commands(): Map<String, String> = mapOf(
        NEW to "Create a new resource",
        HELP to "Show the information of a command",
        RUN to "Run a kotlin script"
    )
}
