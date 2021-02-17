package com.koupper.cli.languages

import com.koupper.cli.ANSIColors.ANSI_BLACK
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_WHITE
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import com.koupper.cli.ANSIColors.YELLOW_BACKGROUND_222
import com.koupper.cli.Wizard
import com.koupper.cli.constructions.ProjectOption
import com.koupper.cli.constructions.ScriptOption

class KotlinOption : Wizard {
    override fun init() {
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
