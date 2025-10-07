package com.koupper.cli.commands.jobs

import com.koupper.cli.commands.RunCommand
import java.io.File

class JobListHandler : JobSubcommandHandler {
    override fun handle(context: String, args: Array<String>): String {
        val queueArg = args.find { it.startsWith("--queue=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }
        val jobIdArg = args.find { it.startsWith("--jobId=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }

        val queue = queueArg ?: getJobQueueFromConfig(context) ?: "default"
        val driver = getJobDriverFromConfig(context) ?: "file"
        val jobId: String? = jobIdArg // null => lista todos

        val scriptPath = "$context/job-runner.kts"
        File(scriptPath).writeText(generateJobRunnerScript(queue, driver, jobId))

        return RunCommand().execute(context, "job-runner.kts")
    }

    private fun generateJobRunnerScript(queue: String, driver: String, jobId: String?): String {
        val jobIdLiteral = jobId?.let { "\"$it\"" } ?: "null"
        return """
        import com.koupper.octopus.annotations.Export
        import com.koupper.orchestrator.JobLister
        import com.koupper.orchestrator.JobResult
    
        @Export
        val setup: (JobLister) -> String = { runner ->
            val results = runner.list(queue = "$queue", driver = "$driver", jobId = $jobIdLiteral)
            val sb = StringBuilder()
    
            results.forEach { res ->
                when (res) {
                    is JobResult.Ok -> {
                        val t = res.task
                        sb.appendLine("📦 Job ID: ${'$'}{t.id}")
                        sb.appendLine(" - Function: ${'$'}{t.functionName}")
                        sb.appendLine(" - Params: ${'$'}{t.params}")
                        sb.appendLine(" - Source: ${'$'}{t.scriptPath}")
                        sb.appendLine(" - Context: ${'$'}{t.context}")
                        sb.appendLine(" - Version: ${'$'}{t.contextVersion}")
                        sb.appendLine(" - Origin: ${'$'}{t.origin}")
                        sb.appendLine()
                    }
                    is JobResult.Error -> {
                        sb.appendLine()
                        sb.appendLine("${'$'}{res.message}")
                    }
                }
            }
    
            sb.toString()
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
