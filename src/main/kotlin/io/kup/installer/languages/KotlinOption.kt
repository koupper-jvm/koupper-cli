package io.kup.installer.languages

import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.ANSI_WHITE
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.Wizard

class KotlinOption : Wizard {
    override fun init() {
        print("${ANSI_WHITE}\nlanguage: Kotlin$ANSI_RESET")

        print(
            """
                
                $ANSI_YELLOW_229
                1.- Project
                2.- Script $ANSI_RESET
                
                Choose an option: 
        """.trimIndent()
        )

        val option = readLine()
    }
}
