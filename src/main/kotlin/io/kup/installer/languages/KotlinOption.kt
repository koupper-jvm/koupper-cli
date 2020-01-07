package io.kup.installer.languages

import io.kup.installer.ANSIColors.ANSI_BLACK
import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.ANSI_WHITE
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.ANSIColors.YELLOW_BACKGROUND_222
import io.kup.installer.Wizard
import io.kup.installer.constructions.ProjectOption
import io.kup.installer.constructions.ScriptOption

class KotlinOption : Wizard {
    override fun init() {
        print("${ANSI_WHITE}\nlanguage: Kotlin$ANSI_RESET")

        print(
            """


                Create a
                $ANSI_YELLOW_229
                1.- Project (default)
                2.- Script $ANSI_RESET
                
                Choose an option: 
        """.trimIndent()
        )

        val option = readLine()

        when {
            option!!.isEmpty() -> {
                print("$YELLOW_BACKGROUND_222$ANSI_BLACK Using default creation. $ANSI_RESET")

                ProjectOption().init()
            }
            option == "1" -> ProjectOption().init()
            option == "2" -> ScriptOption().init()
            else -> {
                println("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Option $option is not valid. Using default creation.$ANSI_RESET\n")

                ProjectOption().init()
            }
        }
    }
}
