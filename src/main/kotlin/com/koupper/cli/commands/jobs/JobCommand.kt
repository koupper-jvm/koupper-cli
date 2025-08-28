package com.koupper.cli.commands.jobs

import com.koupper.cli.ANSIColors
import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.commands.AvailableCommands.JOB
import com.koupper.cli.commands.Command

class JobCommand : Command() {
    init {
        super.name = JOB
        super.usage = "\n" + """
   koupper job init [--force]                                    Initializes the jobs.json configuration file.
   koupper job build-worker [--queue=queue][--jobId=queue]       Builds a runnable worker JAR for a queue.
   koupper job run-worker [--jobId=queue]                        Starts a worker listening to a queue.
   koupper job list [--jobId=queue]                              Lists available jobs (optionally by queue).
   koupper job status                                            Shows current job system status.
   koupper job failed                                            Shows failed jobs.
   koupper job retry [jobId]                                     Retries a failed job.
        """

        super.description = "\n   Creates and manages background job workers\n"
        super.arguments = emptyMap()
        super.additionalInformation = """
   For more info: https://koupper.com/cli/commands/job
        """
        super.arguments = mapOf(
            "--force" to "Initializes the job system: generates jobs.json and base Worker class.",
            "--queue=name" to "Specifies the target queue",
            "--concurrency=N" to "Number of workers to spawn"
        )
    }

    override fun name(): String = JOB

    override fun showArguments(): String {
        val argHeader = "\n ${ANSIColors.ANSI_YELLOW_229}  Flags:$ANSI_RESET \n"

        var finalArgInfo = ""

        this.arguments.forEach { (commandName, _) ->
            finalArgInfo += "   $ANSI_GREEN_155$commandName$ANSI_RESET\n"
        }

        return "$argHeader$finalArgInfo"
    }

    override fun execute(vararg args: String): String {
        val validSubcommands = listOf(
            "init", "build-worker", "run-worker", "list", "status", "failed", "retry"
        )

        val context = args.firstOrNull() ?: "."
        val realArgs = args.drop(1)

        if (realArgs.isEmpty()) {
            return this.usage
        }

        val subcommand = realArgs.first()

        if (subcommand !in validSubcommands) {
            return "\n${ANSIColors.ANSI_RED}Unknown job subcommand: '$subcommand'. Try 'koupper job help'.$ANSI_RESET\n"
        }

        val recognizedFlags = super.arguments.keys
        val usedFlags = realArgs.drop(1).filter { it.startsWith("--") }

        val unrecognized = usedFlags.filter { flag ->
            recognizedFlags.none { valid ->
                if (valid.contains('=')) {
                    flag.startsWith(valid.substringBefore("="))
                } else {
                    flag == valid
                }
            }
        }

        if (unrecognized.isNotEmpty()) {
            val formatted = unrecognized.joinToString(", ")
            return "\n${ANSIColors.ANSI_RED}Unrecognized flags: $formatted${ANSI_RESET}\n"
        }

        return when (subcommand) {
            "init" -> JobInitHandler().handle(context, arrayOf(*args))
            "run-worker" -> JobRunWorkerHandler().handle(context, arrayOf(*args))
            "list" -> JobListHandler().handle(context, arrayOf(*args))
            "build-worker" -> JobBuildWorkerHandler().handle(context, arrayOf(*args))
            "status" -> JobSystemStatusHandler().handle(context, arrayOf(*args))
            /*
            "failed" -> JobFailedHandler().handle(args)
            "retry" -> JobRetryHandler().handle(args)
            "help" -> this.usage*/
            else -> this.usage
        }
    }

}
