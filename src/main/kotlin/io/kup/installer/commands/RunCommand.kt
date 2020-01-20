package io.kup.installer.commands

import io.kup.installer.ANSIColors
import io.kup.installer.ANSIColors.ANSI_BLACK
import io.kup.installer.ANSIColors.ANSI_RED
import io.kup.installer.ANSIColors.ANSI_RESET
import io.kup.installer.ANSIColors.YELLOW_BACKGROUND_222
import io.kup.installer.Command
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

class RunCommand : Command() {
    init {
        super.name = "run"
        super.usage =
            "kup ${ANSIColors.ANSI_GREEN_155}$name${ANSI_RESET} [${ANSIColors.ANSI_GREEN_155}kotlinScriptName${ANSI_RESET}]"
        super.description = "run a kotlin script"
        super.arguments = emptyMap()
    }

    override fun execute(vararg args: String) {
        if (args.isNotEmpty()) {
            val nameOfFileToCompile: String = this.getNameOfFileToCompile(args[0])

            val jarName: String = nameOfFileToCompile.substring(0, nameOfFileToCompile.indexOf(".")).plus(".jar")

            val mainClassName = this.getNameOfMainClass(args[0])

            val kotlinFile = File(nameOfFileToCompile)

            val jarFile = File(jarName)

            try {
                if (!kotlinFile.exists()) {
                    println("\n${YELLOW_BACKGROUND_222}${ANSI_BLACK} The file does not exist. ${ANSI_RESET}\n")

                    exitProcess(7)
                }

                if (jarFile.exists() && jarFile.lastModified() < kotlinFile.lastModified() || !jarFile.exists()) {
                    val process = Runtime.getRuntime()
                        .exec("kotlinc $nameOfFileToCompile -include-runtime -d $jarName")

                    process.waitFor()

                    val errors = process.errorStream.bufferedReader().readText()

                    if (errors.isNotEmpty()) {
                        print("$ANSI_RED$errors$ANSI_RESET")

                        exitProcess(7)
                    }
                }

                val outputExecution = Runtime.getRuntime().exec("kotlin -classpath $jarName $mainClassName")

                outputExecution.waitFor()

                val output = outputExecution.inputStream.bufferedReader().readText()

                print(output)
            } catch (exception: IOException) {
                println()
                println("$YELLOW_BACKGROUND_222 ".padEnd(80))
                println("$ANSI_BLACK ¡Kotlin compiler is not present in your System!  ".padEnd(80))
                println("$YELLOW_BACKGROUND_222 ".padEnd(80))
                println(" Check out https://kotlinlang.org/docs/tutorials/command-line.html ".padEnd(69))
                println("$YELLOW_BACKGROUND_222 ".padEnd(80))
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

            println("\n${YELLOW_BACKGROUND_222}${ANSI_BLACK} The file needs to be a kotlin file. ${ANSI_RESET}\n")

            exitProcess(7)
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
