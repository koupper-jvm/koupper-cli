package com.koupper.installer.constructions

import com.koupper.installer.ANSIColors.ANSI_BLACK
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.ANSIColors.ANSI_YELLOW_229
import com.koupper.installer.ANSIColors.YELLOW_BACKGROUND_222
import com.koupper.installer.Wizard
import com.koupper.installer.buildtools.GradleOption
import com.koupper.installer.buildtools.MavenOption

class ProjectOption : Wizard {
    override fun init() {
        this.askForBuildTool()
    }

    private fun askForBuildTool() {
        print(
            """
            
            Select a build tool
            $ANSI_YELLOW_229
            1.- Gradle (default)
            2.- Maven $ANSI_RESET
            
            Choose an option: 
        """.trimIndent()
        )

        val option = readLine()

        when {
            option!!.isEmpty() -> {
                print("$YELLOW_BACKGROUND_222$ANSI_BLACK Using default build tool. $ANSI_RESET")

                GradleOption().init()
            }
            option == "1" -> GradleOption().init()
            option == "2" -> MavenOption().init()
            else -> {
                println("\n$YELLOW_BACKGROUND_222$ANSI_BLACK Option $option is not valid. Using default build tool. $ANSI_RESET\n")

                GradleOption().init()
            }
        }
    }
}
