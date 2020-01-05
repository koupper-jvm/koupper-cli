package io.kup.installer

import io.kup.installer.ANSIColors.ANSI_WHITE

class KotlinOption : Wizard {
    override fun init() {
        print("$ANSI_WHITE package: ")

        val sourcePackage = readLine()
    }
}
