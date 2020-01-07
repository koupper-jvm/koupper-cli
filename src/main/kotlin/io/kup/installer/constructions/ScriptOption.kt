package io.kup.installer.constructions

import io.kup.installer.ANSIColors
import io.kup.installer.ANSIColors.ANSI_YELLOW_229
import io.kup.installer.Wizard

class ScriptOption : Wizard {
    override fun init() {
        print("${ANSI_YELLOW_229}\nName of file: ${ANSIColors.ANSI_RESET}")

        val fileName = readLine()
    }
}
