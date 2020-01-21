package io.kup.installer.commands

import io.kup.installer.ANSIColors.ANSI_BLACK
import io.kup.installer.ANSIColors.ANSI_GREEN_155
import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.ANSIColors.YELLOW_BACKGROUND_222
import io.kup.installer.commands.AvailableCommands.NEW
import io.kup.installer.languages.JavaOption
import io.kup.installer.languages.KotlinOption

class NewCommand : Command() {
    init {
        super.name = NEW
        super.usage = "kup ${ANSI_GREEN_155}$name$ANSI_RESET"
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
