fun main() {
    CommandManager().init()
}

class CommandManager {
    fun init() {
        when (readLine()) {
            "help" -> HelpCommand().execute()
            "new" -> NewCommand().execute()
            "-v", "--v", "--version" -> DefaultCommand().showDescription()
            else -> DefaultCommand().execute()
        }
    }
}
