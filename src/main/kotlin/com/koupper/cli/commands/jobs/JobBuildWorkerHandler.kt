package com.koupper.cli.commands.jobs

import com.koupper.cli.commands.RunCommand
import java.io.File

class JobBuildWorkerHandler : JobSubcommandHandler {
    override fun handle(context: String, args: Array<String>): String {
        val queue = args.getOrNull(1) ?: "default"
        val driver = getJobDriverFromConfig(context) ?: "file"

        val scriptPath = "$context/build-worker.kts"
        File(scriptPath).writeText(generateBuildWorkerScript(queue, driver))

        return RunCommand().execute(context, "build-worker.kts")
    }

    private fun generateBuildWorkerScript(queue: String, driver: String): String {
        return """
            import com.koupper.octopus.annotations.Export
            import com.koupper.orchestrator.JobBuilder

            @Export
            val setup: (JobBuilder) -> Unit = { runner ->
                runner.buildWorker(queue = "$queue", driver = "$driver")
            }
        """.trimIndent()
    }

    private fun getJobDriverFromConfig(context: String): String? {
        val jobsJson = File("$context/jobs.json")
        if (!jobsJson.exists()) return null
        val rx = """"driver"\s*:\s*"([\w\-]+)"""".toRegex()
        return rx.find(jobsJson.readText())?.groupValues?.get(1)
    }
}
