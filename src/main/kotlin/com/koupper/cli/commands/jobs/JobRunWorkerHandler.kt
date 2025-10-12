package com.koupper.cli.commands.jobs

import com.koupper.cli.commands.RunCommand
import java.io.File

class JobRunWorkerHandler : JobSubcommandHandler {
    override fun handle(context: String, args: Array<String>): String {
        val jobIdArg = args.find { it.startsWith("--jobId=") }?.substringAfter("=")
        val configId = args.find { it.startsWith("--configId=") }?.substringAfter("=")?.takeIf { it.isNotBlank() }

        val scriptPath = "$context/job-runner.kts"
        File(scriptPath).writeText(generateJobRunnerScript(jobIdArg, configId))

        return RunCommand().execute(context, "job-runner.kts")
    }

    /**
     * Genera el script donde se define el objeto JobConfiguration dentro del archivo
     */
    private fun generateJobRunnerScript(configId: String?, jobId: String?): String {
        val jobIdLiteral = jobId?.let { "\"$it\"" } ?: "null"
        val configIdLiteral = configId?.let { "\"$it\"" } ?: "null"

        return """
        import com.koupper.octopus.annotations.Export
        import com.koupper.orchestrator.JobRunner
        import com.koupper.container.context
        import com.koupper.orchestrator.JobResult
        
        @Export
        val setup: (JobRunner) -> String = { runner ->
            val sb = StringBuilder()
        
            runner.runPendingJobs(context!!, jobId = $jobIdLiteral, configId = $configIdLiteral) { res ->
                res.forEach {
                    when (it) {
                        is JobResult.Ok -> {
                            val t = it.task
                            sb.appendLine("FROM CONFIG ID: ${'$'}{it.configName}")
                            sb.appendLine("📦 Job ID: ${'$'}{t.id}")
                            sb.appendLine(" - Function: ${'$'}{t.functionName}")
                            sb.appendLine(" - Params: ${'$'}{t.params}")
                            sb.appendLine(" - Source: ${'$'}{t.sourceSnapshot}")
                            sb.appendLine(" - Context: ${'$'}{t.context}")
                            sb.appendLine(" - Version: ${'$'}{t.contextVersion}")
                            sb.appendLine(" - Origin: ${'$'}{t.origin}")
                            sb.appendLine()
                        }
                        is JobResult.Error -> {
                            sb.appendLine()
                            sb.appendLine("${'$'}{it.message}")
                        }
                    }
                }
            }
            
            sb.toString()
        }
    """.trimIndent()
    }

}
