package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File

// Starts the full CORTEX stack with a single command.
//
// Usage: koupper start [jobsDir] [--no-worker] [--no-webui] [--scheduling]
//
// What it does:
//   1. Starts koupper worker in the background (with optional --scheduling)
//   2. Starts CortexWebUiAgent.kts in the background (if installed)
//   3. Launches koupper monitor in the foreground (blocks until exit)
//   4. On monitor exit — kills all background processes started by this command
//
// Env vars forwarded to all child processes:
//   KOUPPER_LLM_MODEL_PATH, KOUPPER_LLM_EXECUTABLE, KOUPPER_WORKER_TIMEOUT
class StartCommand : Command() {

    override fun name(): String = "start"

    private val home       = System.getProperty("user.home")!!
    private val koupperBin = "$home/.koupper/bin/koupper"
    private val agentsDir  = File(home, ".koupper/agents")

    override fun execute(vararg args: String): String {
        val jobsDir     = args.drop(1).firstOrNull { !it.startsWith("--") }
            ?.let { File(it) } ?: File("$home/.koupper/jobs")
        val noWorker    = args.any { it == "--no-worker" }
        val noWebUi     = args.any { it == "--no-webui" }
        val scheduling  = args.any { it == "--scheduling" }

        jobsDir.mkdirs()

        println("\n${ANSI_GREEN_155}  ◈ IGLY CORTEX — Starting${ANSI_RESET}")
        println("  Jobs dir : ${jobsDir.absolutePath}\n")

        // Kill stale processes to free ports and force octopus to restart
        // with the current environment (picks up KOUPPER_LLM_MODEL_PATH etc.)
        listOf("koupper-monitor.jar", "CortexWebUiAgent", "octopus.jar").forEach { pattern ->
            runCatching {
                ProcessBuilder("pkill", "-f", pattern).start().waitFor()
            }
        }
        Thread.sleep(1500)  // let OS reclaim ports and octopus exit fully

        if (!File(koupperBin).exists()) {
            return "\n  ERROR: koupper binary not found at $koupperBin\n"
        }

        // Boot octopus directly so it inherits the full env (KOUPPER_LLM_PROVIDER etc.)
        // NOT added to children — octopus must survive past monitor exit for clean shutdown.
        val octopusJar = File("$home/.koupper/libs/octopus.jar")
        if (octopusJar.exists()) {
            val java = ProcessHandle.current().info().command().orElse("java")
            ProcessBuilder(java, "-jar", octopusJar.absolutePath)
                .also { pb -> forwardEnv(pb) }
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            Thread.sleep(2500) // give octopus time to bind :9998
        }

        val children = mutableListOf<Process>()

        Runtime.getRuntime().addShutdownHook(Thread {
            if (children.isNotEmpty()) {
                println("\n  Stopping background processes…")
                children.forEach { runCatching { it.destroyForcibly() } }
            }
        })

        // ── 1. Worker ─────────────────────────────────────────────────────────
        if (!noWorker) {
            val workerArgs = mutableListOf(koupperBin, "worker", jobsDir.absolutePath)
            if (scheduling) workerArgs.add("--enable-scheduling")

            val workerLog = File(home, ".koupper/logs/worker.log").also { it.parentFile.mkdirs() }
            val worker = ProcessBuilder(workerArgs)
                .also { pb -> forwardEnv(pb) }
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(workerLog))
                .start()
            children.add(worker)
            println("  ${ANSI_GREEN_155}[worker]${ANSI_RESET}   started (log: ~/.koupper/logs/worker.log)")
            if (scheduling) println("  ${ANSI_YELLOW_229}[scheduler]${ANSI_RESET} reading ~/.koupper/schedules.json")
        }

        // ── 2. Web UI agent ───────────────────────────────────────────────────
        val webAgent = File(agentsDir, "CortexWebUiAgent.kts")
        if (!noWebUi && webAgent.exists()) {
            val webLog = File(home, ".koupper/logs/webui.log").also { it.parentFile.mkdirs() }
            val webUi  = ProcessBuilder(koupperBin, "run", webAgent.absolutePath)
                .also { pb ->
                    forwardEnv(pb)
                    pb.environment()["CORTEX_JOBS_DIR"] = jobsDir.absolutePath
                }
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(webLog))
                .start()
            children.add(webUi)
            println("  ${ANSI_GREEN_155}[web ui]${ANSI_RESET}   http://localhost:18083  (log: ~/.koupper/logs/webui.log)")
        }

        // ── 2b. CORTEX agent (long-running, outside worker to avoid timeout) ──
        val cortexAgent = File(agentsDir, "CortexAgent.kts")
        if (cortexAgent.exists()) {
            val cortexLog = File(home, ".koupper/jobs/logs/cortex/cortex-session.log")
                .also { it.parentFile.mkdirs() }
            cortexLog.writeText("")
            val cortex = ProcessBuilder(koupperBin, "run", cortexAgent.absolutePath)
                .also { pb ->
                    forwardEnv(pb)
                    pb.environment()["CORTEX_JOBS_DIR"] = jobsDir.absolutePath
                }
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(cortexLog))
                .start()
            children.add(cortex)
            println("  ${ANSI_GREEN_155}[cortex]${ANSI_RESET}   started (log: ~/.koupper/jobs/logs/cortex/cortex-session.log)")
        }

        println()
        println("  ${ANSI_YELLOW_229}MCP tools${ANSI_RESET} : http://localhost:18082/mcp/tools")
        println()

        // ── 3. Monitor — foreground, blocks until user exits ──────────────────
        val monitorJar = File(home, ".koupper/libs/koupper-monitor.jar")
        if (!monitorJar.exists()) {
            children.forEach { runCatching { it.destroyForcibly() } }
            return "\n  ERROR: koupper-monitor.jar not found at ${monitorJar.absolutePath}\n"
        }

        val java    = ProcessHandle.current().info().command().orElse("java")
        val monitor = ProcessBuilder(java, "-jar", monitorJar.absolutePath, jobsDir.absolutePath)
            .also { pb -> forwardEnv(pb) }
            .inheritIO()
            .start()
        children.add(monitor)

        monitor.waitFor()
        return ""
    }

    private fun forwardEnv(pb: ProcessBuilder) {
        listOf(
            "KOUPPER_LLM_MODEL_PATH",
            "KOUPPER_LLM_EXECUTABLE",
            "KOUPPER_WORKER_TIMEOUT",
            "KOUPPER_LLM_PROVIDER",
            "KOUPPER_LLM_API_BASE",
            "KOUPPER_LLM_API_KEY",
            "KOUPPER_LLM_MODEL",
            "KOUPPER_LLM_MAX_TOKENS",
            "KOUPPER_LLM_TEMPERATURE"
        ).forEach { key ->
            System.getenv(key)?.let { pb.environment()[key] = it }
        }
    }
}
