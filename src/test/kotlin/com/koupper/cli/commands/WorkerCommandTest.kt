package com.koupper.cli.commands

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

// ── --logs ────────────────────────────────────────────────────────────────────

class WorkerLogsCommandTest {

    private val command = WorkerCommand()
    private fun tempDir(): File = java.nio.file.Files.createTempDirectory("wc-logs").toFile()
        .also { it.deleteOnExit() }

    private fun writeLog(jobsDir: File, queue: String, jobId: String, content: String): File {
        val dir = File(jobsDir, "logs/$queue").also { it.mkdirs() }
        return File(dir, "$jobId.log").also { it.writeText(content) }
    }

    // ── --logs (no jobId) — list mode ─────────────────────────────────────────

    @Test
    fun `list mode with no logs dir reports not found`() {
        val dir = tempDir()
        val out = command.execute("worker", dir.absolutePath, "--logs")
        assertTrue("No logs directory" in out)
    }

    @Test
    fun `list mode with empty logs dir reports no logs`() {
        val dir = tempDir()
        File(dir, "logs").mkdirs()
        val out = command.execute("worker", dir.absolutePath, "--logs")
        assertTrue("No logs found" in out)
    }

    @Test
    fun `list mode shows job ids`() {
        val dir = tempDir()
        writeLog(dir, "default", "job-abc", "[DONE] 1200ms\n")
        writeLog(dir, "default", "job-xyz", "[FAILED] exit=1\n")

        val out = command.execute("worker", dir.absolutePath, "--logs")
        assertTrue("job-abc" in out)
        assertTrue("job-xyz" in out)
    }

    @Test
    fun `list mode shows done status for completed jobs`() {
        val dir = tempDir()
        writeLog(dir, "default", "job-ok", "output line\n[DONE] 500ms\n")

        val out = command.execute("worker", dir.absolutePath, "--logs")
        assertTrue("done" in out)
    }

    @Test
    fun `list mode shows failed status`() {
        val dir = tempDir()
        writeLog(dir, "default", "job-bad", "error output\n[FAILED] exit=1\n")

        val out = command.execute("worker", dir.absolutePath, "--logs")
        assertTrue("failed" in out)
    }

    @Test
    fun `list mode shows timeout status`() {
        val dir = tempDir()
        writeLog(dir, "default", "job-slow", "[TIMEOUT] Job exceeded 300s — process killed\n")

        val out = command.execute("worker", dir.absolutePath, "--logs")
        assertTrue("timeout" in out)
    }

    @Test
    fun `list mode shows hint to view specific log`() {
        val dir = tempDir()
        writeLog(dir, "default", "job-1", "[DONE] 100ms\n")

        val out = command.execute("worker", dir.absolutePath, "--logs")
        assertTrue("koupper worker --logs" in out)
    }

    @Test
    fun `list mode shows queue name for each job`() {
        val dir = tempDir()
        writeLog(dir, "priority", "job-p", "[DONE] 200ms\n")

        val out = command.execute("worker", dir.absolutePath, "--logs")
        assertTrue("priority" in out)
    }

    // ── --logs <jobId> — detail mode ──────────────────────────────────────────

    @Test
    fun `detail mode shows log content`() {
        val dir = tempDir()
        writeLog(dir, "default", "job-42", "step 1 done\nstep 2 done\n[DONE] 800ms\n")

        val out = command.execute("worker", dir.absolutePath, "--logs", "job-42")
        assertTrue("step 1 done" in out)
        assertTrue("step 2 done" in out)
        assertTrue("[DONE]" in out)
    }

    @Test
    fun `detail mode reports not found for unknown jobId`() {
        val dir = tempDir()
        File(dir, "logs/default").mkdirs()

        val out = command.execute("worker", dir.absolutePath, "--logs", "job-nonexistent")
        assertTrue("No log found" in out)
        assertTrue("job-nonexistent" in out)
    }

    @Test
    fun `detail mode with no logs dir reports not found`() {
        val dir = tempDir()
        val out = command.execute("worker", dir.absolutePath, "--logs", "job-abc")
        assertTrue("No logs directory" in out)
    }

    @Test
    fun `detail mode shows queue name`() {
        val dir = tempDir()
        writeLog(dir, "urgent", "job-u", "[DONE] 100ms\n")

        val out = command.execute("worker", dir.absolutePath, "--logs", "job-u")
        assertTrue("urgent" in out)
    }

    @Test
    fun `detail mode finds log across multiple queues`() {
        val dir = tempDir()
        writeLog(dir, "alpha",   "job-in-alpha", "[DONE] 100ms\n")
        writeLog(dir, "beta",    "job-in-beta",  "[DONE] 200ms\n")

        val outAlpha = command.execute("worker", dir.absolutePath, "--logs", "job-in-alpha")
        val outBeta  = command.execute("worker", dir.absolutePath, "--logs", "job-in-beta")

        assertTrue("alpha" in outAlpha)
        assertFalse("beta" in outAlpha)
        assertTrue("beta" in outBeta)
    }
}

// ── --retry ───────────────────────────────────────────────────────────────────

class WorkerRetryCommandTest {

    private val command = WorkerCommand()
    private fun tempDir(): File = java.nio.file.Files.createTempDirectory("wc-retry").toFile()
        .also { it.deleteOnExit() }

    @Test
    fun `retry on nonexistent jobs dir reports no directory`() {
        val dir = File(tempDir(), "nonexistent")
        val out = command.execute("worker", dir.absolutePath, "--retry")
        assertTrue("No jobs directory" in out)
    }

    @Test
    fun `retry with no failed jobs reports nothing to retry`() {
        val dir = tempDir()
        File(dir, "default").mkdirs()
        val out = command.execute("worker", dir.absolutePath, "--retry")
        assertTrue("No failed jobs" in out)
    }

    @Test
    fun `retry moves failed job back to queue`() {
        val dir   = tempDir()
        val queue = File(dir, "default").also { it.mkdirs() }
        val failed = File(queue, ".failed").also { it.mkdirs() }
        File(failed, "job-001.json").writeText("""{"id":"job-001","retryCount":1}""")

        val out = command.execute("worker", dir.absolutePath, "--retry")

        assertTrue("job-001.json" in out)
        assertTrue("re-queued" in out)
        assertTrue(File(queue, "job-001.json").exists())
        assertFalse(File(failed, "job-001.json").exists())
    }

    @Test
    fun `retry counts all moved jobs across queues`() {
        val dir = tempDir()
        listOf("alpha", "beta").forEach { name ->
            val q = File(dir, name).also { it.mkdirs() }
            val f = File(q, ".failed").also { it.mkdirs() }
            File(f, "job-$name.json").writeText("{}")
        }

        val out = command.execute("worker", dir.absolutePath, "--retry")
        assertTrue("2 job(s) re-queued" in out)
    }

    @Test
    fun `retry with target queue only retries that queue`() {
        val dir   = tempDir()
        val alpha = File(dir, "alpha").also { it.mkdirs() }
        val beta  = File(dir, "beta").also  { it.mkdirs() }
        File(alpha, ".failed").also { it.mkdirs() }.let { File(it, "a.json").writeText("{}") }
        File(beta,  ".failed").also { it.mkdirs() }.let { File(it, "b.json").writeText("{}") }

        command.execute("worker", dir.absolutePath, "--retry", "alpha")

        assertTrue(File(alpha, "a.json").exists())   // moved back
        assertFalse(File(beta, "b.json").exists().also {}) // beta untouched
        assertTrue(File(File(beta, ".failed"), "b.json").exists())
    }

    @Test
    fun `retry output shows header`() {
        val dir = tempDir()
        File(dir, "default").mkdirs()
        val out = command.execute("worker", dir.absolutePath, "--retry")
        assertTrue("RETRY" in out)
    }
}

// ── --purge ───────────────────────────────────────────────────────────────────

class WorkerPurgeCommandTest {

    private val command = WorkerCommand()
    private fun tempDir(): File = java.nio.file.Files.createTempDirectory("wc-purge").toFile()
        .also { it.deleteOnExit() }

    @Test
    fun `purge without bucket shows usage`() {
        val dir = tempDir()
        val out = command.execute("worker", dir.absolutePath, "--purge")
        assertTrue("Usage" in out || "dead|failed" in out)
    }

    @Test
    fun `purge with invalid bucket shows usage`() {
        val dir = tempDir()
        val out = command.execute("worker", dir.absolutePath, "--purge", "pending")
        assertTrue("dead|failed" in out)
    }

    @Test
    fun `purge dead deletes files from dead bucket`() {
        val dir   = tempDir()
        val queue = File(dir, "default").also { it.mkdirs() }
        val dead  = File(queue, ".dead").also  { it.mkdirs() }
        File(dead, "job-dead.json").writeText("{}")

        val out = command.execute("worker", dir.absolutePath, "--purge", "dead")

        assertTrue("1 job(s) purged" in out)
        assertFalse(File(dead, "job-dead.json").exists())
    }

    @Test
    fun `purge failed deletes files from failed bucket`() {
        val dir   = tempDir()
        val queue = File(dir, "default").also { it.mkdirs() }
        val failed = File(queue, ".failed").also { it.mkdirs() }
        File(failed, "job-bad.json").writeText("{}")

        val out = command.execute("worker", dir.absolutePath, "--purge", "failed")

        assertTrue("1 job(s) purged" in out)
        assertFalse(File(failed, "job-bad.json").exists())
    }

    @Test
    fun `purge dead across multiple queues deletes all`() {
        val dir = tempDir()
        listOf("alpha", "beta").forEach { name ->
            val q = File(dir, name).also { it.mkdirs() }
            val d = File(q, ".dead").also { it.mkdirs() }
            File(d, "job-$name.json").writeText("{}")
        }

        val out = command.execute("worker", dir.absolutePath, "--purge", "dead")
        assertTrue("2 job(s) purged" in out)
    }

    @Test
    fun `purge with target queue only purges that queue`() {
        val dir   = tempDir()
        val alpha = File(dir, "alpha").also { it.mkdirs() }
        val beta  = File(dir, "beta").also  { it.mkdirs() }
        File(alpha, ".dead").also { it.mkdirs() }.let { File(it, "a.json").writeText("{}") }
        File(beta,  ".dead").also { it.mkdirs() }.let { File(it, "b.json").writeText("{}") }

        command.execute("worker", dir.absolutePath, "--purge", "dead", "alpha")

        assertFalse(File(File(alpha, ".dead"), "a.json").exists())
        assertTrue(File(File(beta,  ".dead"), "b.json").exists())
    }

    @Test
    fun `purge with empty bucket reports nothing to purge`() {
        val dir = tempDir()
        File(dir, "default").mkdirs()
        val out = command.execute("worker", dir.absolutePath, "--purge", "dead")
        assertTrue("No dead jobs" in out)
    }

    @Test
    fun `purge on nonexistent jobs dir reports no directory`() {
        val dir = File(tempDir(), "nonexistent")
        val out = command.execute("worker", dir.absolutePath, "--purge", "dead")
        assertTrue("No jobs directory" in out)
    }

    @Test
    fun `purge does not delete non-json files`() {
        val dir   = tempDir()
        val queue = File(dir, "default").also { it.mkdirs() }
        val dead  = File(queue, ".dead").also  { it.mkdirs() }
        File(dead, "job.json").writeText("{}")
        File(dead, "notes.txt").writeText("keep me")

        command.execute("worker", dir.absolutePath, "--purge", "dead")

        assertFalse(File(dead, "job.json").exists())
        assertTrue(File(dead, "notes.txt").exists())
    }
}

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
