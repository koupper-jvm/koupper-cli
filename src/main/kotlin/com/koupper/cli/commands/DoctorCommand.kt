package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

// Diagnoses the Koupper / CORTEX runtime environment.
//
// Usage: koupper doctor
//
// Checks:
//   - Required env vars (KOUPPER_LLM_MODEL_PATH, KOUPPER_LLM_EXECUTABLE)
//   - Installed JAR files (octopus, cli, monitor)
//   - Port availability (9998 octopus, 8081 llama-server, 18082 MCP, 18083 Web UI)
//   - Job queues (pending / processing / failed / dead counts)
//   - Installed agents
//   - Configured schedules
class DoctorCommand : Command() {

    override fun name(): String = "doctor"

    private val home       = System.getProperty("user.home")!!
    private val koupperDir = File(home, ".koupper")

    private var errors   = 0
    private var warnings = 0

    private fun ok(label: String, detail: String = "")   = "  ${ANSI_GREEN_155}✓${ANSI_RESET}  ${pad(label)}${ANSI_GREEN_155}$detail${ANSI_RESET}"
    private fun warn(label: String, detail: String = "")  = "  ${ANSI_YELLOW_229}⚠${ANSI_RESET}  ${pad(label)}${ANSI_YELLOW_229}$detail${ANSI_RESET}".also { warnings++ }
    private fun err(label: String, detail: String = "")   = "  ${ANSI_RED}✗${ANSI_RESET}  ${pad(label)}${ANSI_RED}$detail${ANSI_RESET}".also { errors++ }
    private fun info(label: String, detail: String = "")  = "       ${pad(label)}$detail"
    private fun pad(s: String) = s.padEnd(26)

    override fun execute(vararg args: String): String {
        errors = 0; warnings = 0
        val sb = StringBuilder()

        sb.appendLine("\n${ANSI_GREEN_155}  ◈ KOUPPER DOCTOR${ANSI_RESET}\n")

        // ── Environment variables ─────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Environment${ANSI_RESET}")

        val modelPath = System.getenv("KOUPPER_LLM_MODEL_PATH")
        if (modelPath.isNullOrBlank()) {
            sb.appendLine(err("KOUPPER_LLM_MODEL_PATH", "not set — LLM inference will fail"))
        } else {
            val f = File(modelPath)
            if (!f.exists()) sb.appendLine(err("KOUPPER_LLM_MODEL_PATH", "set but file not found: $modelPath"))
            else sb.appendLine(ok("KOUPPER_LLM_MODEL_PATH", "${f.name} (${f.length() / 1_048_576}MB)"))
        }

        val execPath = System.getenv("KOUPPER_LLM_EXECUTABLE")
        if (execPath.isNullOrBlank()) {
            sb.appendLine(warn("KOUPPER_LLM_EXECUTABLE", "not set — will try 'llama-server' from PATH"))
        } else {
            val f = File(execPath)
            if (!f.exists()) sb.appendLine(err("KOUPPER_LLM_EXECUTABLE", "set but not found: $execPath"))
            else if (!f.canExecute()) sb.appendLine(warn("KOUPPER_LLM_EXECUTABLE", "found but not executable: $execPath"))
            else sb.appendLine(ok("KOUPPER_LLM_EXECUTABLE", execPath))
        }

        val workerTimeout = System.getenv("KOUPPER_WORKER_TIMEOUT")
        if (!workerTimeout.isNullOrBlank())
            sb.appendLine(info("KOUPPER_WORKER_TIMEOUT", "${workerTimeout}s"))

        sb.appendLine()

        // ── Installed files ───────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Installation${ANSI_RESET}")

        val libsDir   = File(koupperDir, "libs")
        val agentsDir = File(koupperDir, "agents")
        val binDir    = File(koupperDir, "bin")

        checkFile(sb, "koupper binary",     File(binDir,    "koupper"))
        checkFile(sb, "octopus.jar",        File(libsDir,   "octopus.jar"),       sizeMb = true)
        checkFile(sb, "koupper-cli.jar",    File(libsDir,   "koupper-cli.jar"),   sizeMb = true)
        checkFile(sb, "koupper-monitor.jar",File(libsDir,   "koupper-monitor.jar"))

        sb.appendLine()

        // ── Ports ─────────────────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Ports${ANSI_RESET}")

        checkPort(sb, 9998,  "Octopus socket")
        checkPort(sb, 8081,  "llama-server (LLM)", required = false)
        checkPort(sb, 18082, "MCP server", required = false)
        checkPort(sb, 18083, "Web UI", required = false)

        sb.appendLine()

        // ── Job queues ────────────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Job Queues${ANSI_RESET}  (${File(koupperDir, "jobs").absolutePath})")

        val jobsDir  = File(koupperDir, "jobs")
        val excluded = setOf("logs", "commands")

        if (!jobsDir.exists()) {
            sb.appendLine(warn("jobs/", "directory not found — run koupper start once"))
        } else {
            val queues = jobsDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
                ?: emptyList()

            if (queues.isEmpty()) {
                sb.appendLine(info("queues", "none yet"))
            } else {
                queues.forEach { qDir ->
                    val pending    = qDir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
                    val processing = qDir.listFiles { f -> f.name.endsWith(".json.processing") }?.size ?: 0
                    val failed     = File(qDir, ".failed").listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
                    val dead       = File(qDir, ".dead").listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

                    val detail = "${pending}p  ${processing}▶  ${failed}f  ${dead}☠"
                    val line = when {
                        dead > 0    -> warn(qDir.name, "$detail  ← dead-letter jobs need attention")
                        failed > 0  -> warn(qDir.name, "$detail  ← failed jobs present")
                        processing > 0 -> ok(qDir.name, "$detail")
                        else        -> ok(qDir.name, detail)
                    }
                    sb.appendLine(line)
                }
            }
        }

        sb.appendLine()

        // ── Agents ────────────────────────────────────────────────────────────

        sb.appendLine("  ${ANSI_YELLOW_229}Agents${ANSI_RESET}")

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
            val entries = ScheduleStore.load()
            val active  = entries.count { it.enabled }
            if (entries.isEmpty()) {
                sb.appendLine(info("schedules", "file exists but empty"))
            } else {
                sb.appendLine(ok("schedules", "${entries.size} configured, $active active"))
                entries.forEach { e ->
                    val trigger = when (e.type) {
                        "cron" -> "cron: ${e.cron}"
                        "rate" -> "every ${(e.rateMs ?: 0) / 1000}s"
                        "once" -> "once: ${e.runAt}"
                        else   -> e.type
                    }
                    val status = if (e.enabled) "${ANSI_GREEN_155}●${ANSI_RESET}" else "${ANSI_YELLOW_229}○${ANSI_RESET}"
                    sb.appendLine(info("", "  $status ${e.id}  ($trigger)"))
                }
            }
        }

        sb.appendLine()

        // ── Summary ───────────────────────────────────────────────────────────

        val summary = when {
            errors > 0   -> "${ANSI_RED}  $errors error(s)${ANSI_RESET}" +
                            (if (warnings > 0) ", ${ANSI_YELLOW_229}$warnings warning(s)${ANSI_RESET}" else "") +
                            " — system may not work correctly"
            warnings > 0 -> "${ANSI_YELLOW_229}  $warnings warning(s)${ANSI_RESET} — system functional but check above"
            else         -> "${ANSI_GREEN_155}  All checks passed — system healthy${ANSI_RESET}"
        }
        sb.appendLine(summary)
        sb.appendLine()

        return sb.toString()
    }

    private fun checkFile(sb: StringBuilder, label: String, file: File, sizeMb: Boolean = false) {
        if (!file.exists()) {
            sb.appendLine(err(label, "not found: ${file.absolutePath}"))
        } else {
            val detail = if (sizeMb) "${file.absolutePath} (${file.length() / 1_048_576}MB)" else file.absolutePath
            sb.appendLine(ok(label, detail))
        }
    }

    private fun checkPort(sb: StringBuilder, port: Int, label: String, required: Boolean = true) {
        val listening = try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 300)
                true
            }
        } catch (_: Exception) { false }

        val portLabel = "$port  $label"
        if (listening) {
            sb.appendLine(ok(portLabel, "listening"))
        } else if (required) {
            sb.appendLine(err(portLabel, "not listening — is koupper running?"))
        } else {
            sb.appendLine(warn(portLabel, "not listening"))
        }
    }
}
