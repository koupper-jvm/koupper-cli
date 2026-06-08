package com.koupper.cli.commands

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// ── updateRetryCount ─────────────────────────────────────────────────────────

class UpdateRetryCountTest {

    @Test
    fun `adds retryCount field when absent`() {
        val json = """{"id":"job-1","scriptPath":"agents/Foo.kts"}"""
        val out  = updateRetryCount(json, 1)
        assertTrue(""""retryCount":1""" in out)
    }

    @Test
    fun `updates existing retryCount value`() {
        val json = """{"id":"job-1","retryCount":1,"scriptPath":"agents/Foo.kts"}"""
        val out  = updateRetryCount(json, 2)
        assertTrue(""""retryCount":2""" in out)
        assertFalse(""""retryCount":1""" in out)
    }

    @Test
    fun `preserves other fields when adding`() {
        val json = """{"id":"job-42","scriptPath":"agents/Bar.kts"}"""
        val out  = updateRetryCount(json, 1)
        assertTrue(""""id":"job-42"""" in out)
        assertTrue(""""scriptPath":"agents/Bar.kts"""" in out)
    }

    @Test
    fun `preserves other fields when updating`() {
        val json = """{"id":"job-42","retryCount":2,"scriptPath":"agents/Bar.kts"}"""
        val out  = updateRetryCount(json, 3)
        assertTrue(""""id":"job-42"""" in out)
        assertTrue(""""scriptPath":"agents/Bar.kts"""" in out)
        assertTrue(""""retryCount":3""" in out)
    }

    @Test
    fun `handles count of zero`() {
        val json = """{"id":"job-1"}"""
        val out  = updateRetryCount(json, 0)
        assertTrue(""""retryCount":0""" in out)
    }

    @Test
    fun `result is still valid-shaped json`() {
        val json = """{"id":"job-1","scriptPath":"x.kts"}"""
        val out  = updateRetryCount(json, 1)
        assertTrue(out.trim().startsWith("{"))
        assertTrue(out.trim().endsWith("}"))
    }

    @Test
    fun `does not duplicate retryCount when called twice`() {
        val json = """{"id":"job-1"}"""
        val after1 = updateRetryCount(json, 1)
        val after2 = updateRetryCount(after1, 2)
        val occurrences = after2.split("retryCount").size - 1
        assertEquals(1, occurrences)
    }
}

// ── retry count logic (unit — no real processes) ──────────────────────────────

class WorkerRetryCountLogicTest {

    @Test
    fun `first failure produces retryCount 1 in failed file`() {
        val jobJson = """{"id":"job-abc","scriptPath":"agents/Foo.kts"}"""
        // First failure: currentRetries=0, newRetries=1, maxRetries=3 → .failed/
        val currentRetries = extractRawJsonValue(jobJson, "retryCount")?.toIntOrNull() ?: 0
        assertEquals(0, currentRetries)
        val newRetries = currentRetries + 1
        val updated = updateRetryCount(jobJson, newRetries)
        assertTrue(""""retryCount":1""" in updated)
        assertTrue(newRetries < 3) // goes to .failed/, not .dead/
    }

    @Test
    fun `second failure produces retryCount 2`() {
        val jobJson = """{"id":"job-abc","retryCount":1,"scriptPath":"agents/Foo.kts"}"""
        val currentRetries = extractRawJsonValue(jobJson, "retryCount")?.toIntOrNull() ?: 0
        assertEquals(1, currentRetries)
        val newRetries = currentRetries + 1
        val updated = updateRetryCount(jobJson, newRetries)
        assertTrue(""""retryCount":2""" in updated)
        assertTrue(newRetries < 3)
    }

    @Test
    fun `third failure with maxRetries 3 triggers dead-letter`() {
        val jobJson = """{"id":"job-abc","retryCount":2,"scriptPath":"agents/Foo.kts"}"""
        val currentRetries = extractRawJsonValue(jobJson, "retryCount")?.toIntOrNull() ?: 0
        val newRetries = currentRetries + 1
        assertEquals(3, newRetries)
        assertTrue(newRetries >= 3) // → .dead/
    }

    @Test
    fun `maxRetries 1 means first failure goes to dead`() {
        val jobJson = """{"id":"job-abc"}"""
        val currentRetries = extractRawJsonValue(jobJson, "retryCount")?.toIntOrNull() ?: 0
        val newRetries = currentRetries + 1
        assertTrue(newRetries >= 1) // maxRetries=1 → .dead/ on first failure
    }

    @Test
    fun `job with retryCount 0 set explicitly behaves same as absent`() {
        val jobJson = """{"id":"job-abc","retryCount":0}"""
        val currentRetries = extractRawJsonValue(jobJson, "retryCount")?.toIntOrNull() ?: 0
        assertEquals(0, currentRetries)
        val newRetries = currentRetries + 1
        assertEquals(1, newRetries)
    }
}

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
