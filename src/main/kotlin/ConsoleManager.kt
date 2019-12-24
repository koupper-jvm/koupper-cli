fun main() {
    ConsoleManager().init()
}

class ConsoleManager {
    fun init() {
        when (readLine()) {
            "help" -> HelpCommand().execute()
            "new" -> NewCommand().execute()
            "-v", "--v", "--version" -> DefaultCommand().showDescription()
            else -> DefaultCommand().execute()
        }
    }
}
