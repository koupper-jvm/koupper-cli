package com.koupper.cli.commands.jobs

import com.koupper.cli.commands.RunCommand
import java.io.File

class JobListHandler: JobSubcommandHandler {
    override fun handle(context: String, args: Array<String>): String {
        val queue = args.find { it.startsWith("--queue=") }?.substringAfter("=") ?: "default"
        val driver = getJobDriverFromConfig(context) ?: "file"

        val scriptPath = "$context/job-runner.kts"
        File(scriptPath).writeText(generateJobRunnerScript(queue, driver))

        return RunCommand().execute(context, "job-runner.kts")
    }

    private fun generateJobRunnerScript(queue: String, driver: String): String = """
        import com.koupper.octopus.annotations.Export
        import com.koupper.orchestrator.JobRunner

        @Export
        val setup: (JobRunner) -> Unit = { runner ->
            runner.listJobs(queue = "$queue", driver = "$driver")
        }
    """.trimIndent()

    private fun getJobDriverFromConfig(context: String): String? {
        val jobsJson = File("$context/jobs.json")
        if (!jobsJson.exists()) return null

        val regex = """"driver"\s*:\s*"(\w+)"""".toRegex()
        return regex.find(jobsJson.readText())?.groupValues?.get(1)
    }
}