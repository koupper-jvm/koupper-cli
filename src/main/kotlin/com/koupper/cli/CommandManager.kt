package com.koupper.cli

import com.koupper.cli.commands.*
import com.koupper.cli.commands.AvailableCommands.HELP
import com.koupper.cli.commands.AvailableCommands.NEW
import com.koupper.cli.commands.AvailableCommands.RUN
import java.io.File
import java.io.IOException
import java.net.URL
import kotlin.system.exitProcess

class CommandManager {
    fun process(arg: Array<String>) {
        this.checkForUpdates()

        if (arg.isEmpty()) {
            DefaultCommand().execute()

            return
        }

        if (this.isFlagVersion(arg[0])) {
            DefaultCommand().showDescription()

            return
        }

        val command = getCommandByName(arg[0])

        if (command is UndefinedCommand) {
            command.execute(arg[0])

            return
        }

        val args = this.getArgsFrom(arg)

        command.execute(*args)
    }

    private fun checkForUpdates() {
        try {
            val userPath = System.getProperty("user.home")

            val process = Runtime.getRuntime()
                    .exec("$userPath/.koupper/helpers/octopusBootstrapper.sh UPDATING_CHECK")

            process.waitFor()

            val errors = process.errorStream.bufferedReader().readText()

            if (errors.isNotEmpty()) {
                print("${ANSIColors.ANSI_RED}$errors${ANSIColors.ANSI_RESET}")

                exitProcess(7)
            }

            val output = process.inputStream.bufferedReader().readText()

            if (output == "AVAILABLE_UPDATES") {
                print("updates are available, Would you like apply them now? [y/n] ")

                when (readLine()) {
                    "y" , "Y" -> {
                        val content = URL("https://lib-installer.s3.amazonaws.com/updateme.txt").readText()

                        val file = File("$userPath/.koupper/helpers/updateme.kts")
                        file.writeText(content)
                        file.createNewFile()
                        file.setExecutable(true)
                        file.setReadable(true)
                        file.setWritable(true)

                        exitProcess(0)
                    }
                    else -> return
                }
            }
        } catch (exception: IOException) {
            println()
            println(ANSIColors.ANSI_RED + exception.printStackTrace())
            println()
        }
    }

    fun getCommandByName(input: String): Command {
        return when (input) {
            HELP -> HelpCommand()
            NEW -> NewCommand()
            RUN -> RunCommand()
            else -> UndefinedCommand()
        }
    }

    private fun isFlagVersion(input: String): Boolean {
        return input == "-v" || input == "--v" || input == "--version"
    }

    private fun getArgsFrom(arg: Array<String>): Array<String> {
        return if (arg.size > 1) arg.sliceArray(1 until arg.size) else emptyArray()
    }
}

fun main(args: Array<String>) {
    CommandManager().process(args)
}
