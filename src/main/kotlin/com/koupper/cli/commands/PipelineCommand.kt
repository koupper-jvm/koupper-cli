package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File

// Submits, tracks, and scaffolds multi-step pipelines without writing a .kts script.
//
// Usage:
//   koupper pipeline new    <name>          [--steps=N] [--queue=<queue>]
//   koupper pipeline submit <pipeline.json> [--jobs-dir=path]
//   koupper pipeline status <pipelineId>    [--jobs-dir=path]
//
// Pipeline file format:
//   {
//     "id":    "my-pipeline",        (auto-generated when omitted)
//     "queue": "default",            (defaults to "default")
//     "steps": [
//       { "agent": "agents/Step1.kts", "input": {"key": "val"} },
//       { "agent": "agents/Step2.kts" },
//       { "agent": "agents/Step3.kts" }
//     ]
//   }
//
// Each step's result is forwarded as the next step's input via pipelineNext chaining.
class PipelineCommand : Command() {

    override fun name(): String = "pipeline"

    private val home = System.getProperty("user.home")!!

    override fun execute(vararg args: String): String {
        val positionals = args.drop(1).filter { !it.startsWith("--") }
        val subcommand  = positionals.firstOrNull() ?: return usage()

        val jobsDir = args.firstOrNull { it.startsWith("--jobs-dir=") }
            ?.removePrefix("--jobs-dir=")
            ?.let { File(it) }
            ?: File("$home/.koupper/jobs")

        return when (subcommand) {
            "new" -> {
                val name = positionals.getOrNull(1)
                    ?: return "\n  ${ANSI_RED}Error: pipeline name required${ANSI_RESET}\n  Usage: koupper pipeline new <name> [--steps=N] [--queue=<queue>]\n"
                val stepsCount = args.firstOrNull { it.startsWith("--steps=") }
                    ?.removePrefix("--steps=")?.toIntOrNull()?.coerceAtLeast(1) ?: 2
                val queue = args.firstOrNull { it.startsWith("--queue=") }
                    ?.removePrefix("--queue=") ?: "default"
                scaffoldPipeline(name, stepsCount, queue, File(args[0]))
            }
            "submit" -> {
                val path = positionals.getOrNull(1)
                    ?: return "\n  ${ANSI_RED}Error: pipeline file path required${ANSI_RESET}\n  Usage: koupper pipeline submit <pipeline.json>\n"
                val pipelineFile = File(path).let { if (it.isAbsolute) it else File(args[0], path) }
                submit(pipelineFile, jobsDir)
            }
            "status" -> {
                val pipelineId = positionals.getOrNull(1)
                    ?: return "\n  ${ANSI_RED}Error: pipeline id required${ANSI_RESET}\n  Usage: koupper pipeline status <pipelineId>\n"
                statusPipeline(pipelineId, jobsDir)
            }
            else -> usage()
        }
    }

    // ── New (scaffold) ───────────────────────────────────────────────────────

    private fun scaffoldPipeline(name: String, stepsCount: Int, queue: String, baseDir: File): String {
        val pipelineDir = File(baseDir, name)
        if (pipelineDir.exists()) {
            return "\n  ${ANSI_RED}Error: '${pipelineDir.absolutePath}' already exists${ANSI_RESET}\n"
        }

        val agentsDir = File(pipelineDir, "agents")
        agentsDir.mkdirs()

        val stepNames = (1..stepsCount).map { "Step$it" }

        val stepsJson = stepNames.mapIndexed { i, stepName ->
            val inputPart = if (i == 0) """, "input": {}""" else ""
            """    {"agent": "agents/$stepName.kts"$inputPart}"""
        }.joinToString(",\n")

        val pipelineJson = buildString {
            appendLine("{")
            appendLine("""  "id": "$name",""")
            appendLine("""  "queue": "$queue",""")
            appendLine("""  "steps": [""")
            append(stepsJson)
            appendLine()
            appendLine("  ]")
            append("}")
        }
        File(pipelineDir, "pipeline.json").writeText(pipelineJson)

        stepNames.forEach { stepName ->
            val stub = buildString {
                appendLine("@Export")
                appendLine("fun setup() {")
                appendLine("    // TODO: implement $stepName logic")
                appendLine()
                appendLine("""    println("[RESULT] {}")""")
                append("}")
            }
            File(agentsDir, "$stepName.kts").writeText(stub)
        }

        return buildString {
            appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER PIPELINE NEW${ANSI_RESET}")
            appendLine()
            appendLine("  Created  : ${pipelineDir.absolutePath}")
            appendLine("  Pipeline : $name")
            appendLine("  Queue    : $queue")
            appendLine("  Steps    : $stepsCount")
            appendLine()
            appendLine("  ${ANSI_GREEN_155}✓${ANSI_RESET}  pipeline.json")
            stepNames.forEach { appendLine("  ${ANSI_GREEN_155}✓${ANSI_RESET}  agents/$it.kts") }
            appendLine()
            append("  Next: ${ANSI_YELLOW_229}koupper pipeline submit $name/pipeline.json${ANSI_RESET}")
        }
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    private fun submit(pipelineFile: File, jobsDir: File): String {
        val sb = StringBuilder()
        sb.appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER PIPELINE SUBMIT${ANSI_RESET}")

        if (!pipelineFile.exists()) {
            sb.appendLine("  ${ANSI_RED}Pipeline file not found: ${pipelineFile.absolutePath}${ANSI_RESET}")
            return sb.toString()
        }

        val json = runCatching { pipelineFile.readText() }.getOrElse {
            sb.appendLine("  ${ANSI_RED}Cannot read pipeline file: ${it.message}${ANSI_RESET}")
            return sb.toString()
        }

        val pipelineId = extractField(json, "id")
            ?: "pipeline-${System.currentTimeMillis()}"
        val queue = extractField(json, "queue") ?: "default"

        val stepsRaw = extractRawJsonValue(json, "steps")
        if (stepsRaw == null || !stepsRaw.startsWith("[")) {
            sb.appendLine("  ${ANSI_RED}Missing or invalid 'steps' array in pipeline file${ANSI_RESET}")
            return sb.toString()
        }

        val steps = parseStepObjects(stepsRaw)
        if (steps.isEmpty()) {
            sb.appendLine("  ${ANSI_RED}No valid steps found in pipeline${ANSI_RESET}")
            return sb.toString()
        }

        sb.appendLine("  Pipeline : $pipelineId")
        sb.appendLine("  Queue    : $queue")
        sb.appendLine("  Steps    : ${steps.size}")
        sb.appendLine("  Jobs dir : ${jobsDir.absolutePath}\n")

        // Build pipelineNext chain from last step towards step 1 (step 0 is the first job)
        var pipelineNextJson: String? = null
        for (i in steps.indices.reversed()) {
            if (i == 0) break
            val step = steps[i]
            pipelineNextJson = buildStepNextJson(step, pipelineNextJson)
        }

        // Build first job JSON
        val step0     = steps[0]
        val firstJobId = "$pipelineId-step0"
        val jobJson   = buildFirstJobJson(
            jobId         = firstJobId,
            step          = step0,
            pipelineId    = pipelineId,
            pipelineTotal = steps.size,
            pipelineNext  = pipelineNextJson
        )

        val qDir = File(jobsDir, queue).also { it.mkdirs() }
        File(qDir, "$firstJobId.json").writeText(jobJson)

        sb.appendLine("  ${ANSI_GREEN_155}✓${ANSI_RESET}  Enqueued : $firstJobId  [$queue]")
        for (i in 1 until steps.size) {
            sb.appendLine("  ${ANSI_YELLOW_229}⟶${ANSI_RESET}  Pending  : $pipelineId-step$i  [$queue]")
        }
        sb.appendLine("\n  Run ${ANSI_YELLOW_229}koupper pipeline status $pipelineId${ANSI_RESET} to track progress.")

        return sb.toString()
    }

    // ── Status ────────────────────────────────────────────────────────────────

    private fun statusPipeline(pipelineId: String, jobsDir: File): String {
        val sb = StringBuilder()
        sb.appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER PIPELINE STATUS${ANSI_RESET}  $pipelineId\n")

        if (!jobsDir.exists()) {
            sb.appendLine("  ${ANSI_YELLOW_229}No jobs directory found.${ANSI_RESET}")
            return sb.toString()
        }

        val excluded = setOf("logs", "commands")
        val queues = jobsDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
            ?: emptyList()

        data class StepInfo(val step: Int, val queue: String, val status: String, val result: String?)

        val steps = mutableListOf<StepInfo>()
        val prefix = "$pipelineId-step"

        queues.forEach { qDir ->
            File(qDir, ".done").listFiles { f ->
                f.name.startsWith(prefix) && f.name.endsWith(".result.json")
            }?.forEach { f ->
                val n = f.name.removePrefix(prefix).substringBefore(".result.json").toIntOrNull() ?: return@forEach
                val resultRaw = runCatching { f.readText() }.getOrDefault("")
                val result = extractRawJsonValue(resultRaw, "result")?.take(80)
                steps += StepInfo(n, qDir.name, "done", result)
            }

            File(qDir, ".failed").listFiles { f ->
                f.name.startsWith(prefix) && f.name.endsWith(".json")
            }?.forEach { f ->
                val n = f.name.removePrefix(prefix).substringBefore(".json").toIntOrNull() ?: return@forEach
                steps += StepInfo(n, qDir.name, "failed", null)
            }

            File(qDir, ".dead").listFiles { f ->
                f.name.startsWith(prefix) && f.name.endsWith(".json")
            }?.forEach { f ->
                val n = f.name.removePrefix(prefix).substringBefore(".json").toIntOrNull() ?: return@forEach
                steps += StepInfo(n, qDir.name, "dead", null)
            }

            qDir.listFiles { f ->
                f.name.startsWith(prefix) && f.name.endsWith(".json.processing")
            }?.forEach { f ->
                val n = f.name.removePrefix(prefix).substringBefore(".json.processing").toIntOrNull() ?: return@forEach
                steps += StepInfo(n, qDir.name, "running", null)
            }

            qDir.listFiles { f ->
                f.name.startsWith(prefix) && f.name.endsWith(".json") && !f.name.endsWith(".processing")
            }?.forEach { f ->
                val n = f.name.removePrefix(prefix).substringBefore(".json").toIntOrNull() ?: return@forEach
                steps += StepInfo(n, qDir.name, "pending", null)
            }
        }

        if (steps.isEmpty()) {
            sb.appendLine("  No steps found for pipeline: $pipelineId")
            return sb.toString()
        }

        val sorted    = steps.sortedBy { it.step }
        val total     = sorted.maxOf { it.step } + 1
        val doneCount = sorted.count { it.status == "done" }

        sb.appendLine("  Progress : $doneCount / $total steps done\n")

        sorted.forEach { info ->
            val icon = when (info.status) {
                "done"    -> "${ANSI_GREEN_155}✓${ANSI_RESET}"
                "running" -> "${ANSI_YELLOW_229}▶${ANSI_RESET}"
                "failed"  -> "${ANSI_RED}✗${ANSI_RESET}"
                "dead"    -> "${ANSI_RED}☠${ANSI_RESET}"
                else      -> "○"
            }
            val label = when (info.status) {
                "done"    -> "${ANSI_GREEN_155}done${ANSI_RESET}"
                "running" -> "${ANSI_YELLOW_229}running${ANSI_RESET}"
                "failed"  -> "${ANSI_RED}failed${ANSI_RESET}"
                "dead"    -> "${ANSI_RED}dead${ANSI_RESET}"
                else      -> "pending"
            }
            val resultSuffix = if (info.result != null) "  → ${info.result}" else ""
            sb.appendLine("  $icon  step ${info.step}  [${info.queue}]  $label$resultSuffix")
        }

        return sb.toString()
    }

    // ── JSON builders ─────────────────────────────────────────────────────────

    private fun usage(): String = buildString {
        appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER PIPELINE${ANSI_RESET}")
        appendLine()
        appendLine("  Subcommands:")
        appendLine("    new    <name>          [--steps=N] [--queue=<queue>]   Scaffold pipeline directory + agent stubs")
        appendLine("    submit <pipeline.json> [--jobs-dir=path]               Enqueue a multi-step pipeline")
        appendLine("    status <pipelineId>    [--jobs-dir=path]               Show pipeline step progress")
        appendLine()
        appendLine("  Pipeline file format (pipeline.json):")
        appendLine("    {")
        appendLine("""      "id":    "my-pipeline",""")
        appendLine("""      "queue": "default",""")
        appendLine("      \"steps\": [")
        appendLine("""        { "agent": "agents/Step1.kts", "input": {"key": "value"} },""")
        appendLine("""        { "agent": "agents/Step2.kts" },""")
        appendLine("""        { "agent": "agents/Step3.kts" }""")
        appendLine("      ]")
        appendLine("    }")
        appendLine()
        appendLine("  Each step receives the previous step's [RESULT] as its input.")
    }
}

// Builds the pipelineNext JSON object for a given step, optionally embedding a deeper chain.
internal fun buildStepNextJson(step: PipelineStep, nestedNext: String?): String =
    buildString {
        append("""{"scriptPath":"${step.agent}"""")
        if (step.input  != null) append(""","input":${step.input}""")
        if (step.env    != null) append(""","env":${step.env}""")
        if (nestedNext  != null) append(""","pipelineNext":$nestedNext""")
        append("}")
    }

// Builds the full job JSON for the first step in a pipeline.
internal fun buildFirstJobJson(
    jobId: String,
    step: PipelineStep,
    pipelineId: String,
    pipelineTotal: Int,
    pipelineNext: String?
): String = buildString {
    append("""{"id":"$jobId"""")
    append(""","scriptPath":"${step.agent}"""")
    append(""","fileName":"${File(step.agent).nameWithoutExtension}"""")
    append(""","functionName":"run","sourceType":"script"""")
    if (step.input != null) append(""","input":${step.input}""")
    if (step.env   != null) append(""","env":${step.env}""")
    append(""","pipelineId":"$pipelineId","pipelineStep":0,"pipelineTotal":$pipelineTotal""")
    if (pipelineNext != null) append(""","pipelineNext":$pipelineNext""")
    append("}")
}

// Parses a JSON array of step objects. Each element must have "agent" (string);
// "input" and "env" are optional raw JSON values.
internal fun parseStepObjects(arrayJson: String): List<PipelineStep> {
    val steps = mutableListOf<PipelineStep>()
    val s = arrayJson.trim()
    if (!s.startsWith("[")) return steps

    var pos = 1
    while (pos < s.length) {
        while (pos < s.length && s[pos].isWhitespace()) pos++
        if (pos >= s.length || s[pos] == ']') break
        if (s[pos] == ',') { pos++; continue }

        if (s[pos] == '{') {
            val start = pos
            var depth  = 0
            var inStr  = false
            var escaped = false
            while (pos < s.length) {
                val c = s[pos]
                when {
                    escaped            -> escaped = false
                    inStr && c == '\\' -> escaped = true
                    c == '"'           -> inStr = !inStr
                    !inStr && c == '{' -> depth++
                    !inStr && c == '}' -> { depth--; if (depth == 0) break }
                }
                pos++
            }
            val obj   = s.substring(start, pos + 1)
            pos++
            val agent = extractField(obj, "agent") ?: continue
            val input = extractRawJsonValue(obj, "input")?.takeIf { it != "null" }
            val env   = extractRawJsonValue(obj, "env")  ?.takeIf { it != "null" && it.startsWith("{") }
            steps += PipelineStep(agent, input, env)
        } else {
            pos++
        }
    }

    return steps
}

data class PipelineStep(val agent: String, val input: String?, val env: String?)
