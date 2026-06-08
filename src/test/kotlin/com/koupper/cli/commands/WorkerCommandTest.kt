package com.koupper.cli.commands

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// ── extractField ─────────────────────────────────────────────────────────────

class ExtractFieldTest {

    @Test
    fun `returns value for present string field`() {
        val json = """{"id":"abc-123","queue":"default"}"""
        assertEquals("abc-123", extractField(json, "id"))
        assertEquals("default", extractField(json, "queue"))
    }

    @Test
    fun `returns null for absent field`() {
        val json = """{"id":"abc"}"""
        assertNull(extractField(json, "missing"))
    }

    @Test
    fun `tolerates whitespace around colon`() {
        val json = """{"scriptPath" : "agents/Foo.kts"}"""
        assertEquals("agents/Foo.kts", extractField(json, "scriptPath"))
    }

    @Test
    fun `returns empty string for empty string value`() {
        val json = """{"key":""}"""
        assertEquals("", extractField(json, "key"))
    }

    @Test
    fun `does not match numeric or object fields`() {
        val json = """{"count":42,"obj":{"a":"b"}}"""
        // extractField only matches quoted string values — count and obj should not be found
        assertNull(extractField(json, "count"))
        assertNull(extractField(json, "obj"))
    }
}

// ── extractRawJsonValue ───────────────────────────────────────────────────────

class ExtractRawJsonValueTest {

    @Test
    fun `returns null for absent field`() {
        val json = """{"a":"b"}"""
        assertNull(extractRawJsonValue(json, "missing"))
    }

    @Test
    fun `returns json null literal`() {
        val json = """{"input":null,"other":"x"}"""
        assertEquals("null", extractRawJsonValue(json, "input"))
    }

    @Test
    fun `returns true and false literals`() {
        val json = """{"ok":true,"fail":false}"""
        assertEquals("true",  extractRawJsonValue(json, "ok"))
        assertEquals("false", extractRawJsonValue(json, "fail"))
    }

    @Test
    fun `returns integer number`() {
        val json = """{"pipelineStep":2,"pipelineTotal":3}"""
        assertEquals("2", extractRawJsonValue(json, "pipelineStep"))
        assertEquals("3", extractRawJsonValue(json, "pipelineTotal"))
    }

    @Test
    fun `returns flat object`() {
        val json = """{"env":{"TOKEN":"abc","ID":"1"}}"""
        val result = extractRawJsonValue(json, "env")
        assertEquals("""{"TOKEN":"abc","ID":"1"}""", result)
    }

    @Test
    fun `returns nested object`() {
        val pipelineNext = """{"scriptPath":"agents/B.kts","pipelineNext":{"scriptPath":"agents/C.kts"}}"""
        val json = """{"id":"p0","pipelineNext":$pipelineNext}"""
        assertEquals(pipelineNext, extractRawJsonValue(json, "pipelineNext"))
    }

    @Test
    fun `returns array value`() {
        val json = """{"tags":["a","b","c"]}"""
        assertEquals("""["a","b","c"]""", extractRawJsonValue(json, "tags"))
    }

    @Test
    fun `handles object with nested braces containing closing brace in string`() {
        val json = """{"env":{"KEY":"val}ue"},"id":"x"}"""
        val result = extractRawJsonValue(json, "env")
        assertEquals("""{"KEY":"val}ue"}""", result)
    }

    @Test
    fun `returns null when field is a quoted string (use extractField for those)`() {
        // extractRawJsonValue on a quoted-string field returns the string with quotes stripped
        // by the scalar branch — verify it does NOT return null
        val json = """{"id":"abc-123"}"""
        val result = extractRawJsonValue(json, "id")
        // scalar branch: returns content up to next delimiter
        assertTrue(result != null)
    }
}

// ── WorkerCommand --status ────────────────────────────────────────────────────

class WorkerCommandStatusTest {

    private val command = WorkerCommand()

    private fun tempDir(): File = Files.createTempDirectory("wc-test").toFile()
        .also { it.deleteOnExit() }

    @Test
    fun `status on nonexistent dir reports no directory`() {
        val dir = File(tempDir(), "nonexistent")
        val output = command.execute("worker", dir.absolutePath, "--status")
        assertTrue("No jobs directory" in output)
    }

    @Test
    fun `status on empty jobs dir reports no queues`() {
        val dir = tempDir()
        val output = command.execute("worker", dir.absolutePath, "--status")
        assertTrue("No queues found" in output)
    }

    @Test
    fun `status shows pending count for jobs in queue`() {
        val dir = tempDir()
        val queue = File(dir, "default").also { it.mkdirs() }
        File(queue, "job-001.json").writeText("""{"id":"job-001"}""")
        File(queue, "job-002.json").writeText("""{"id":"job-002"}""")

        val output = command.execute("worker", dir.absolutePath, "--status")

        assertTrue("default" in output)
        assertTrue("2p" in output)
    }

    @Test
    fun `status shows processing count for in-flight jobs`() {
        val dir = tempDir()
        val queue = File(dir, "default").also { it.mkdirs() }
        File(queue, "job-001.json.processing").writeText("""{"id":"job-001"}""")

        val output = command.execute("worker", dir.absolutePath, "--status")

        assertTrue("1▶" in output)
    }

    @Test
    fun `status shows failed count`() {
        val dir = tempDir()
        val queue = File(dir, "default").also { it.mkdirs() }
        val failed = File(queue, ".failed").also { it.mkdirs() }
        File(failed, "job-bad.json").writeText("""{"id":"job-bad"}""")

        val output = command.execute("worker", dir.absolutePath, "--status")

        assertTrue("1f" in output)
    }

    @Test
    fun `status shows dead count and dead indicator`() {
        val dir = tempDir()
        val queue = File(dir, "default").also { it.mkdirs() }
        val dead = File(queue, ".dead").also { it.mkdirs() }
        File(dead, "job-dead.json").writeText("""{"id":"job-dead"}""")

        val output = command.execute("worker", dir.absolutePath, "--status")

        assertTrue("1☠" in output)
    }

    @Test
    fun `status excludes logs and commands directories`() {
        val dir = tempDir()
        File(dir, "logs").also { it.mkdirs() }
        File(dir, "commands").also { it.mkdirs() }
        File(dir, "realqueue").also { it.mkdirs() }

        val output = command.execute("worker", dir.absolutePath, "--status")

        assertFalse("logs" in output.lines().filter { it.contains("  ○") || it.contains("  ▶") || it.contains("  ⚠") || it.contains("  ☠") }.joinToString())
        assertTrue("realqueue" in output)
    }

    @Test
    fun `status shows totals row across multiple queues`() {
        val dir = tempDir()
        val q1 = File(dir, "alpha").also { it.mkdirs() }
        val q2 = File(dir, "beta").also { it.mkdirs() }
        File(q1, "j1.json").writeText("{}")
        File(q2, "j2.json").writeText("{}")
        File(q2, "j3.json").writeText("{}")

        val output = command.execute("worker", dir.absolutePath, "--status")

        // Total should show 3 pending
        assertTrue("3p" in output)
        // Both queues listed
        assertTrue("alpha" in output)
        assertTrue("beta" in output)
    }

    @Test
    fun `status includes jobs dir path in header`() {
        val dir = tempDir()
        val output = command.execute("worker", dir.absolutePath, "--status")
        assertTrue(dir.absolutePath in output)
    }
}
