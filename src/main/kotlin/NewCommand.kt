import ANSIColors.ANSI_BLACK
import ANSIColors.ANSI_RESET
import ANSIColors.ANSI_YELLOW_229
import ANSIColors.YELLOW_BACKGROUND_222

class NewCommand : Command() {
    override fun execute() {
        print(
            """
            $ANSI_YELLOW_229
            1.- Kotlin (default)
            2.- Java $ANSI_RESET
            
            Choose an option: 
        """.trimIndent()
        )

        val selectedOption = readLine()

        when {
            selectedOption!!.isEmpty() -> {
                print("$YELLOW_BACKGROUND_222$ANSI_BLACK Using default language. ")

                KotlinOption().init()
            }
            selectedOption == "2" -> JavaOption().init()
            else -> print("$YELLOW_BACKGROUND_222$ANSI_BLACK Option $selectedOption is not valid. Using default language. ")
        }
    }
}
