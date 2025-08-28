package com.koupper.cli.commands.jobs

import com.koupper.cli.commands.RunCommand
import java.io.File

class JobSystemStatusHandler : JobSubcommandHandler {
    override fun handle(context: String, args: Array<String>): String {
        val driver = getJobDriverFromConfig(context) ?: "❓ (no driver found)"
        val queue = getJobQueueFromConfig(context) ?: "❓ (no queue found)"

        val scriptPath = "$context/job-runner.kts"
        File(scriptPath).writeText(generateJobDisplayerScript(queue, driver))

        val statusHeader = """
   🧠 Current Job Configuration
   ─────────────────────────────
   🛠️  Driver: $driver
   📦  Queue:  $queue        
        """

        return statusHeader + RunCommand().execute(context, "job-runner.kts")
    }

    private fun generateJobDisplayerScript(queue: String, driver: String): String {
        return """
            import com.koupper.octopus.annotations.Export
            import com.koupper.orchestrator.JobDisplayer

            @Export
            val setup: (JobDisplayer) -> Unit = { displayer ->
                displayer.showStatus(queue = "$queue", driver = "$driver")
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
