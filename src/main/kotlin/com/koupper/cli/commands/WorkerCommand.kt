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

        // Start scheduling engine if enabled
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
    // Reads ~/.koupper/schedules.json and submits jobs to queues at the right time.

    private fun startScheduler(jobsDir: File, running: AtomicBoolean) {
        val scheduler = Executors.newScheduledThreadPool(1) { r ->
            Thread(r, "koupper-scheduler").also { it.isDaemon = true }
        }

        // Check every minute — matches cron resolution
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
                                // Disable after firing so it doesn't repeat
                                ScheduleStore.setEnabled(entry.id, false)
                            }
                        }
                    }
                    // "rate" schedules are handled by a separate fixed-rate timer below
                }
            }
        }, 0, 60, TimeUnit.SECONDS)

        // Rate-based schedules: start individual timers
        val rateEntries = ScheduleStore.load().filter { it.enabled && it.type == "rate" && (it.rateMs ?: 0) > 0 }
        for (entry in rateEntries) {
            scheduler.scheduleAtFixedRate({
                if (!running.get()) return@scheduleAtFixedRate
                // Re-check enabled in case it was disabled after start
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
            // Dead-letter after maxRetries: count existing .failed entries for this job base
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

        val logFile = File(jobsDir, "logs/$queue").also { it.mkdirs() }
            .let { File(it, "$jobId.log") }

        println("  ${ANSI_YELLOW_229}[WORKER]${ANSI_RESET} ▶ $jobId  [$queue]  (timeout: ${timeoutSec}s)")
        val startMs = System.currentTimeMillis()

        val proc = runCatching {
            ProcessBuilder(koupperBin, "run", scriptFile.absolutePath)
                .redirectErrorStream(true)
                .start()
        }.getOrElse { e ->
            logFile.appendText("[WORKER ERROR] Could not start process: ${e.message}\n")
            release(); return
        }

        // Stream output to log file in a daemon thread
        Thread {
            proc.inputStream.bufferedReader().forEachLine { line ->
                logFile.appendText("$line\n")
            }
        }.also { it.isDaemon = true }.start()

        // Wait with timeout — isolates the worker from hanging agents
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
                // Extract script return value: last non-empty line before [DONE]
                val scriptResult = runCatching {
                    logFile.readLines()
                        .filter { it.isNotBlank() && !it.startsWith("[DEBUG]") && !it.startsWith("[DONE]") }
                        .lastOrNull()
                        ?.replace(Regex("\\[[;\\d]*m"), "")  // strip ANSI
                        ?.take(500)
                }.getOrNull()

                logFile.appendText("[DONE] ${elapsed}ms\n")
                println("  ${ANSI_GREEN_155}[WORKER]${ANSI_RESET} ✓ $jobId  (${elapsed}ms)")

                // Write result file before ack so WebUI watcher can read it
                runCatching {
                    if (!scriptResult.isNullOrBlank()) {
                        val doneDir = File(processingFile.parent, ".done").also { it.mkdirs() }
                        val escaped = scriptResult.replace("\\", "\\\\").replace("\"", "\\\"")
                        File(doneDir, "$jobId.result.json")
                            .writeText("""{"id":"$jobId","result":"$escaped"}""")
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

    private fun extractField(json: String, field: String): String? =
        Regex(""""$field"\s*:\s*"([^"\\]*)"""").find(json)?.groupValues?.get(1)
}
