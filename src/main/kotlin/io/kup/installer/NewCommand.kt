package io.kup.installer

import io.kup.installer.ANSIColors.ANSI_BLACK
import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.ANSIColors.YELLOW_BACKGROUND_222

class NewCommand : Command() {
    override fun name(): String {
        return "new"
    }

    override fun execute(vararg args: String) {
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
