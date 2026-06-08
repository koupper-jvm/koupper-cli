package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229

// Manages recurring agent schedules stored in ~/.koupper/schedules.json.
// The worker daemon reads this file and enqueues jobs at the right time.
//
// Usage:
//   koupper schedule add <agent> --cron="0 8 * * *" [--id=name] [--queue=q] [--input='{}']
//   koupper schedule add <agent> --rate=3600000       [--id=name] [--queue=q] [--input='{}']
//   koupper schedule add <agent> --once="2026-06-01T08:00:00" [--queue=q] [--input='{}']
//   koupper schedule list
//   koupper schedule remove <id>
//   koupper schedule enable  <id>
//   koupper schedule disable <id>
class ScheduleCommand : Command() {

    override fun name(): String = "schedule"

    override fun execute(vararg args: String): String {
        val sub = args.getOrNull(1) ?: return usage()
        return when (sub) {
            "add"     -> handleAdd(args)
            "list"    -> handleList()
            "remove"  -> handleRemove(args)
            "enable"  -> handleToggle(args, true)
            "disable" -> handleToggle(args, false)
            else      -> usage()
        }
    }

    // ── Add ───────────────────────────────────────────────────────────────────

    private fun handleAdd(args: Array<out String>): String {
        val agent = args.getOrNull(2)?.takeIf { !it.startsWith("--") }
            ?: return "\n${ANSI_RED}Error: agent name required — koupper schedule add <agent> ...${ANSI_RESET}\n"

        val cron    = flag(args, "--cron")
        val rateMs  = flag(args, "--rate")?.toLongOrNull()
        val runAt   = flag(args, "--once")
        val queue   = flag(args, "--queue") ?: "default"
        val id      = flag(args, "--id") ?: "$agent-${System.currentTimeMillis() % 100000}"
        val input   = flag(args, "--input")

        val (type, valid, hint) = when {
            cron   != null -> Triple("cron",  true,  "")
            rateMs != null -> Triple("rate",  true,  "")
            runAt  != null -> Triple("once",  true,  "")
            else           -> Triple("",      false, "--cron, --rate, or --once required")
        }
        if (!valid) return "\n${ANSI_RED}Error: $hint${ANSI_RESET}\n"

        val entry = ScheduleEntry(
            id     = id,
            agent  = agent,
            queue  = queue,
            type   = type,
            cron   = cron,
            rateMs = rateMs,
            runAt  = runAt,
            input  = input
        )
        ScheduleStore.add(entry)

        val desc = when (type) {
            "cron" -> "cron: $cron"
            "rate" -> "every ${rateMs!! / 1000}s"
            "once" -> "once at $runAt"
            else   -> ""
        }
        return "\n${ANSI_GREEN_155}Schedule added:${ANSI_RESET} $id  [$agent → $queue]  $desc\n"
    }

    // ── List ──────────────────────────────────────────────────────────────────

    private fun handleList(): String {
        val entries = ScheduleStore.load()
        if (entries.isEmpty()) return "\n  No schedules. Add one with: koupper schedule add <agent> --cron=\"...\"\n"

        return buildString {
            append("\n")
            append("  ${"ID".padEnd(30)} ${"AGENT".padEnd(20)} ${"TYPE".padEnd(6)} ${"SCHEDULE".padEnd(25)} STATUS\n")
            append("  ${"─".repeat(30)} ${"─".repeat(20)} ${"─".repeat(6)} ${"─".repeat(25)} ${"─".repeat(8)}\n")
            for (e in entries) {
                val schedule = when (e.type) {
                    "cron" -> e.cron ?: ""
                    "rate" -> "every ${(e.rateMs ?: 0) / 1000}s"
                    "once" -> e.runAt ?: ""
                    else   -> ""
                }
                val status = if (e.enabled) "${ANSI_GREEN_155}enabled${ANSI_RESET}" else "${ANSI_YELLOW_229}disabled${ANSI_RESET}"
                append("  ${e.id.padEnd(30)} ${e.agent.padEnd(20)} ${e.type.padEnd(6)} ${schedule.padEnd(25)} $status\n")
            }
        }
    }

    // ── Remove / Enable / Disable ─────────────────────────────────────────────

    private fun handleRemove(args: Array<out String>): String {
        val id = args.getOrNull(2) ?: return "\n${ANSI_RED}Error: id required — koupper schedule remove <id>${ANSI_RESET}\n"
        return if (ScheduleStore.remove(id))
            "\n${ANSI_GREEN_155}Schedule removed:${ANSI_RESET} $id\n"
        else
            "\n${ANSI_RED}Schedule not found:${ANSI_RESET} $id\n"
    }

    private fun handleToggle(args: Array<out String>, enabled: Boolean): String {
        val id = args.getOrNull(2) ?: return "\n${ANSI_RED}Error: id required${ANSI_RESET}\n"
        return if (ScheduleStore.setEnabled(id, enabled))
            "\n${ANSI_GREEN_155}Schedule ${if (enabled) "enabled" else "disabled"}:${ANSI_RESET} $id\n"
        else
            "\n${ANSI_RED}Schedule not found:${ANSI_RESET} $id\n"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun flag(args: Array<out String>, name: String): String? =
        args.firstOrNull { it.startsWith("$name=") }?.removePrefix("$name=")

    private fun usage() = """

  ${ANSI_YELLOW_229}koupper schedule${ANSI_RESET} — manage recurring agent schedules

  ${ANSI_GREEN_155}Commands:${ANSI_RESET}
    add <agent> --cron="0 8 * * *"    Schedule agent with cron expression
    add <agent> --rate=3600000         Schedule agent every N milliseconds
    add <agent> --once="2026-06-01T08:00:00"  Run once at a datetime
    list                               List all schedules
    remove <id>                        Remove a schedule
    enable <id>                        Enable a disabled schedule
    disable <id>                       Disable without removing

  ${ANSI_GREEN_155}Options:${ANSI_RESET}
    --id=<name>      Custom schedule ID (default: agent-timestamp)
    --queue=<q>      Target queue (default: default)
    --input=<json>   JSON input forwarded to the agent on every run

  ${ANSI_GREEN_155}Examples:${ANSI_RESET}
    koupper schedule add DataAgent --cron="0 8 * * 1-5" --id=daily-data
    koupper schedule add HealthCheck --rate=300000
    koupper schedule list
    koupper schedule disable daily-data

  Schedules are stored in ~/.koupper/schedules.json.
  The worker daemon reads this file when started with --enable-scheduling.

"""
}
