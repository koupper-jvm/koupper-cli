package com.koupper.cli

import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.commands.*
import com.koupper.cli.commands.AvailableCommands.HELP
import com.koupper.cli.commands.AvailableCommands.NEW
import com.koupper.cli.commands.AvailableCommands.RUN
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private val userPath = System.getProperty("user.home")

private fun checkForUpdatesFrom(baseDate: String) {
    try {
        val process = Runtime.getRuntime()
                .exec("$userPath/.koupper/helpers/octopusBootstrapper.sh UPDATING_CHECK")

        process.waitFor()

        val errors = process.errorStream.bufferedReader().readText()

        if (errors.isNotEmpty()) {
            print("$ANSI_RED$errors${ANSIColors.ANSI_RESET}")

            exitProcess(7)
        }

        val version = File("$userPath/.koupper/helpers/.update_info")
        version.createNewFile()

        val output = process.inputStream.bufferedReader().readText()
        val readyForUpdate = output == "AVAILABLE_UPDATES"

        version.printWriter().use { out -> out.println("{\"last_request\": $baseDate, \"ready_for_update\": $readyForUpdate") }
    } catch (exception: IOException) {
        println("\n$ANSI_RED${exception.printStackTrace()}\n")
    }
}


class CommandManager {
    fun process(arg: Array<String>) {
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

    fun notifyAboutUpdate(baseDate: String) {
        print("updates are available, Would you like apply them now? [y/n] ")

        val updateInfo = File("$userPath/.koupper/helpers/.update_info")

        when (readLine()) {
            "y", "Y" -> {
                val content = URL("https://lib-installer.s3.amazonaws.com/updateme.txt").readText()

                val file = File("$userPath/.koupper/helpers/updateme.kts")
                file.writeText(content)
                file.createNewFile()
                file.setExecutable(true)
                file.setReadable(true)
                file.setWritable(true)
                updateInfo.printWriter().use { out -> out.println("{\"last_request\": ${Date().time}, \"ready_for_update\": false}") }
            }
            else -> {
                val lastRequest = Date(baseDate.toLong() - (3600 * 3) * 1000).time

                updateInfo.printWriter().use { out -> out.println("{\"last_request\": $lastRequest, \"ready_for_update\": true}") }
            }
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

fun main(args: Array<String>) = runBlocking {
    val commandManager = CommandManager()

    val versionFile = File("$userPath/.koupper/helpers/.update_info")

    if (versionFile.exists()) {
        val updateInfo = versionFile.readLines().first()

        val date = "\\D".toRegex().split(updateInfo).first {
            it.isNotEmpty()
        }

        val timeElapsedSinceLastRequest = Date().time - Date(date.toLong()).time

        val hoursElapsedSinceLastRequest = TimeUnit.MILLISECONDS.toHours(timeElapsedSinceLastRequest)

        if (hoursElapsedSinceLastRequest >= 24) {
            if ("true|false".toRegex().find(updateInfo)!!.value.toBoolean()) {
                if (hoursElapsedSinceLastRequest.rem(4) == 0L) {
                    commandManager.notifyAboutUpdate(date)
                }
            } else {
                launch {
                    checkForUpdatesFrom(date)
                }
            }
        }
    }

    commandManager.process(args)
}
