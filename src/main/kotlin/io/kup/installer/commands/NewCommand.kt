package io.kup.installer.commands

import io.kup.installer.ANSIColors.ANSI_BLACK
import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.ANSIColors.YELLOW_BACKGROUND_222
import io.kup.installer.Command
import io.kup.installer.JavaOption
import io.kup.installer.KotlinOption

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
                print("$YELLOW_BACKGROUND_222$ANSI_BLACK Using default language. $ANSI_RESET")

                KotlinOption().init()
            }
            selectedOption == "2" -> JavaOption().init()
            selectedOption == "1" -> KotlinOption().init()
            else -> {
                println("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Option $selectedOption is not valid. Using default language.$ANSI_RESET\n")

                KotlinOption().init()
            }
        }
    }
}
