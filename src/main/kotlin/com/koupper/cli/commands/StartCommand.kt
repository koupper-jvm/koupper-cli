package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File

// Starts the Koupper runtime.
//
// koupper start                      → worker + TUI monitor (terminal mode)
// koupper start --web                → worker + web API at :18083 (no TUI)
// koupper start --web --dashboard    → web API + React dashboard dev server
// koupper start --scheduling         → enables scheduled agents
//
// Dashboard path resolution (first match wins):
//   1. KOUPPER_DASHBOARD_PATH env var
//   2. ~/koupper-dashboard
//   3. ~/.koupper/dashboard
class StartCommand : Command() {

    override fun name(): String = "start"

    private val home       = System.getProperty("user.home")!!
    private val koupperBin = "$home/.koupper/bin/koupper"

    override fun execute(vararg args: String): String {
        val jobsDir    = args.drop(1).firstOrNull { !it.startsWith("--") }
            ?.let { File(it) } ?: File("$home/.koupper/jobs")
        val noWorker   = args.any { it == "--no-worker" }
        val webMode    = args.any { it == "--web" }
        val scheduling = args.any { it == "--scheduling" }
        val dashboard  = args.any { it == "--dashboard" }

        jobsDir.mkdirs()

        // Kill stale processes
        listOf("koupper-monitor.jar", "CortexWebUiAgent", "CortexAgent", "octopus.jar").forEach { pattern ->
            runCatching { ProcessBuilder("pkill", "-f", pattern).start().waitFor() }
        }
        Thread.sleep(1500)

        if (!File(koupperBin).exists()) {
            return "\n  ERROR: koupper binary not found at $koupperBin\n"
        }

        // Boot octopus with full env so LLM vars are inherited by scripts
        val octopusJar = File("$home/.koupper/libs/octopus.jar")
        if (octopusJar.exists()) {
            val java = ProcessHandle.current().info().command().orElse("java")
            ProcessBuilder(java, "-jar", octopusJar.absolutePath)
                .also { pb -> forwardEnv(pb) }
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            Thread.sleep(2500)
        }

        val children = mutableListOf<Process>()
        Runtime.getRuntime().addShutdownHook(Thread {
            if (children.isNotEmpty()) {
                println("\n  Stopping background processes…")
                children.forEach { runCatching { it.destroyForcibly() } }
            }
        })

        // ── Worker ────────────────────────────────────────────────────────────
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

        if (webMode) {
            // ── Web UI mode: start dashboard, no TUI ─────────────────────────
            val agentsDir = File(home, ".koupper/agents")
            val webAgent  = File(agentsDir, "CortexWebUiAgent.kts")
            if (webAgent.exists()) {
                val webPort = resolveWebPort(18083)
                if (webPort != null) {
                    val webLog = File(home, ".koupper/logs/webui.log").also { it.parentFile.mkdirs() }
                    val webUi  = ProcessBuilder(koupperBin, "run", webAgent.absolutePath)
                        .also { pb ->
                            forwardEnv(pb)
                            pb.environment()["CORTEX_JOBS_DIR"] = jobsDir.absolutePath
                            pb.environment()["CORTEX_WEB_PORT"] = webPort.toString()
                        }
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.appendTo(webLog))
                        .start()
                    children.add(webUi)
                    println("  ${ANSI_GREEN_155}[web ui]${ANSI_RESET}   http://localhost:$webPort  (log: ~/.koupper/logs/webui.log)")
                }
            }
            // ── Dashboard dev server (optional) ──────────────────────────────
            if (dashboard) {
                val dashDir = resolveDashboardDir()
                if (dashDir != null) {
                    val npm = findNpm()
                    if (npm != null) {
                        val dashLog = File(home, ".koupper/logs/dashboard.log").also { it.parentFile.mkdirs() }
                        val dash = ProcessBuilder(npm, "run", "dev")
                            .directory(dashDir)
                            .redirectErrorStream(true)
                            .redirectOutput(ProcessBuilder.Redirect.appendTo(dashLog))
                            .start()
                        children.add(dash)
                        Thread.sleep(2000)
                        println("  ${ANSI_GREEN_155}[dashboard]${ANSI_RESET} http://localhost:5173  (log: ~/.koupper/logs/dashboard.log)")
                    } else {
                        println("  ${ANSI_YELLOW_229}[dashboard]${ANSI_RESET} npm not found — start manually: cd ${dashDir.absolutePath} && npm run dev")
                    }
                } else {
                    println("  ${ANSI_YELLOW_229}[dashboard]${ANSI_RESET} not found — set KOUPPER_DASHBOARD_PATH or clone to ~/koupper-dashboard")
                }
            }

            println()
            println("  ${ANSI_YELLOW_229}MCP tools${ANSI_RESET} : http://localhost:18082/mcp/tools")
            println()
            println("  Press Ctrl+C to stop.")
            // Block until killed
            Thread.currentThread().join()
        } else {
            // ── Terminal mode: TUI monitor blocks until user presses q ────────
            val monitorJar = File(home, ".koupper/libs/koupper-monitor.jar")
            if (!monitorJar.exists()) {
                children.forEach { runCatching { it.destroyForcibly() } }
                return "\n  ERROR: koupper-monitor.jar not found at ${monitorJar.absolutePath}\n"
            }
            if (isPortInUse(18083)) {
                println("  ${ANSI_GREEN_155}[web ui]${ANSI_RESET}   http://localhost:18083")
            }
            println()
            val java    = ProcessHandle.current().info().command().orElse("java")
            val monitor = ProcessBuilder(java, "-jar", monitorJar.absolutePath, jobsDir.absolutePath)
                .also { pb -> forwardEnv(pb) }
                .inheritIO()
                .start()
            children.add(monitor)
            monitor.waitFor()
        }

        return ""
    }

    private fun isPortInUse(port: Int): Boolean = runCatching {
        java.net.Socket("localhost", port).use { true }
    }.getOrDefault(false)

    private fun nextFreePort(from: Int): Int {
        var p = from
        while (p < 65535 && isPortInUse(p)) p++
        return p
    }

    /**
     * Returns the port to use for the web UI, or null if the user chose to skip.
     * - If [preferred] is free → returns it immediately.
     * - If [preferred] is occupied → asks the user; suggests the next free port.
     *   Input options: Enter / y → use suggestion | number → use that port | n → skip
     */
    private fun resolveWebPort(preferred: Int): Int? {
        if (!isPortInUse(preferred)) return preferred

        val suggestion = nextFreePort(preferred + 1)
        print("  ${ANSI_YELLOW_229}[web ui]${ANSI_RESET}   port $preferred is in use. Use $suggestion instead? [Y/n/port] ")
        System.out.flush()

        return when (val input = readLine()?.trim()?.lowercase() ?: "") {
            "", "y", "yes" -> suggestion
            "n", "no"      -> { println("  [web ui]   skipped."); null }
            else           -> input.toIntOrNull()
                ?.takeIf { it in 1024..65535 }
                ?: run { println("  [web ui]   invalid port, skipped."); null }
        }
    }

    private fun resolveDashboardDir(): File? {
        val candidates = listOfNotNull(
            System.getenv("KOUPPER_DASHBOARD_PATH")?.let { File(it) },
            File(home, "koupper-dashboard"),
            File(home, ".koupper/dashboard")
        )
        return candidates.firstOrNull { it.exists() && File(it, "package.json").exists() }
    }

    private fun findNpm(): String? =
        listOf("npm", "/usr/bin/npm", "/usr/local/bin/npm")
            .firstOrNull { runCatching { ProcessBuilder(it, "--version").start().waitFor() == 0 }.getOrDefault(false) }

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
