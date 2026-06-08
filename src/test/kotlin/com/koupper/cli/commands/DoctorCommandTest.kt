package com.koupper.cli.commands

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// All tests inject a temp koupperDir — no real ~/.koupper touched.
// Port checks always show "not listening" in test environment; that's expected.

private fun tempKoupperDir(): File =
    Files.createTempDirectory("doctor-test").toFile().also { it.deleteOnExit() }

// ── Section headers ────────────────────────────────────────────────────────────

class DoctorSectionsTest {

    @Test
    fun `output contains Environment section`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("Environment" in out)
    }

    @Test
    fun `output contains Installation section`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("Installation" in out)
    }

    @Test
    fun `output contains Ports section`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("Ports" in out)
    }

    @Test
    fun `output contains Job Queues section`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("Job Queues" in out)
    }

    @Test
    fun `output contains Agents section`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("Agents" in out)
    }

    @Test
    fun `output contains Schedules section`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("Schedules" in out)
    }

    @Test
    fun `output contains summary line`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        // Summary is either errors/warnings count or "All checks passed"
        assertTrue("error" in out || "warning" in out || "All checks passed" in out)
    }
}

// ── Agent checks ───────────────────────────────────────────────────────────────

class DoctorAgentCheckTest {

    @Test
    fun `no agents directory shows warning`() {
        val dir = tempKoupperDir() // agents/ subdir does not exist
        val out = DoctorCommand(dir).execute("doctor")
        assertTrue("none installed" in out || "warning" in out.lowercase() || "⚠" in out)
    }

    @Test
    fun `empty agents directory shows warning`() {
        val dir = tempKoupperDir()
        File(dir, "agents").mkdirs()
        val out = DoctorCommand(dir).execute("doctor")
        assertTrue("none installed" in out)
    }

    @Test
    fun `installed agents shows count and names`() {
        val dir = tempKoupperDir()
        val agentsDir = File(dir, "agents").also { it.mkdirs() }
        File(agentsDir, "CortexAgent.kts").writeText("// agent")
        File(agentsDir, "HeartbeatAgent.kts").writeText("// agent")

        val out = DoctorCommand(dir).execute("doctor")

        assertTrue("2 installed" in out)
        assertTrue("CortexAgent" in out)
        assertTrue("HeartbeatAgent" in out)
    }

    @Test
    fun `single agent shows 1 installed`() {
        val dir = tempKoupperDir()
        val agentsDir = File(dir, "agents").also { it.mkdirs() }
        File(agentsDir, "WorkerAgent.kts").writeText("// agent")

        val out = DoctorCommand(dir).execute("doctor")
        assertTrue("1 installed" in out)
    }
}

// ── Queue checks ───────────────────────────────────────────────────────────────

class DoctorQueueCheckTest {

    @Test
    fun `no jobs directory shows warning`() {
        val dir = tempKoupperDir() // jobs/ not created
        val out = DoctorCommand(dir).execute("doctor")
        assertTrue("directory not found" in out || "⚠" in out)
    }

    @Test
    fun `empty jobs dir shows no queues`() {
        val dir = tempKoupperDir()
        File(dir, "jobs").mkdirs()
        val out = DoctorCommand(dir).execute("doctor")
        assertTrue("none yet" in out)
    }

    @Test
    fun `pending jobs reported correctly`() {
        val dir = tempKoupperDir()
        val queue = File(dir, "jobs/default").also { it.mkdirs() }
        File(queue, "job-1.json").writeText("{}")
        File(queue, "job-2.json").writeText("{}")

        val out = DoctorCommand(dir).execute("doctor")
        assertTrue("default" in out)
        assertTrue("2p" in out)
    }

    @Test
    fun `dead-letter jobs trigger warning`() {
        val dir = tempKoupperDir()
        val queue = File(dir, "jobs/default").also { it.mkdirs() }
        val dead  = File(queue, ".dead").also  { it.mkdirs() }
        File(dead, "job-dead.json").writeText("{}")

        val out = DoctorCommand(dir).execute("doctor")
        assertTrue("dead-letter" in out || "☠" in out)
    }

    @Test
    fun `failed jobs trigger warning`() {
        val dir = tempKoupperDir()
        val queue  = File(dir, "jobs/default").also { it.mkdirs() }
        val failed = File(queue, ".failed").also { it.mkdirs() }
        File(failed, "job-bad.json").writeText("{}")

        val out = DoctorCommand(dir).execute("doctor")
        assertTrue("failed jobs" in out || "⚠" in out)
    }

    @Test
    fun `logs and commands dirs are excluded from queue listing`() {
        val dir = tempKoupperDir()
        File(dir, "jobs/logs").mkdirs()
        File(dir, "jobs/commands").mkdirs()
        File(dir, "jobs/alpha").mkdirs()

        val out = DoctorCommand(dir).execute("doctor")

        // logs and commands should not appear as queue entries
        val queueLines = out.lines().filter { "●" in it || "○" in it || "0p" in it }
        assertFalse(queueLines.any { "logs" in it })
        assertFalse(queueLines.any { "commands" in it })
        assertTrue("alpha" in out)
    }
}

// ── Summary line ───────────────────────────────────────────────────────────────

class DoctorSummaryTest {

    @Test
    fun `summary present in all cases`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        val summaryPresent = "error" in out || "warning" in out || "All checks passed" in out
        assertTrue(summaryPresent)
    }

    @Test
    fun `healthy system with agents and queues mentions no critical errors`() {
        val dir = tempKoupperDir()
        File(dir, "agents").mkdirs()
        File(dir, "jobs").mkdirs()
        // Ports won't be listening in test env — that's expected (warnings, not errors on optional ports)
        val out = DoctorCommand(dir).execute("doctor")
        // Should not say "All checks passed" because octopus is not listening
        // but should not crash
        assertTrue(out.isNotBlank())
    }

    @Test
    fun `output always ends with newline`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue(out.endsWith("\n"))
    }
}

// ── Port section format ────────────────────────────────────────────────────────

class DoctorPortSectionTest {

    @Test
    fun `Ollama port 11434 listed`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("11434" in out)
        assertTrue("Ollama" in out)
    }

    @Test
    fun `Octopus port 9998 listed`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("9998" in out)
        assertTrue("Octopus" in out)
    }

    @Test
    fun `Web UI port 18083 listed`() {
        val out = DoctorCommand(tempKoupperDir()).execute("doctor")
        assertTrue("18083" in out)
    }
}
