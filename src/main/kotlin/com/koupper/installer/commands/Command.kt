package com.koupper.installer.commands

import com.koupper.installer.ANSIColors.ANSI_GREEN_155
import com.koupper.installer.ANSIColors.ANSI_RESET
import com.koupper.installer.ANSIColors.ANSI_YELLOW_229

abstract class Command {
    lateinit var name: String
    lateinit var usage: String
    lateinit var description: String
    lateinit var arguments: Map<String, String>

    abstract fun execute(vararg args: String)

    abstract fun name(): String

    open fun showUsage() {
        println(" ${ANSI_YELLOW_229}- Usage:$ANSI_RESET")

        println("   $usage")

        println()
    }

    open fun showDescription() {
        println("\n  $ANSI_GREEN_155$ANSI_RESET$ANSI_YELLOW_229 $description \n")
    }

    open fun showArguments() {
        println(" ${ANSI_YELLOW_229}- Commands:$ANSI_RESET")

        var maxLengthOfCommand = 0

        this.arguments.forEach { (commandName, _) ->
            if (commandName.length > maxLengthOfCommand) {
                maxLengthOfCommand = commandName.length
            }
        }

        this.arguments.forEach { (commandName, description) ->
            val message = "$commandName".padEnd(maxLengthOfCommand + 3)

            println("   $ANSI_GREEN_155$message$ANSI_RESET$description")
        }

        println()
    }
}
