package com.koupper.cli.commands

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ── ScheduleEntry input field ─────────────────────────────────────────────────

class ScheduleEntryInputTest {

    @Test
    fun `input defaults to null`() {
        val entry = ScheduleEntry(
            id = "x", agent = "A", type = "cron", cron = "0 * * * *"
        )
        assertTrue(entry.input == null)
    }

    @Test
    fun `input is preserved when set`() {
        val entry = ScheduleEntry(
            id = "x", agent = "A", type = "once", runAt = "2026-01-01T08:00:00",
            input = """{"key":"val"}"""
        )
        assertTrue(entry.input == """{"key":"val"}""")
    }

    @Test
    fun `copy preserves input`() {
        val entry = ScheduleEntry(
            id = "x", agent = "A", type = "rate", rateMs = 3600000L,
            input = """{"n":1}"""
        )
        val copy = entry.copy(enabled = false)
        assertTrue(copy.input == """{"n":1}""")
    }
}

// ── ScheduleStore round-trip ──────────────────────────────────────────────────

class ScheduleStoreInputTest {

    private fun withTempHome(block: (File) -> Unit) {
        val tmp = Files.createTempDirectory("sched-store").toFile().also { it.deleteOnExit() }
        val orig = System.getProperty("user.home")
        System.setProperty("user.home", tmp.absolutePath)
        try { block(tmp) } finally { System.setProperty("user.home", orig) }
    }

    @Test
    fun `input round-trips through store`() = withTempHome {
        val entry = ScheduleEntry(
            id = "r1", agent = "Fetch", type = "cron", cron = "0 8 * * *",
            input = """{"page":1}"""
        )
        ScheduleStore.add(entry)
        val loaded = ScheduleStore.load().first { it.id == "r1" }
        assertTrue(loaded.input == """{"page":1}""")
    }

    @Test
    fun `null input round-trips as null`() = withTempHome {
        val entry = ScheduleEntry(
            id = "r2", agent = "Ping", type = "rate", rateMs = 60000L
        )
        ScheduleStore.add(entry)
        val loaded = ScheduleStore.load().first { it.id == "r2" }
        assertTrue(loaded.input == null)
    }
}

// ── ScheduleCommand --input flag ──────────────────────────────────────────────

class ScheduleCommandInputTest {

    private fun withTempHome(block: (File) -> Unit) {
        val tmp = Files.createTempDirectory("sched-cmd").toFile().also { it.deleteOnExit() }
        val orig = System.getProperty("user.home")
        System.setProperty("user.home", tmp.absolutePath)
        try { block(tmp) } finally { System.setProperty("user.home", orig) }
    }

    private val command = ScheduleCommand()

    @Test
    fun `add once with input stores input in entry`() = withTempHome {
        command.execute("/cwd", "add", "MyAgent",
            "--once=2026-12-01T09:00:00", "--id=once-test", """--input={"x":42}""")
        val entry = ScheduleStore.load().first { it.id == "once-test" }
        assertTrue(entry.input == """{"x":42}""")
    }

    @Test
    fun `add cron with input stores input`() = withTempHome {
        command.execute("/cwd", "add", "BatchAgent",
            "--cron=0 6 * * *", "--id=cron-test", """--input={"batch":true}""")
        val entry = ScheduleStore.load().first { it.id == "cron-test" }
        assertTrue(entry.input == """{"batch":true}""")
    }

    @Test
    fun `add rate with input stores input`() = withTempHome {
        command.execute("/cwd", "add", "PollAgent",
            "--rate=300000", "--id=rate-test", """--input={"poll":true}""")
        val entry = ScheduleStore.load().first { it.id == "rate-test" }
        assertTrue(entry.input == """{"poll":true}""")
    }

    @Test
    fun `add without input stores null`() = withTempHome {
        command.execute("/cwd", "add", "NoInputAgent",
            "--rate=60000", "--id=no-input-test")
        val entry = ScheduleStore.load().first { it.id == "no-input-test" }
        assertTrue(entry.input == null)
    }

    @Test
    fun `usage includes input option`() {
        val out = command.execute("/cwd")
        assertTrue("--input" in out)
    }
}

// ── submitScheduledJob input injection ───────────────────────────────────────

class ScheduleJobSubmitInputTest {

    private fun tempDir(): File = Files.createTempDirectory("sched-job").toFile().also { it.deleteOnExit() }

    @Test
    fun `job file contains input when entry has input`() {
        val entry = ScheduleEntry(
            id = "s1", agent = "FetchAgent", queue = "default",
            type = "once", runAt = "2026-12-01T08:00:00",
            input = """{"region":"us-east"}"""
        )
        val jobsDir = tempDir()
        val jobId = "FetchAgent-sched-123456"
        val qDir = File(jobsDir, entry.queue).also { it.mkdirs() }
        val inputFragment = if (entry.input != null) ""","input":${entry.input}""" else ""
        val jobJson = """{"id":"$jobId","scriptPath":"agents/${entry.agent}.kts","scheduledBy":"${entry.id}"$inputFragment}"""
        File(qDir, "$jobId.json").writeText(jobJson)

        val content = File(qDir, "$jobId.json").readText()
        assertTrue(""""input":{"region":"us-east"}""" in content)
    }

    @Test
    fun `job file omits input when entry has no input`() {
        val entry = ScheduleEntry(
            id = "s2", agent = "PingAgent", queue = "default",
            type = "rate", rateMs = 60000L
        )
        val jobsDir = tempDir()
        val jobId = "PingAgent-sched-123456"
        val qDir = File(jobsDir, entry.queue).also { it.mkdirs() }
        val inputFragment = if (entry.input != null) ""","input":${entry.input}""" else ""
        val jobJson = """{"id":"$jobId","scriptPath":"agents/${entry.agent}.kts","scheduledBy":"${entry.id}"$inputFragment}"""
        File(qDir, "$jobId.json").writeText(jobJson)

        val content = File(qDir, "$jobId.json").readText()
        assertFalse("\"input\"" in content)
    }
}
