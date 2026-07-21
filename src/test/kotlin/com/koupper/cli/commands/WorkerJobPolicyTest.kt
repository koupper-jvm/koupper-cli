package com.koupper.cli.commands

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkerJobPolicyTest {

    @Test
    fun `readAttempts defaults to zero when missing`() {
        assertEquals(0, WorkerJobPolicy.readAttempts("""{"id":"job-1","scriptPath":"a.kts"}"""))
    }

    @Test
    fun `readAttempts parses existing field`() {
        assertEquals(2, WorkerJobPolicy.readAttempts("""{"id":"job-1","attempts":2}"""))
    }

    @Test
    fun `withAttempts inserts field when missing`() {
        val updated = WorkerJobPolicy.withAttempts("""{"id":"job-1"}""", 1)
        assertEquals(1, WorkerJobPolicy.readAttempts(updated))
        assertTrue(updated.contains("\"id\":\"job-1\""))
    }

    @Test
    fun `withAttempts overwrites existing field`() {
        val updated = WorkerJobPolicy.withAttempts("""{"attempts":1,"id":"job-1"}""", 3)
        assertEquals(3, WorkerJobPolicy.readAttempts(updated))
    }

    @Test
    fun `shouldDeadLetter after max retries`() {
        assertFalse(WorkerJobPolicy.shouldDeadLetter(1, 3))
        assertFalse(WorkerJobPolicy.shouldDeadLetter(2, 3))
        assertTrue(WorkerJobPolicy.shouldDeadLetter(3, 3))
        assertTrue(WorkerJobPolicy.shouldDeadLetter(4, 3))
    }

    @Test
    fun `countQueue tallies pending processing failed and dead`() {
        val root = createTempDir(prefix = "koupper-worker-policy-")
        try {
            val q = File(root, "default").also { it.mkdirs() }
            File(q, "a.json").writeText("{}")
            File(q, "b.json.processing").writeText("{}")
            File(q, ".failed").also { it.mkdirs() }.let { File(it, "c.json").writeText("{}") }
            File(q, ".dead").also { it.mkdirs() }.let { File(it, "d.json").writeText("{}") }

            val counts = WorkerJobPolicy.countQueue(q)
            assertEquals("default", counts.name)
            assertEquals(1, counts.pending)
            assertEquals(1, counts.processing)
            assertEquals(1, counts.failed)
            assertEquals(1, counts.dead)
        } finally {
            root.deleteRecursively()
        }
    }
}
