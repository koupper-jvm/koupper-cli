package com.koupper.cli.commands.jobs

import com.koupper.cli.commands.RunCommand
import java.io.File

class JobRunWorkerHandler : JobSubcommandHandler {
    override fun handle(context: String, args: Array<String>): String {
        val queueArg = args.find { it.startsWith("--queue=") }?.substringAfter("=")
        val jobIdArg = args.find { it.startsWith("--jobId=") }?.substringAfter("=")

        val queue = queueArg ?: getJobQueueFromConfig(context) ?: "default"
        val jobId: String? = jobIdArg?.takeIf { it.isNotBlank() }
        val driver = getJobDriverFromConfig(context) ?: "file"

        val scriptPath = "$context/job-runner.kts"
        File(scriptPath).writeText(generateJobRunnerScript(queue, driver, jobId))

        return RunCommand().execute(context, "job-runner.kts")
    }

    private fun generateJobRunnerScript(queue: String, driver: String, jobId: String?): String {
        val jobIdLiteral = jobId?.let { "\"$it\"" } ?: "null"
        return """
            import com.koupper.octopus.annotations.Export
            import com.koupper.orchestrator.JobRunner

            @Export
            val setup: (JobRunner) -> Unit = { runner ->
                runner.runPendingJobs(queue = "$queue", driver = "$driver", jobId = $jobIdLiteral)
            }
        """.trimIndent()
    }

    private fun getJobDriverFromConfig(context: String): String? {
        val jobsJson = File("$context/jobs.json")
        if (!jobsJson.exists()) return null
        val rx = """"driver"\s*:\s*"([\w\-]+)"""".toRegex()
        return rx.find(jobsJson.readText())?.groupValues?.get(1)
    }

    private fun getJobQueueFromConfig(context: String): String? {
        val jobsJson = File("$context/jobs.json")
        if (!jobsJson.exists()) return null
        val rx = """"queue"\s*:\s*"([\w\-]+)"""".toRegex()
        return rx.find(jobsJson.readText())?.groupValues?.get(1)
    }
}
