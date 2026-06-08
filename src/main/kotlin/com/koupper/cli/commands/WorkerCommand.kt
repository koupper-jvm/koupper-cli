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
//                       [--enable-scheduling] [--status] [--retry [queue]]
//                       [--purge dead|failed [queue]] [--logs [jobId]]
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
//   --retry [queue]    = move all .failed/ jobs back to queue and exit
//   --purge dead|failed [queue] = delete jobs from .dead/ or .failed/ and exit
//   --logs [jobId]     = list recent job logs (no jobId) or print a specific job log
class WorkerCommand : Command() {

    override fun name(): String = "worker"

    private val home       = System.getProperty("user.home")!!
    private val koupperBin = "$home/.koupper/bin/koupper"
    private val excluded   = setOf("logs", "commands")

    override fun execute(vararg args: String): String {
        val jobsDir     = args.drop(1).firstOrNull { !it.startsWith("--") }
            ?.let { File(it) } ?: File("$home/.koupper/jobs")

        if (args.any { it == "--status" }) return statusSnapshot(jobsDir)

        val retryIdx = args.indexOfFirst { it == "--retry" }
        if (retryIdx >= 0) {
            val targetQueue = args.getOrNull(retryIdx + 1)?.takeIf { !it.startsWith("--") }
            return retryFailed(jobsDir, targetQueue)
        }

        val purgeIdx = args.indexOfFirst { it == "--purge" }
        if (purgeIdx >= 0) {
            val bucket      = args.getOrNull(purgeIdx + 1)?.takeIf { !it.startsWith("--") }
            val targetQueue = args.getOrNull(purgeIdx + 2)?.takeIf { !it.startsWith("--") }
            return purgeBucket(jobsDir, bucket, targetQueue)
        }

        val logsIdx = args.indexOfFirst { it == "--logs" }
        if (logsIdx >= 0) {
            val jobId = args.getOrNull(logsIdx + 1)?.takeIf { !it.startsWith("--") }
            return showLogs(jobsDir, jobId)
        }

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
        val inputFragment = if (entry.input != null) ""","input":${entry.input}""" else ""
        File(qDir, "$jobId.json").writeText(
            """{"id":"$jobId","fileName":"${entry.agent}","functionName":"run","scriptPath":"agents/${entry.agent}.kts","sourceType":"script","scheduledBy":"${entry.id}","submittedAt":"${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"$inputFragment}"""
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

        // Mutable so release() captures it; assigned immediately after read.
        // When the read fails (file unreadable), jobJson stays "" → retryCount treated as 0.
        var jobJson = ""

        fun ack()     { processingFile.delete() }
        fun release() {
            if (jobJson.isBlank()) {
                processingFile.renameTo(File(deadDir, originalName))
                println("  ${ANSI_RED}[WORKER]${ANSI_RESET} ☠ $jobId → .dead/ (unreadable job file)")
                return
            }
            val currentRetries = extractRawJsonValue(jobJson, "retryCount")?.toIntOrNull() ?: 0
            val newRetries = currentRetries + 1

            if (newRetries >= maxRetries) {
                processingFile.renameTo(File(deadDir, originalName))
                println("  ${ANSI_RED}[WORKER]${ANSI_RESET} ☠ $jobId → .dead/ ($newRetries/$maxRetries retries)")
            } else {
                val updatedJson = updateRetryCount(jobJson, newRetries)
                File(failedDir, originalName).writeText(updatedJson)
                processingFile.delete()
                println("  ${ANSI_YELLOW_229}[WORKER]${ANSI_RESET} ↩ $jobId → .failed/ (retry $newRetries/$maxRetries)")
            }
        }

        jobJson = runCatching { processingFile.readText() }.getOrElse { e ->
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

        // Per-job env vars — {"KEY": "value"} object in job JSON
        val envOverrides: Map<String, String> = runCatching {
            val envRaw = extractRawJsonValue(jobJson, "env")
                ?.takeIf { it.startsWith("{") } ?: return@runCatching emptyMap()
            // Parse flat {"KEY":"value"} without Jackson dependency
            val result = mutableMapOf<String, String>()
            val kvPattern = Regex(""""([^"]+)"\s*:\s*"([^"\\]*)"""")
            kvPattern.findAll(envRaw).forEach { m -> result[m.groupValues[1]] = m.groupValues[2] }
            result
        }.getOrDefault(emptyMap())

        val logFile = File(jobsDir, "logs/$queue").also { it.mkdirs() }
            .let { File(it, "$jobId.log") }

        println("  ${ANSI_YELLOW_229}[WORKER]${ANSI_RESET} ▶ $jobId  [$queue]  (timeout: ${timeoutSec}s)")
        if (envOverrides.isNotEmpty()) println("  ${ANSI_YELLOW_229}[WORKER]${ANSI_RESET}   env: ${envOverrides.keys.joinToString()}")
        val startMs = System.currentTimeMillis()

        val cmd = buildList {
            add(koupperBin); add("run"); add(scriptFile.absolutePath)
            if (inputJson != null) add(inputJson)
        }

        val proc = runCatching {
            val pb = ProcessBuilder(cmd).redirectErrorStream(true)
            if (envOverrides.isNotEmpty()) pb.environment().putAll(envOverrides)
            pb.start()
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

    // ── Retry: move .failed/ → queue ──────────────────────────────────────────

    private fun retryFailed(jobsDir: File, targetQueue: String?): String {
        val sb = StringBuilder()
        sb.appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER WORKER RETRY${ANSI_RESET}")
        if (targetQueue != null) sb.appendLine("  Queue    : $targetQueue")
        sb.appendLine("  Jobs dir : ${jobsDir.absolutePath}\n")

        if (!jobsDir.exists()) {
            sb.appendLine("  ${ANSI_YELLOW_229}No jobs directory found.${ANSI_RESET}")
            return sb.toString()
        }

        val queues = if (targetQueue != null)
            listOf(File(jobsDir, targetQueue)).filter { it.isDirectory }
        else
            queueDirs(jobsDir, emptyList())

        if (queues.isEmpty()) {
            sb.appendLine("  No queues found.")
            return sb.toString()
        }

        var total = 0
        queues.forEach { qDir ->
            val failedDir = File(qDir, ".failed")
            if (!failedDir.exists()) return@forEach
            val jobs = failedDir.listFiles { f -> f.name.endsWith(".json") } ?: return@forEach
            jobs.sortedBy { it.lastModified() }.forEach { file ->
                val dest = File(qDir, file.name)
                if (file.renameTo(dest)) {
                    total++
                    sb.appendLine("  ${ANSI_GREEN_155}↩${ANSI_RESET}  [${qDir.name}] ${file.name}")
                } else {
                    sb.appendLine("  ${ANSI_RED}✗${ANSI_RESET}  [${qDir.name}] ${file.name}  (could not move)")
                }
            }
        }

        if (total == 0) sb.appendLine("  No failed jobs to retry.")
        else sb.appendLine("\n  ${ANSI_GREEN_155}$total job(s) re-queued.${ANSI_RESET}")

        return sb.toString()
    }

    // ── Purge: delete jobs from .dead/ or .failed/ ────────────────────────────

    private fun purgeBucket(jobsDir: File, bucket: String?, targetQueue: String?): String {
        val sb = StringBuilder()

        if (bucket != "dead" && bucket != "failed") {
            return "\n  Usage: koupper worker --purge dead|failed [queue]\n"
        }

        sb.appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER WORKER PURGE${ANSI_RESET}  (.$bucket)")
        if (targetQueue != null) sb.appendLine("  Queue    : $targetQueue")
        sb.appendLine("  Jobs dir : ${jobsDir.absolutePath}\n")

        if (!jobsDir.exists()) {
            sb.appendLine("  ${ANSI_YELLOW_229}No jobs directory found.${ANSI_RESET}")
            return sb.toString()
        }

        val queues = if (targetQueue != null)
            listOf(File(jobsDir, targetQueue)).filter { it.isDirectory }
        else
            queueDirs(jobsDir, emptyList())

        if (queues.isEmpty()) {
            sb.appendLine("  No queues found.")
            return sb.toString()
        }

        var total = 0
        queues.forEach { qDir ->
            val bucketDir = File(qDir, ".$bucket")
            if (!bucketDir.exists()) return@forEach
            val jobs = bucketDir.listFiles { f -> f.name.endsWith(".json") } ?: return@forEach
            jobs.forEach { file ->
                if (file.delete()) {
                    total++
                    sb.appendLine("  ${ANSI_RED}✗${ANSI_RESET}  [${qDir.name}] ${file.name}  deleted")
                }
            }
        }

        if (total == 0) sb.appendLine("  No $bucket jobs to purge.")
        else sb.appendLine("\n  ${ANSI_GREEN_155}$total job(s) purged from .$bucket.${ANSI_RESET}")

        return sb.toString()
    }

    // ── Logs: list recent or show specific job log ─────────────────────────────

    private fun showLogs(jobsDir: File, jobId: String?): String {
        val sb      = StringBuilder()
        val logsDir = File(jobsDir, "logs")

        if (jobId == null) {
            sb.appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER WORKER LOGS${ANSI_RESET}")
            sb.appendLine("  Logs dir : ${logsDir.absolutePath}\n")

            if (!logsDir.exists()) {
                sb.appendLine("  No logs directory found.")
                return sb.toString()
            }

            val allLogs = logsDir.walkTopDown()
                .filter { it.isFile && it.name.endsWith(".log") }
                .sortedByDescending { it.lastModified() }
                .take(20)
                .toList()

            if (allLogs.isEmpty()) {
                sb.appendLine("  No logs found.")
                return sb.toString()
            }

            sb.appendLine("  Recent jobs (latest ${allLogs.size}):\n")
            val idW = allLogs.maxOf { it.nameWithoutExtension.length }.coerceAtLeast(6) + 2
            sb.append("  ${"JOB ID".padEnd(idW)}${"QUEUE".padEnd(12)}STATUS\n")
            sb.append("  ${"─".repeat(idW)}${"─".repeat(12)}──────────\n")

            allLogs.forEach { f ->
                val id    = f.nameWithoutExtension
                val queue = f.parentFile.name
                val text  = runCatching { f.readText() }.getOrDefault("")
                val status = when {
                    "[DONE]"    in text -> "${ANSI_GREEN_155}done${ANSI_RESET}"
                    "[TIMEOUT]" in text -> "${ANSI_RED}timeout${ANSI_RESET}"
                    "[FAILED]"  in text -> "${ANSI_RED}failed${ANSI_RESET}"
                    else                -> "${ANSI_YELLOW_229}running?${ANSI_RESET}"
                }
                sb.appendLine("  ${id.padEnd(idW)}${queue.padEnd(12)}$status")
            }

            sb.appendLine("\n  Run: koupper worker --logs <jobId>")
            return sb.toString()
        }

        // ── Specific job ──────────────────────────────────────────────────────

        sb.appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER WORKER LOGS${ANSI_RESET}  $jobId\n")

        if (!logsDir.exists()) {
            sb.appendLine("  ${ANSI_RED}No logs directory found.${ANSI_RESET}")
            return sb.toString()
        }

        val matches = logsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { qDir -> File(qDir, "$jobId.log").takeIf { it.exists() } }
            ?: emptyList()

        if (matches.isEmpty()) {
            sb.appendLine("  ${ANSI_RED}No log found for: $jobId${ANSI_RESET}")
            sb.appendLine("  Searched: ${logsDir.absolutePath}")
            return sb.toString()
        }

        matches.forEach { logFile ->
            val queue = logFile.parentFile.name
            sb.appendLine("  ${ANSI_YELLOW_229}[$queue]${ANSI_RESET}  ${logFile.absolutePath}")
            sb.appendLine("  ${"─".repeat(60)}")
            sb.append(runCatching { logFile.readText() }.getOrDefault("(unreadable)"))
            if (!sb.endsWith("\n")) sb.appendLine()
            sb.appendLine("  ${"─".repeat(60)}")
        }

        return sb.toString()
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

}

// Embeds or updates the retryCount field in a job JSON string.
// Appends the field before the closing brace when not present.
internal fun updateRetryCount(json: String, count: Int): String {
    val pattern = Regex(""""retryCount"\s*:\s*\d+""")
    if (pattern.containsMatchIn(json)) return pattern.replace(json, """"retryCount":$count""")
    val trimmed = json.trim()
    return if (trimmed.endsWith("}")) trimmed.dropLast(1) + ""","retryCount":$count}"""
    else json
}

// Extracts a quoted string field value from a flat JSON object.
internal fun extractField(json: String, field: String): String? =
    Regex(""""$field"\s*:\s*"([^"\\]*)"""").find(json)?.groupValues?.get(1)

// Extracts a raw JSON value (object, array, boolean, null, or number) for a field.
// Returns null if the field is absent. Returns the string "null" if the JSON value is null.
// For quoted string fields use extractField instead.
internal fun extractRawJsonValue(json: String, field: String): String? {
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
                    escaped            -> escaped = false
                    inStr && c == '\\' -> escaped = true
                    c == '"'           -> inStr = !inStr
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
            val start = pos
            while (pos < json.length && json[pos] !in ",}] \n\r\t") pos++
            json.substring(start, pos).trim().takeIf { it.isNotEmpty() }
        }
    }
}
