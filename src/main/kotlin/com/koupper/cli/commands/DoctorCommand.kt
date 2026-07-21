package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

// Diagnoses the Koupper runtime environment (OS install + local ~/.koupper).
//
// Usage: koupper doctor
//
// Checks:
//   - Java runtime (17+)
//   - PATH includes ~/.koupper/bin
//   - Optional LLM env vars (warnings only — not required for API/infra workloads)
//   - Installed JAR / shim files
//   - Port availability (9998 octopus, optional LLM/MCP/UI)
//   - Job queues (pending / processing / failed / dead)
//   - Installed agents
//   - Configured schedules
class DoctorCommand(
    private val homeDir: File = File(System.getProperty("user.home")!!),
    private val env: (String) -> String? = { System.getenv(it) },
    private val javaVersion: () -> String = { System.getProperty("java.version") ?: "" },
    private val pathEnv: () -> String? = { System.getenv("PATH") ?: System.getenv("Path") },
    private val isWindows: Boolean = System.getProperty("os.name")
        .lowercase(Locale.getDefault())
        .contains("win"),
    private val portProbe: (Int) -> Boolean = { port -> isPortListening(port) },
    private val scheduleLoader: () -> List<ScheduleEntry> = { ScheduleStore.load() }
) : Command() {

    override fun name(): String = "doctor"

    private val koupperDir = File(homeDir, ".koupper")

    private var errors = 0
    private var warnings = 0

    private fun ok(label: String, detail: String = "") =
        "  ${ANSI_GREEN_155}✓${ANSI_RESET}  ${pad(label)}${ANSI_GREEN_155}$detail${ANSI_RESET}"

    private fun warn(label: String, detail: String = "") =
        "  ${ANSI_YELLOW_229}⚠${ANSI_RESET}  ${pad(label)}${ANSI_YELLOW_229}$detail${ANSI_RESET}".also { warnings++ }

    private fun err(label: String, detail: String = "") =
        "  ${ANSI_RED}✗${ANSI_RESET}  ${pad(label)}${ANSI_RED}$detail${ANSI_RESET}".also { errors++ }

    private fun info(label: String, detail: String = "") = "       ${pad(label)}$detail"
    private fun pad(s: String) = s.padEnd(26)

    private fun emit(sb: StringBuilder, check: DoctorChecks.Check) {
        sb.appendLine(
            when (check.level) {
                DoctorChecks.Level.OK -> ok(check.label, check.detail)
                DoctorChecks.Level.WARN -> warn(check.label, check.detail)
                DoctorChecks.Level.ERROR -> err(check.label, check.detail)
                DoctorChecks.Level.INFO -> info(check.label, check.detail)
            }
        )
    }

    override fun execute(vararg args: String): String {
        errors = 0
        warnings = 0
        val sb = StringBuilder()

        sb.appendLine()
        sb.appendLine("${ANSI_GREEN_155}  ◈ KOUPPER DOCTOR${ANSI_RESET}")
        sb.appendLine()

        // ── Runtime (OS) ──────────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Runtime${ANSI_RESET}")
        emit(sb, DoctorChecks.javaRuntimeCheck(javaVersion()))

        val binDir = File(koupperDir, "bin")
        if (DoctorChecks.pathContainsDir(pathEnv(), binDir)) {
            sb.appendLine(ok("PATH", "includes ${binDir.absolutePath}"))
        } else {
            sb.appendLine(
                warn(
                    "PATH",
                    "missing ${binDir.absolutePath} — add it for OS-level `koupper` access"
                )
            )
        }
        sb.appendLine(info("OS", if (isWindows) "Windows" else "Unix-like"))
        sb.appendLine()

        // ── Environment variables ─────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Environment${ANSI_RESET}")

        val modelPath = env("KOUPPER_LLM_MODEL_PATH")
        if (modelPath.isNullOrBlank()) {
            sb.appendLine(warn("KOUPPER_LLM_MODEL_PATH", "not set — LLM inference unavailable (OK for API/infra)"))
        } else {
            val f = File(modelPath)
            if (!f.exists()) sb.appendLine(err("KOUPPER_LLM_MODEL_PATH", "set but file not found: $modelPath"))
            else sb.appendLine(ok("KOUPPER_LLM_MODEL_PATH", "${f.name} (${f.length() / 1_048_576}MB)"))
        }

        val execPath = env("KOUPPER_LLM_EXECUTABLE")
        if (execPath.isNullOrBlank()) {
            sb.appendLine(warn("KOUPPER_LLM_EXECUTABLE", "not set — will try 'llama-server' from PATH"))
        } else {
            val f = File(execPath)
            if (!f.exists()) sb.appendLine(err("KOUPPER_LLM_EXECUTABLE", "set but not found: $execPath"))
            else if (!f.canExecute()) sb.appendLine(warn("KOUPPER_LLM_EXECUTABLE", "found but not executable: $execPath"))
            else sb.appendLine(ok("KOUPPER_LLM_EXECUTABLE", execPath))
        }

        val workerTimeout = env("KOUPPER_WORKER_TIMEOUT")
        if (!workerTimeout.isNullOrBlank()) {
            sb.appendLine(info("KOUPPER_WORKER_TIMEOUT", "${workerTimeout}s"))
        }

        sb.appendLine()

        // ── Installed files ───────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Installation${ANSI_RESET}")

        DoctorChecks.installationFiles(koupperDir, isWindows).forEach { item ->
            checkFile(sb, item.label, item.file, sizeMb = item.sizeMb, required = item.required)
        }

        sb.appendLine()

        // ── Ports ─────────────────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Ports${ANSI_RESET}")

        checkPort(sb, 9998, "Octopus socket")
        checkPort(sb, 8081, "llama-server (LLM)", required = false)
        checkPort(sb, 18082, "MCP server", required = false)
        checkPort(sb, 18083, "Web UI", required = false)

        sb.appendLine()

        // ── Job queues ────────────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Job Queues${ANSI_RESET}  (${File(koupperDir, "jobs").absolutePath})")

        val jobsDir = File(koupperDir, "jobs")
        val excluded = setOf("logs", "commands")

        if (!jobsDir.exists()) {
            sb.appendLine(warn("jobs/", "directory not found — run koupper start once"))
        } else {
            val queues = WorkerJobPolicy.listQueues(jobsDir, excluded)

            if (queues.isEmpty()) {
                sb.appendLine(info("queues", "none yet"))
            } else {
                queues.forEach { qDir ->
                    val c = WorkerJobPolicy.countQueue(qDir)
                    val detail = "${c.pending}p  ${c.processing}▶  ${c.failed}f  ${c.dead}☠"
                    val line = when {
                        c.dead > 0 -> warn(qDir.name, "$detail  ← dead-letter jobs need attention")
                        c.failed > 0 -> warn(qDir.name, "$detail  ← failed jobs present")
                        c.processing > 0 -> ok(qDir.name, detail)
                        else -> ok(qDir.name, detail)
                    }
                    sb.appendLine(line)
                }
            }
        }

        sb.appendLine()

        // ── Agents ────────────────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Agents${ANSI_RESET}")

        val agentsDir = File(koupperDir, "agents")
        val agents = agentsDir.listFiles { f -> f.name.endsWith(".kts") } ?: emptyArray()
        if (agents.isEmpty()) {
            sb.appendLine(warn("agents", "none installed in ~/.koupper/agents/"))
        } else {
            sb.appendLine(ok("agents", "${agents.size} installed"))
            agents.sortedBy { it.name }.forEach { f ->
                sb.appendLine(info("", "  · ${f.nameWithoutExtension}"))
            }
        }

        sb.appendLine()

        // ── Schedules ─────────────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Schedules${ANSI_RESET}")

        val schedulesFile = File(koupperDir, "schedules.json")
        if (!schedulesFile.exists()) {
            sb.appendLine(info("schedules", "none — use koupper schedule add"))
        } else {
            val entries = scheduleLoader()
            val active = entries.count { it.enabled }
            if (entries.isEmpty()) {
                sb.appendLine(info("schedules", "file exists but empty"))
            } else {
                sb.appendLine(ok("schedules", "${entries.size} configured, $active active"))
                entries.forEach { e ->
                    val trigger = when (e.type) {
                        "cron" -> "cron: ${e.cron}"
                        "rate" -> "every ${(e.rateMs ?: 0) / 1000}s"
                        "once" -> "once: ${e.runAt}"
                        else -> e.type
                    }
                    val status = if (e.enabled) "${ANSI_GREEN_155}●${ANSI_RESET}" else "${ANSI_YELLOW_229}○${ANSI_RESET}"
                    sb.appendLine(info("", "  $status ${e.id}  ($trigger)"))
                }
            }
        }

        sb.appendLine()

        // ── Summary ───────────────────────────────────────────────────────────

        val summary = when {
            errors > 0 -> "${ANSI_RED}  $errors error(s)${ANSI_RESET}" +
                (if (warnings > 0) ", ${ANSI_YELLOW_229}$warnings warning(s)${ANSI_RESET}" else "") +
                " — system may not work correctly"
            warnings > 0 -> "${ANSI_YELLOW_229}  $warnings warning(s)${ANSI_RESET} — system functional but check above"
            else -> "${ANSI_GREEN_155}  All checks passed — system healthy${ANSI_RESET}"
        }
        sb.appendLine(summary)
        sb.appendLine()

        return sb.toString()
    }

    private fun checkFile(
        sb: StringBuilder,
        label: String,
        file: File,
        sizeMb: Boolean = false,
        required: Boolean = true
    ) {
        if (!file.exists()) {
            if (required) sb.appendLine(err(label, "not found: ${file.absolutePath}"))
            else sb.appendLine(warn(label, "not found (optional): ${file.absolutePath}"))
        } else {
            val detail = if (sizeMb) "${file.absolutePath} (${file.length() / 1_048_576}MB)" else file.absolutePath
            sb.appendLine(ok(label, detail))
        }
    }

    private fun checkPort(sb: StringBuilder, port: Int, label: String, required: Boolean = true) {
        val listening = portProbe(port)
        val portLabel = "$port  $label"
        if (listening) {
            sb.appendLine(ok(portLabel, "listening"))
        } else if (required) {
            sb.appendLine(err(portLabel, "not listening — is koupper running?"))
        } else {
            sb.appendLine(warn(portLabel, "not listening"))
        }
    }

    companion object {
        fun isPortListening(port: Int): Boolean = try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 300)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
