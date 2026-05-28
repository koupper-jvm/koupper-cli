package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// Daemon that continuously polls job queues under jobsDir, claims jobs atomically,
// executes agent scripts via `koupper run`, and writes output to the job log file.
//
// Usage: koupper worker [jobsDir] [--queues=q1,q2] [--concurrency=N] [--interval=ms]
//
// Defaults:
//   jobsDir     = ~/.koupper/jobs
//   queues      = all subdirectories (excluding logs, commands)
//   concurrency = 2
//   interval    = 2000ms
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

        jobsDir.mkdirs()

        println("\n${ANSI_GREEN_155}  ◈ KOUPPER WORKER${ANSI_RESET}")
        println("  Jobs dir    : ${jobsDir.absolutePath}")
        println("  Queues      : ${if (queues.isEmpty()) "all" else queues.joinToString(", ")}")
        println("  Concurrency : $concurrency")
        println("  Poll        : ${intervalMs}ms")
        println("  Press Ctrl+C to stop\n")

        val running = AtomicBoolean(true)
        val active  = AtomicInteger(0)

        Runtime.getRuntime().addShutdownHook(Thread {
            println("\n  Worker shutting down…")
            running.set(false)
        })

        while (running.get()) {
            if (active.get() < concurrency) {
                for (qDir in queueDirs(jobsDir, queues)) {
                    if (active.get() >= concurrency) break
                    claimAndRun(qDir, jobsDir, active, concurrency)
                }
            }
            Thread.sleep(intervalMs)
        }

        return ""
    }

    // ── Claim loop ────────────────────────────────────────────────────────────

    private fun claimAndRun(qDir: File, jobsDir: File, active: AtomicInteger, concurrency: Int) {
        val pending = qDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() } ?: return
        val failedDir = File(qDir, ".failed").also { it.mkdirs() }

        for (file in pending) {
            if (active.get() >= concurrency) break

            val processingFile = File(file.parent, "${file.name}.processing")

            // Atomic claim — renameTo returns false if another worker got there first
            if (!file.renameTo(processingFile)) continue

            active.incrementAndGet()
            Thread {
                try {
                    runJob(
                        processingFile = processingFile,
                        originalName   = file.name,
                        queue          = qDir.name,
                        jobsDir        = jobsDir,
                        failedDir      = failedDir
                    )
                } finally {
                    active.decrementAndGet()
                }
            }.also { it.isDaemon = true }.start()
        }
    }

    // ── Job execution ─────────────────────────────────────────────────────────

    private fun runJob(
        processingFile: File,
        originalName: String,
        queue: String,
        jobsDir: File,
        failedDir: File
    ) {
        val jobId = processingFile.name.removeSuffix(".json.processing")

        fun ack()     { processingFile.delete() }
        fun release() { processingFile.renameTo(File(failedDir, originalName)) }

        val jobJson = runCatching { processingFile.readText() }.getOrElse { e ->
            println("  ${ANSI_RED}[WORKER] ERROR reading $jobId: ${e.message}${ANSI_RESET}")
            release(); return
        }

        val scriptPath = extractField(jobJson, "scriptPath") ?: run {
            println("  ${ANSI_RED}[WORKER] ERROR: no scriptPath in job $jobId${ANSI_RESET}")
            release(); return
        }

        val scriptFile = resolveScript(scriptPath) ?: run {
            println("  ${ANSI_RED}[WORKER] ERROR: script not found: $scriptPath${ANSI_RESET}")
            release(); return
        }

        val logFile = File(jobsDir, "logs/$queue").also { it.mkdirs() }
            .let { File(it, "$jobId.log") }

        println("  ${ANSI_YELLOW_229}[WORKER]${ANSI_RESET} ▶ $jobId  [$queue]")
        val startMs = System.currentTimeMillis()

        val exitCode = runCatching {
            val proc = ProcessBuilder(koupperBin, "run", scriptFile.absolutePath)
                .redirectErrorStream(true)
                .start()

            // Stream output to log file so the monitor can tail it live
            Thread {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    logFile.appendText("$line\n")
                }
            }.also { it.isDaemon = true }.start()

            proc.waitFor()
        }.getOrElse { e ->
            logFile.appendText("[WORKER ERROR] ${e.message}\n")
            1
        }

        val elapsed = System.currentTimeMillis() - startMs

        if (exitCode == 0) {
            println("  ${ANSI_GREEN_155}[WORKER]${ANSI_RESET} ✓ $jobId  (${elapsed}ms)")
            logFile.appendText("[DONE] ${elapsed}ms\n")
            ack()
        } else {
            println("  ${ANSI_RED}[WORKER]${ANSI_RESET} ✗ $jobId  exit=$exitCode  (${elapsed}ms)")
            logFile.appendText("[FAILED] exit=$exitCode  ${elapsed}ms\n")
            release()
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

    // Resolves scriptPath to an actual File using three strategies:
    //   1. Absolute path
    //   2. Relative to ~/.koupper/  (e.g. "agents/DataAgent.kts")
    //   3. Bare filename in ~/.koupper/agents/
    private fun resolveScript(scriptPath: String): File? {
        File(scriptPath).takeIf { it.isAbsolute && it.exists() }?.let { return it }
        File("$home/.koupper/$scriptPath").takeIf { it.exists() }?.let { return it }
        File("$home/.koupper/agents", File(scriptPath).name).takeIf { it.exists() }?.let { return it }
        return null
    }

    // Lightweight JSON string field extractor — no Jackson dependency in the worker loop.
    private fun extractField(json: String, field: String): String? =
        Regex(""""$field"\s*:\s*"([^"\\]*)"""").find(json)?.groupValues?.get(1)
}
