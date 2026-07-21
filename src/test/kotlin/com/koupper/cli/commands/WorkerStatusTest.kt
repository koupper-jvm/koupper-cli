package com.koupper.cli.commands

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class WorkerStatusTest {

    @Test
    fun `worker --status reports queue snapshot without starting daemon`() {
        val jobsDir = createTempDir(prefix = "koupper-jobs-")
        try {
            val q = File(jobsDir, "emails").also { it.mkdirs() }
            File(q, "job-1.json").writeText("""{"id":"job-1"}""")
            File(q, ".failed").also { it.mkdirs() }
            File(q, ".dead").also { it.mkdirs() }.let { File(it, "old.json").writeText("{}") }

            val output = WorkerCommand().execute(".", jobsDir.absolutePath, "--status")

            assertTrue(output.contains("KOUPPER WORKER STATUS"))
            assertTrue(output.contains("emails"))
            assertTrue(output.contains("1p"))
            assertTrue(output.contains("1☠") || output.contains("dead"))
        } finally {
            jobsDir.deleteRecursively()
        }
    }
}
