package io.kup.installer.commands

import io.kup.installer.ANSIColors
import io.kup.installer.Command
import java.io.IOException

class RunCommand : Command() {
    init {
        super.name = "run"
        super.usage =
            "kup ${ANSIColors.ANSI_GREEN_155}$name${ANSIColors.ANSI_RESET} [${ANSIColors.ANSI_GREEN_155}kotlinScriptName${ANSIColors.ANSI_RESET}]"
        super.description = "run a kotlin script"
        super.arguments = emptyMap()
    }

    override fun execute(vararg args: String) {
        if (args.isNotEmpty()) {
            val nameOfFileToCompile: String = this.getNameOfFileToCompile(args[0])

            val jarName: String = nameOfFileToCompile.substring(0, nameOfFileToCompile.indexOf(".")).plus(".jar")

            val mainClassName = this.getNameOfMainClass(args[0])

            try {
                val process = Runtime.getRuntime()
                    .exec("kotlinc $nameOfFileToCompile -include-runtime -d $jarName")

                process.waitFor()

                val outputExecution = Runtime.getRuntime().exec("kotlin -classpath $jarName $mainClassName")

                outputExecution.waitFor()

                print(outputExecution.inputStream.bufferedReader().readText())
            } catch (exception: IOException) {
                println()
                println("${ANSIColors.YELLOW_BACKGROUND_222} ".padEnd(80))
                println("${ANSIColors.ANSI_BLACK} ¡Kotlin compiler is not present in your System!  ".padEnd(80))
                println("${ANSIColors.YELLOW_BACKGROUND_222} ".padEnd(80))
                println(" Check out https://kotlinlang.org/docs/tutorials/command-line.html ".padEnd(69))
                println("${ANSIColors.YELLOW_BACKGROUND_222} ".padEnd(80))
                println()
            }
        }
    }

    override fun name(): String {
        return AvailableCommands.RUN
    }

    private fun getNameOfFileToCompile(name: String): String {
        if (name.contains(".")) {
            if (name.matches("\\w+\\.?kt$".toRegex())) {
                return name.trim()
            }

            print("\n${ANSIColors.YELLOW_BACKGROUND_222}${ANSIColors.ANSI_BLACK} The file needs to be a kotlin file. ${ANSIColors.ANSI_RESET}\n")
        }

        return name.trim().plus(".kt")
    }

    private fun getNameOfMainClass(name: String): String {
        if (!name.contains(".")) {
            return name.trim().capitalize() + "Kt"
        }

        return name.substring(0, name.indexOf(".")).capitalize() + "Kt"
    }
}
