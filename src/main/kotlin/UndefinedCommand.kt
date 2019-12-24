class UndefinedCommand : Command() {
    override fun name(): String {
        return "undefined"
    }

    override fun execute(vararg args: String) {
        print("")
    }
}
