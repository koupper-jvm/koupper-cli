package io.kup.installer.constructions

import io.kup.installer.ANSIColors
import io.kup.installer.Wizard
import io.kup.installer.buildtools.GradleOption
import io.kup.installer.buildtools.MavenOption

class ProjectOption : Wizard {
    override fun init() {
        this.askForBuildTool()
    }

    private fun askForBuildTool() {
        print(
            """
            
            Select a build tool
            ${ANSIColors.ANSI_YELLOW_229}
            1.- Gradle (default)
            2.- Maven ${ANSIColors.ANSI_RESET}
            
            Choose an option: 
        """.trimIndent()
        )

        val option = readLine()

        when {
            option!!.isEmpty() -> {
                print("${ANSIColors.YELLOW_BACKGROUND_222}${ANSIColors.ANSI_BLACK} Using default build tool. ${ANSIColors.ANSI_RESET}")

                GradleOption().init()
            }
            option == "1" -> GradleOption().init()
            option == "2" -> MavenOption().init()
            else -> {
                println("\n${ANSIColors.YELLOW_BACKGROUND_222}${ANSIColors.ANSI_BLACK} Option $option is not valid. Using default build tool. ${ANSIColors.ANSI_RESET}\n")

                GradleOption().init()
            }
        }
    }
}
