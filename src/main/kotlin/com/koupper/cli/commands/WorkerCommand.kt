package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// Daemon that continuously polls job queues under jobsDir, claims jobs atomically,
// executes agent scripts via `koupper run`, and writes output to the job log file.
//
// Usage: koupper worker [jobsDir] [--queues=q1,q2] [--concurrency=N]
//                       [--interval=ms] [--timeout=seconds] [--max-retries=N]
//                       [--enable-scheduling] [--status]
//
// Defaults:
//   jobsDir            = ~/.koupper/jobs
//   queues             = all subdirectories (excluding logs, commands)
//   concurrency        = 2
//   interval           = 2000ms
//   timeout            = 300s (5 min) — env KOUPPER_WORKER_TIMEOUT overrides default
//   max-retries        = 3 — moves to .dead/ after N failures on the same job
//   enable-scheduling  = false (reads ~/.koupper/schedules.json when set)
//   --status           = print queue snapshot and exit without starting daemon
class WorkerCommand : Command() {

    override fun name(): String = "worker"

    private val home       = System.getProperty("user.home")!!
    private val koupperBin = "$home/.koupper/bin/koupper"
    private val excluded   = setOf("logs", "commands")

    override fun execute(vararg args: String): String {
        val jobsDir     = args.drop(1).firstOrNull { !it.startsWith("--") }
            ?.let { File(it) } ?: File("$home/.koupper/jobs")

        if (args.any { it == "--status" }) return statusSnapshot(jobsDir)

        val queues      = args.firstOrNull { it.startsWith("--queues=") }
            ?.removePrefix("--queues=")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val concurrency = args.firstOrNull { it.startsWith("--concurrency=") }
            ?.removePrefix("--concurrency=")?.toIntOrNull() ?: 2
        val intervalMs  = args.firstOrNull { it.startsWith("--interval=") }
            ?.removePrefix("--interval=")?.toLongOrNull() ?: 2000L
        val timeoutSec       = args.firstOrNull { it.startsWith("--timeout=") }
            ?.removePrefix("--timeout=")?.toLongOrNull()
            ?: System.getenv("KOUPPER_WORKER_TIMEOUT")?.toLongOrNull()
            ?: 300L
        val maxRetries       = args.firstOrNull { it.startsWith("--max-retries=") }
            ?.removePrefix("--max-retries=")?.toIntOrNull() ?: 3
        val enableScheduling = args.any { it == "--enable-scheduling" }

        jobsDir.mkdirs()

        println("\n${ANSI_GREEN_155}  ◈ KOUPPER WORKER${ANSI_RESET}")
        println("  Jobs dir    : ${jobsDir.absolutePath}")
        println("  Queues      : ${if (queues.isEmpty()) "all" else queues.joinToString(", ")}")
        println("  Concurrency : $concurrency")
        println("  Poll        : ${intervalMs}ms")
        println("  Timeout     : ${timeoutSec}s per job")
        println("  Max retries : $maxRetries before dead-letter")
        println("  Scheduling  : ${if (enableScheduling) "${ANSI_GREEN_155}enabled${ANSI_RESET}" else "disabled (--enable-scheduling to activate)"}")
        println("  Press Ctrl+C to stop\n")

        val running = AtomicBoolean(true)
        val active  = AtomicInteger(0)

        Runtime.getRuntime().addShutdownHook(Thread {
            println("\n  Worker shutting down…")
            running.set(false)
        })

        if (enableScheduling) startScheduler(jobsDir, running)

        while (running.get()) {
            if (active.get() < concurrency) {
                for (qDir in queueDirs(jobsDir, queues)) {
                    if (active.get() >= concurrency) break
                    claimAndRun(qDir, jobsDir, active, concurrency, timeoutSec, maxRetries)
                }
            }
            Thread.sleep(intervalMs)
        }

        return ""
    }

    // ── Scheduler engine ──────────────────────────────────────────────────────

    private fun startScheduler(jobsDir: File, running: AtomicBoolean) {
        val scheduler = Executors.newScheduledThreadPool(1) { r ->
            Thread(r, "koupper-scheduler").also { it.isDaemon = true }
        }

        scheduler.scheduleAtFixedRate({
            if (!running.get()) { scheduler.shutdown(); return@scheduleAtFixedRate }
            val now     = LocalDateTime.now()
            val entries = ScheduleStore.load().filter { it.enabled }

            for (entry in entries) {
                when (entry.type) {
                    "cron" -> if (entry.cron != null && CronMatcher.matches(entry.cron, now)) {
                        submitScheduledJob(entry, jobsDir)
                    }
                    "once" -> if (entry.runAt != null) {
                        runCatching {
                            val runAt = LocalDateTime.parse(entry.runAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            if (now.year  == runAt.year  && now.month  == runAt.month  &&
                                now.dayOfMonth == runAt.dayOfMonth && now.hour == runAt.hour &&
                                now.minute == runAt.minute) {
                                submitScheduledJob(entry, jobsDir)
                                ScheduleStore.setEnabled(entry.id, false)
                            }
                        }
                    }
                }
            }
        }, 0, 60, TimeUnit.SECONDS)

        val rateEntries = ScheduleStore.load().filter { it.enabled && it.type == "rate" && (it.rateMs ?: 0) > 0 }
        for (entry in rateEntries) {
            scheduler.scheduleAtFixedRate({
                if (!running.get()) return@scheduleAtFixedRate
                if (ScheduleStore.load().any { it.id == entry.id && it.enabled }) {
                    submitScheduledJob(entry, jobsDir)
                }
            }, entry.rateMs!!, entry.rateMs, TimeUnit.MILLISECONDS)
            println("  ${ANSI_GREEN_155}[SCHEDULER]${ANSI_RESET} ${entry.id} every ${entry.rateMs / 1000}s")
        }

        val cronEntries = ScheduleStore.load().filter { it.enabled && it.type == "cron" }
        val onceEntries = ScheduleStore.load().filter { it.enabled && it.type == "once" }
        println("  ${ANSI_GREEN_155}[SCHEDULER]${ANSI_RESET} loaded: ${cronEntries.size} cron, ${rateEntries.size} rate, ${onceEntries.size} once")
    }

    private fun submitScheduledJob(entry: ScheduleEntry, jobsDir: File) {
        val jobId = "${entry.agent}-sched-${System.currentTimeMillis()}"
        val qDir  = File(jobsDir, entry.queue).also { it.mkdirs() }
        File(qDir, "$jobId.json").writeText(
            """{"id":"$jobId","fileName":"${entry.agent}","functionName":"run","scriptPath":"agents/${entry.agent}.kts","sourceType":"script","scheduledBy":"${entry.id}","submittedAt":"${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"}"""
        )
        println("  ${ANSI_YELLOW_229}[SCHEDULER]${ANSI_RESET} ⏰ ${entry.id} → $jobId [${entry.queue}]")
    }

    // ── Claim loop ────────────────────────────────────────────────────────────

    private fun claimAndRun(
        qDir: File, jobsDir: File,
        active: AtomicInteger, concurrency: Int,
        timeoutSec: Long, maxRetries: Int
    ) {
        val pending   = qDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() } ?: return
        val failedDir = File(qDir, ".failed").also { it.mkdirs() }
        val deadDir   = File(qDir, ".dead").also { it.mkdirs() }

        for (file in pending) {
            if (active.get() >= concurrency) break

            val processingFile = File(file.parent, "${file.name}.processing")
            if (!file.renameTo(processingFile)) continue

            active.incrementAndGet()
            Thread {
                try {
                    runJob(
                        processingFile = processingFile,
                        originalName   = file.name,
                        queue          = qDir.name,
                        jobsDir        = jobsDir,
                        failedDir      = failedDir,
                        deadDir        = deadDir,
                        timeoutSec     = timeoutSec,
                        maxRetries     = maxRetries
                    )
                } finally {
                    active.decrementAndGet()
                }
            }.also { it.isDaemon = true }.start()
        }
    }

    // ── Job execution with isolation ──────────────────────────────────────────

    private fun runJob(
        processingFile: File,
        originalName: String,
        queue: String,
        jobsDir: File,
        failedDir: File,
        deadDir: File,
        timeoutSec: Long,
        maxRetries: Int
    ) {
        val jobId = processingFile.name.removeSuffix(".json.processing")

        fun ack()     { processingFile.delete() }
        fun release() {
            val baseName  = originalName.removeSuffix(".json")
            val failCount = failedDir.listFiles { f ->
                f.name.startsWith(baseName) && f.name.endsWith(".json")
            }?.size ?: 0

            if (failCount >= maxRetries) {
                processingFile.renameTo(File(deadDir, originalName))
                println("  ${ANSI_RED}[WORKER]${ANSI_RESET} ☠ $jobId → .dead/ (exceeded $maxRetries retries)")
            } else {
                processingFile.renameTo(File(failedDir, originalName))
            }
        }

        val jobJson = runCatching { processingFile.readText() }.getOrElse { e ->
            println("  ${ANSI_RED}[WORKER] ERROR reading $jobId: ${e.message}${ANSI_RESET}")
            release(); return
        }

        val scriptPath = extractField(jobJson, "scriptPath") ?: run {
            println("  ${ANSI_RED}[WORKER] ERROR: no scriptPath in $jobId${ANSI_RESET}")
            release(); return
        }

        val scriptFile = resolveScript(scriptPath) ?: run {
            println("  ${ANSI_RED}[WORKER] ERROR: script not found: $scriptPath${ANSI_RESET}")
            release(); return
        }

        // Pipeline input — JSON object/array from previous step, or null
        val inputJson = extractRawJsonValue(jobJson, "input")
            ?.takeIf { it.isNotBlank() && it != "null" }

        val logFile = File(jobsDir, "logs/$queue").also { it.mkdirs() }
            .let { File(it, "$jobId.log") }

        println("  ${ANSI_YELLOW_229}[WORKER]${ANSI_RESET} ▶ $jobId  [$queue]  (timeout: ${timeoutSec}s)")
        val startMs = System.currentTimeMillis()

        val cmd = buildList {
            add(koupperBin); add("run"); add(scriptFile.absolutePath)
            if (inputJson != null) add(inputJson)
        }

        val proc = runCatching {
            ProcessBuilder(cmd).redirectErrorStream(true).start()
        }.getOrElse { e ->
            logFile.appendText("[WORKER ERROR] Could not start process: ${e.message}\n")
            release(); return
        }

        Thread {
            proc.inputStream.bufferedReader().forEachLine { line ->
                logFile.appendText("$line\n")
            }
        }.also { it.isDaemon = true }.start()

        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        val elapsed  = System.currentTimeMillis() - startMs

        when {
            !finished -> {
                proc.destroyForcibly()
                logFile.appendText("[TIMEOUT] Job exceeded ${timeoutSec}s — process killed\n")
                println("  ${ANSI_RED}[WORKER]${ANSI_RESET} ⏱ $jobId timed out (${timeoutSec}s) — killed")
                release()
            }
            proc.exitValue() == 0 && !logContainsScriptError(logFile) -> {
                // Prefer [RESULT] <json> sentinel (typed pipeline agents); fall back to last non-blank line
                val lines = runCatching { logFile.readLines() }.getOrDefault(emptyList())
                val sentinelJson = lines
                    .firstOrNull { it.trimStart().startsWith("[RESULT] ") }
                    ?.substringAfter("[RESULT] ")?.trim()

                val scriptResult = sentinelJson ?: lines
                    .filter { it.isNotBlank() && !it.startsWith("[DEBUG]") && !it.startsWith("[DONE]") && !it.startsWith("[RESULT]") }
                    .lastOrNull()
                    ?.replace(Regex("\\[[;\\d]*m"), "")
                    ?.take(500)

                logFile.appendText("[DONE] ${elapsed}ms\n")
                println("  ${ANSI_GREEN_155}[WORKER]${ANSI_RESET} ✓ $jobId  (${elapsed}ms)")

                // Write result file — raw JSON when sentinel present, escaped string otherwise
                runCatching {
                    if (!scriptResult.isNullOrBlank()) {
                        val doneDir = File(processingFile.parent, ".done").also { it.mkdirs() }
                        val resultJson = if (sentinelJson != null) {
                            """{"id":"$jobId","result":$sentinelJson}"""
                        } else {
                            val escaped = scriptResult.replace("\\", "\\\\").replace("\"", "\\\"")
                            """{"id":"$jobId","result":"$escaped"}"""
                        }
                        File(doneDir, "$jobId.result.json").writeText(resultJson)
                    }
                }

                // Pipeline dispatch: enqueue next step with this result as input
                if (sentinelJson != null) {
                    val pipelineNextStr = extractRawJsonValue(jobJson, "pipelineNext")
                        ?.takeIf { it.isNotBlank() && it != "null" }
                    if (pipelineNextStr != null) {
                        val pipelineId    = extractField(jobJson, "pipelineId") ?: jobId
                        val pipelineStep  = extractRawJsonValue(jobJson, "pipelineStep")?.toIntOrNull() ?: 0
                        val pipelineTotal = extractRawJsonValue(jobJson, "pipelineTotal")?.toIntOrNull() ?: 0
                        val qDir = processingFile.parentFile
                        dispatchPipelineNext(pipelineNextStr, sentinelJson, pipelineId, pipelineStep + 1, pipelineTotal, qDir)
                    }
                }

                ack()
            }
            else -> {
                val exit = proc.exitValue()
                val reason = if (logContainsScriptError(logFile)) "script error" else "exit=$exit"
                logFile.appendText("[FAILED] $reason  ${elapsed}ms\n")
                println("  ${ANSI_RED}[WORKER]${ANSI_RESET} ✗ $jobId  $reason  (${elapsed}ms)")
                release()
            }
        }
    }

    private fun dispatchPipelineNext(
        pipelineNextJson: String,
        input: String,
        pipelineId: String,
        nextStep: Int,
        pipelineTotal: Int,
        qDir: File
    ) {
        val nextScript = extractField(pipelineNextJson, "scriptPath") ?: run {
            println("  ${ANSI_RED}[WORKER]${ANSI_RESET} ⚠ pipeline $pipelineId step $nextStep: missing scriptPath")
            return
        }
        val nestedNext = extractRawJsonValue(pipelineNextJson, "pipelineNext")
            ?.takeIf { it.isNotBlank() && it != "null" }
        val nextJobId = "$pipelineId-step$nextStep"

        val sb = StringBuilder()
        sb.append("""{"id":"$nextJobId","scriptPath":"$nextScript","input":$input""")
        sb.append(""","pipelineId":"$pipelineId","pipelineStep":$nextStep""")
        if (pipelineTotal > 0) sb.append(""","pipelineTotal":$pipelineTotal""")
        if (nestedNext != null) sb.append(""","pipelineNext":$nestedNext""")
        sb.append("}")

        File(qDir, "$nextJobId.json").writeText(sb.toString())
        println("  ${ANSI_GREEN_155}[WORKER]${ANSI_RESET} ⟶ pipeline [$pipelineId] step $nextStep → $nextScript")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun queueDirs(jobsDir: File, queues: List<String>): List<File> =
        if (queues.isEmpty())
            jobsDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
                ?: emptyList()
        else
            queues.map { File(jobsDir, it) }.filter { it.isDirectory }

    private fun resolveScript(scriptPath: String): File? {
        File(scriptPath).takeIf { it.isAbsolute && it.exists() }?.let { return it }
        File("$home/.koupper/$scriptPath").takeIf { it.exists() }?.let { return it }
        File("$home/.koupper/agents", File(scriptPath).name).takeIf { it.exists() }?.let { return it }
        return null
    }

    private fun logContainsScriptError(logFile: File): Boolean {
        if (!logFile.exists()) return false
        return logFile.useLines { lines ->
            lines.any { line ->
                "No function annotated with @Export was found" in line ||
                "Script error:" in line ||
                "<ERROR::>" in line ||
                "error: unresolved reference" in line ||
                "Exception in thread \"main\"" in line
            }
        }
    }

    private fun statusSnapshot(jobsDir: File): String {
        val sb = StringBuilder()
        sb.appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER WORKER STATUS${ANSI_RESET}")
        sb.appendLine("  Jobs dir : ${jobsDir.absolutePath}\n")

        if (!jobsDir.exists()) {
            sb.appendLine("  ${ANSI_YELLOW_229}No jobs directory found.${ANSI_RESET}")
            return sb.toString()
        }

        val queues = jobsDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
            ?.sortedBy { it.name }
            ?: emptyList()

        if (queues.isEmpty()) {
            sb.appendLine("  No queues found.")
            return sb.toString()
        }

        var totalPending = 0; var totalProcessing = 0; var totalFailed = 0; var totalDead = 0

        queues.forEach { qDir ->
            val pending    = qDir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
            val processing = qDir.listFiles { f -> f.name.endsWith(".json.processing") }?.size ?: 0
            val failed     = File(qDir, ".failed").listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
            val dead       = File(qDir, ".dead").listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

            totalPending += pending; totalProcessing += processing
            totalFailed  += failed;  totalDead       += dead

            val indicator = when {
                dead > 0        -> "${ANSI_RED}☠${ANSI_RESET}"
                failed > 0      -> "${ANSI_YELLOW_229}⚠${ANSI_RESET}"
                processing > 0  -> "${ANSI_GREEN_155}▶${ANSI_RESET}"
                else            -> "○"
            }
            sb.append("  $indicator  ${qDir.name.padEnd(14)}")
            sb.append("  ${ANSI_YELLOW_229}${pending}p${ANSI_RESET}")
            sb.append("  ${ANSI_GREEN_155}${processing}▶${ANSI_RESET}")
            sb.append("  ${ANSI_RED}${failed}f${ANSI_RESET}")
            sb.append("  ${ANSI_RED}${dead}☠${ANSI_RESET}")
            sb.appendLine()
        }

        sb.appendLine()
        sb.append("  Total  ")
        sb.append("  ${ANSI_YELLOW_229}${totalPending}p${ANSI_RESET}")
        sb.append("  ${ANSI_GREEN_155}${totalProcessing}▶${ANSI_RESET}")
        sb.append("  ${ANSI_RED}${totalFailed}f${ANSI_RESET}")
        sb.append("  ${ANSI_RED}${totalDead}☠${ANSI_RESET}")
        sb.appendLine("\n")

        return sb.toString()
    }

    // Extracts a string field value from flat JSON (existing helper)
    private fun extractField(json: String, field: String): String? =
        Regex(""""$field"\s*:\s*"([^"\\]*)"""").find(json)?.groupValues?.get(1)

    // Extracts a raw JSON value (object, array, null, or number) for a given field.
    // Returns null if the field is absent. Returns "null" if the field value is JSON null.
    // For string fields use extractField instead.
    private fun extractRawJsonValue(json: String, field: String): String? {
        val keyMatch = Regex(""""$field"\s*:\s*""").find(json) ?: return null
        var pos = keyMatch.range.last + 1
        while (pos < json.length && json[pos].isWhitespace()) pos++
        if (pos >= json.length) return null
        return when {
            json.startsWith("null",  pos) -> "null"
            json.startsWith("true",  pos) -> "true"
            json.startsWith("false", pos) -> "false"
            json[pos] == '{' || json[pos] == '[' -> {
                val open  = json[pos]
                val close = if (open == '{') '}' else ']'
                var depth = 0
                val start = pos
                var inStr = false
                var escaped = false
                while (pos < json.length) {
                    val c = json[pos]
                    when {
                        escaped        -> escaped = false
                        inStr && c == '\\' -> escaped = true
                        c == '"'       -> inStr = !inStr
                        !inStr && c == open  -> depth++
                        !inStr && c == close -> {
                            depth--
                            if (depth == 0) return json.substring(start, pos + 1)
                        }
                    }
                    pos++
                }
                null
            }
            else -> {
                // number or other scalar
                val start = pos
                while (pos < json.length && json[pos] !in ",}] \n\r\t") pos++
                json.substring(start, pos).trim().takeIf { it.isNotEmpty() }
            }
        }
    }
}
