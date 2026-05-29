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
//                       [--enable-scheduling]
//
// Defaults:
//   jobsDir            = ~/.koupper/jobs
//   queues             = all subdirectories (excluding logs, commands)
//   concurrency        = 2
//   interval           = 2000ms
//   timeout            = 300s (5 min) — env KOUPPER_WORKER_TIMEOUT overrides default
//   max-retries        = 3 — moves to .dead/ after N failures on the same job
//   enable-scheduling  = false (reads ~/.koupper/schedules.json when set)
class WorkerCommand : Command() {

    override fun name(): String = "worker"

    private val home       = System.getProperty("user.home")!!
    private val koupperBin = "$home/.koupper/bin/koupper"
    private val excluded   = setOf("logs", "commands")

    override fun execute(vararg args: String): String {
        val jobsDir     = args.drop(1).firstOrNull { !it.startsWith("--") }
            ?.let { File(it) } ?: File("$home/.koupper/jobs")
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
            proc.exitValue() == 0 -> {
                logFile.appendText("[DONE] ${elapsed}ms\n")
                println("  ${ANSI_GREEN_155}[WORKER]${ANSI_RESET} ✓ $jobId  (${elapsed}ms)")
                ack()
            }
            else -> {
                val exit = proc.exitValue()
                logFile.appendText("[FAILED] exit=$exit  ${elapsed}ms\n")
                println("  ${ANSI_RED}[WORKER]${ANSI_RESET} ✗ $jobId  exit=$exit  (${elapsed}ms)")
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

    private fun extractField(json: String, field: String): String? =
        Regex(""""$field"\s*:\s*"([^"\\]*)"""").find(json)?.groupValues?.get(1)
}
