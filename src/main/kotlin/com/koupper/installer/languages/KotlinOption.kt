package com.koupper.installer.languages

import com.koupper.installer.ANSIColors.ANSI_BLACK
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.ANSIColors.ANSI_WHITE
import com.koupper.installer.ANSIColors.ANSI_YELLOW_229
import com.koupper.installer.ANSIColors.YELLOW_BACKGROUND_222
import com.koupper.installer.Wizard
import com.koupper.installer.constructions.ProjectOption
import com.koupper.installer.constructions.ScriptOption

class KotlinOption : Wizard {
    override fun init() {
        print("\n${ANSI_WHITE}language: Kotlin$ANSI_RESET")

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
                print("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Using default creation. $ANSI_RESET\n")

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
