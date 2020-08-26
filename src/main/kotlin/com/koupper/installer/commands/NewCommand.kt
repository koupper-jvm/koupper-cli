package com.koupper.installer.commands

import com.koupper.installer.ANSIColors.ANSI_BLACK
import com.koupper.installer.ANSIColors.ANSI_GREEN_155
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.ANSIColors.ANSI_YELLOW_229
import com.koupper.installer.ANSIColors.YELLOW_BACKGROUND_222
import com.koupper.installer.commands.AvailableCommands.NEW
import com.koupper.installer.languages.JavaOption
import com.koupper.installer.languages.KotlinOption

class NewCommand : Command() {
    init {
        super.name = NEW
        super.usage = "koupper ${ANSI_GREEN_155}$name$ANSI_RESET"
        super.description = "Initializes a wizard to create a new resource"
        super.arguments = emptyMap()
    }

    override fun name(): String {
        return NEW
    }

    override fun execute(vararg args: String) {
        this.askForLanguage()
    }

    private fun askForLanguage() {
        print(
            """

            Select a language
            $ANSI_YELLOW_229
            1.- Kotlin (default)
            2.- Java $ANSI_RESET
            
            Choose an option: 
        """.trimIndent()
        )

        val option = readLine()

        when {
            option!!.isEmpty() -> {
                print("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Using default language. $ANSI_RESET\n")

                KotlinOption().init()
            }
            option == "1" -> KotlinOption().init()
            option == "2" -> JavaOption().init()
            else -> {
                println("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Option $option is not valid. Using default language. $ANSI_RESET\n")

                KotlinOption().init()
            }
        }
    }
}
