package com.koupper.cli.commands

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ── parseStepObjects ──────────────────────────────────────────────────────────

class ParseStepObjectsTest {

    @Test
    fun `returns empty list for empty array`() {
        assertEquals(emptyList(), parseStepObjects("[]"))
    }

    @Test
    fun `returns empty list for non-array input`() {
        assertEquals(emptyList(), parseStepObjects("{}"))
    }

    @Test
    fun `parses single step with agent only`() {
        val steps = parseStepObjects("""[{"agent":"agents/MyAgent.kts"}]""")
        assertEquals(1, steps.size)
        assertEquals("agents/MyAgent.kts", steps[0].agent)
        assertEquals(null, steps[0].input)
        assertEquals(null, steps[0].env)
    }

    @Test
    fun `parses step with input object`() {
        val steps = parseStepObjects("""[{"agent":"agents/A.kts","input":{"key":"val"}}]""")
        assertEquals("""{"key":"val"}""", steps[0].input)
    }

    @Test
    fun `parses step with env object`() {
        val steps = parseStepObjects("""[{"agent":"agents/A.kts","env":{"FOO":"bar"}}]""")
        assertEquals("""{"FOO":"bar"}""", steps[0].env)
    }

    @Test
    fun `parses multiple steps`() {
        val steps = parseStepObjects("""[
          {"agent":"agents/Step1.kts"},
          {"agent":"agents/Step2.kts","input":{"x":1}},
          {"agent":"agents/Step3.kts"}
        ]""")
        assertEquals(3, steps.size)
        assertEquals("agents/Step1.kts", steps[0].agent)
        assertEquals("agents/Step2.kts", steps[1].agent)
        assertEquals("""{"x":1}""", steps[1].input)
        assertEquals("agents/Step3.kts", steps[2].agent)
    }

    @Test
    fun `skips steps without agent field`() {
        val steps = parseStepObjects("""[{"queue":"default"},{"agent":"agents/A.kts"}]""")
        assertEquals(1, steps.size)
        assertEquals("agents/A.kts", steps[0].agent)
    }

    @Test
    fun `null input is treated as absent`() {
        val steps = parseStepObjects("""[{"agent":"agents/A.kts","input":null}]""")
        assertEquals(null, steps[0].input)
    }
}

// ── buildStepNextJson ─────────────────────────────────────────────────────────

class BuildStepNextJsonTest {

    @Test
    fun `builds minimal next object`() {
        val step = PipelineStep("agents/A.kts", null, null)
        val json = buildStepNextJson(step, null)
        assertEquals("""{"scriptPath":"agents/A.kts"}""", json)
    }

    @Test
    fun `includes input when present`() {
        val step = PipelineStep("agents/A.kts", """{"n":1}""", null)
        val json = buildStepNextJson(step, null)
        assertTrue(""""input":{"n":1}""" in json)
    }

    @Test
    fun `includes nested pipelineNext`() {
        val step = PipelineStep("agents/B.kts", null, null)
        val json = buildStepNextJson(step, """{"scriptPath":"agents/C.kts"}""")
        assertTrue(""""pipelineNext":{"scriptPath":"agents/C.kts"}""" in json)
    }

    @Test
    fun `includes env when present`() {
        val step = PipelineStep("agents/A.kts", null, """{"TOKEN":"abc"}""")
        val json = buildStepNextJson(step, null)
        assertTrue(""""env":{"TOKEN":"abc"}""" in json)
    }
}

// ── buildFirstJobJson ─────────────────────────────────────────────────────────

class BuildFirstJobJsonTest {

    @Test
    fun `contains required fields`() {
        val step = PipelineStep("agents/Start.kts", null, null)
        val json = buildFirstJobJson("pipe-step0", step, "pipe", 3, null)
        assertTrue(""""id":"pipe-step0"""" in json)
        assertTrue(""""scriptPath":"agents/Start.kts"""" in json)
        assertTrue(""""pipelineId":"pipe"""" in json)
        assertTrue(""""pipelineStep":0""" in json)
        assertTrue(""""pipelineTotal":3""" in json)
    }

    @Test
    fun `includes pipelineNext when provided`() {
        val step = PipelineStep("agents/Start.kts", null, null)
        val json = buildFirstJobJson("p-step0", step, "p", 2, """{"scriptPath":"agents/End.kts"}""")
        assertTrue(""""pipelineNext":{"scriptPath":"agents/End.kts"}""" in json)
    }

    @Test
    fun `omits pipelineNext when null`() {
        val step = PipelineStep("agents/OnlyStep.kts", null, null)
        val json = buildFirstJobJson("p-step0", step, "p", 1, null)
        assertFalse("pipelineNext" in json)
    }

    @Test
    fun `derives fileName from agent path`() {
        val step = PipelineStep("agents/FetchData.kts", null, null)
        val json = buildFirstJobJson("p-step0", step, "p", 1, null)
        assertTrue(""""fileName":"FetchData"""" in json)
    }
}

// ── PipelineCommand submit ────────────────────────────────────────────────────

class PipelineSubmitTest {

    private val command = PipelineCommand()
    private fun tempDir(): File = Files.createTempDirectory("pipe-submit").toFile().also { it.deleteOnExit() }

    private fun writePipelineFile(dir: File, content: String): File =
        File(dir, "pipeline.json").also { it.writeText(content) }

    @Test
    fun `missing file returns error message`() {
        val dir = tempDir()
        val out = command.execute("pipeline", "submit", "/nonexistent/pipeline.json", "--jobs-dir=${dir.absolutePath}")
        assertTrue("not found" in out.lowercase())
    }

    @Test
    fun `missing steps returns error`() {
        val dir = tempDir()
        writePipelineFile(dir, """{"id":"p1","queue":"default"}""")
        val out = command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${dir.absolutePath}")
        assertTrue("steps" in out.lowercase())
    }

    @Test
    fun `empty steps array returns error`() {
        val dir = tempDir()
        writePipelineFile(dir, """{"id":"p1","queue":"default","steps":[]}""")
        val out = command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${dir.absolutePath}")
        assertTrue("No valid steps" in out)
    }

    @Test
    fun `single-step pipeline creates job file in queue dir`() {
        val dir    = tempDir()
        val jobs   = tempDir()
        writePipelineFile(dir, """{"id":"p-single","queue":"default","steps":[{"agent":"agents/A.kts"}]}""")
        command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${jobs.absolutePath}")
        assertTrue(File(jobs, "default/p-single-step0.json").exists())
    }

    @Test
    fun `job file contains pipelineId and pipelineTotal`() {
        val dir  = tempDir()
        val jobs = tempDir()
        writePipelineFile(dir, """{"id":"my-pipe","queue":"default","steps":[{"agent":"agents/A.kts"},{"agent":"agents/B.kts"}]}""")
        command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${jobs.absolutePath}")
        val content = File(jobs, "default/my-pipe-step0.json").readText()
        assertTrue(""""pipelineId":"my-pipe"""" in content)
        assertTrue(""""pipelineTotal":2""" in content)
    }

    @Test
    fun `two-step pipeline embeds pipelineNext in first job`() {
        val dir  = tempDir()
        val jobs = tempDir()
        writePipelineFile(dir, """{"id":"pipe2","queue":"q","steps":[
          {"agent":"agents/A.kts"},
          {"agent":"agents/B.kts"}
        ]}""")
        command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${jobs.absolutePath}")
        val content = File(jobs, "q/pipe2-step0.json").readText()
        assertTrue("pipelineNext" in content)
        assertTrue("agents/B.kts" in content)
    }

    @Test
    fun `three-step pipeline nests pipelineNext two levels deep`() {
        val dir  = tempDir()
        val jobs = tempDir()
        writePipelineFile(dir, """{"id":"pipe3","queue":"q","steps":[
          {"agent":"agents/A.kts"},
          {"agent":"agents/B.kts"},
          {"agent":"agents/C.kts"}
        ]}""")
        command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${jobs.absolutePath}")
        val content = File(jobs, "q/pipe3-step0.json").readText()
        assertTrue("agents/A.kts" in content)
        assertTrue("agents/B.kts" in content)
        assertTrue("agents/C.kts" in content)
    }

    @Test
    fun `step0 input is forwarded into job JSON`() {
        val dir  = tempDir()
        val jobs = tempDir()
        writePipelineFile(dir, """{"id":"p","queue":"q","steps":[{"agent":"agents/A.kts","input":{"n":42}}]}""")
        command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${jobs.absolutePath}")
        val content = File(jobs, "q/p-step0.json").readText()
        assertTrue(""""input":{"n":42}""" in content)
    }

    @Test
    fun `auto-generates id when absent`() {
        val dir  = tempDir()
        val jobs = tempDir()
        writePipelineFile(dir, """{"queue":"default","steps":[{"agent":"agents/A.kts"}]}""")
        val out = command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${jobs.absolutePath}")
        assertTrue("Enqueued" in out)
        val qDir = File(jobs, "default")
        assertTrue(qDir.listFiles()?.any { it.name.endsWith(".json") } == true)
    }

    @Test
    fun `output lists pending steps for multi-step pipeline`() {
        val dir  = tempDir()
        val jobs = tempDir()
        writePipelineFile(dir, """{"id":"multi","queue":"q","steps":[
          {"agent":"agents/A.kts"},
          {"agent":"agents/B.kts"},
          {"agent":"agents/C.kts"}
        ]}""")
        val out = command.execute("pipeline", "submit", File(dir, "pipeline.json").absolutePath, "--jobs-dir=${jobs.absolutePath}")
        assertTrue("Enqueued" in out)
        assertTrue("Pending" in out)
    }
}

// ── PipelineCommand status ────────────────────────────────────────────────────

class PipelineStatusTest {

    private val command = PipelineCommand()
    private fun tempDir(): File = Files.createTempDirectory("pipe-status").toFile().also { it.deleteOnExit() }

    @Test
    fun `missing jobs dir reports not found`() {
        val dir = tempDir()
        val out = command.execute("pipeline", "status", "my-pipe", "--jobs-dir=${File(dir, "nonexistent").absolutePath}")
        assertTrue("No jobs directory" in out)
    }

    @Test
    fun `no matching steps reports not found`() {
        val dir = tempDir()
        File(dir, "default").mkdirs()
        val out = command.execute("pipeline", "status", "ghost-pipe", "--jobs-dir=${dir.absolutePath}")
        assertTrue("No steps found" in out)
    }

    @Test
    fun `done step shows done status`() {
        val dir  = tempDir()
        val done = File(dir, "default/.done").also { it.mkdirs() }
        File(done, "my-pipe-step0.result.json").writeText("""{"id":"my-pipe-step0","result":"ok"}""")
        val out  = command.execute("pipeline", "status", "my-pipe", "--jobs-dir=${dir.absolutePath}")
        assertTrue("done" in out)
        assertTrue("step 0" in out)
    }

    @Test
    fun `failed step shows failed status`() {
        val dir    = tempDir()
        val failed = File(dir, "default/.failed").also { it.mkdirs() }
        File(failed, "p-step1.json").writeText("""{"id":"p-step1"}""")
        val out = command.execute("pipeline", "status", "p", "--jobs-dir=${dir.absolutePath}")
        assertTrue("failed" in out)
    }

    @Test
    fun `pending step shows pending status`() {
        val dir = tempDir()
        val q   = File(dir, "default").also { it.mkdirs() }
        File(q, "my-pipe-step0.json").writeText("""{"id":"my-pipe-step0"}""")
        val out = command.execute("pipeline", "status", "my-pipe", "--jobs-dir=${dir.absolutePath}")
        assertTrue("pending" in out)
    }

    @Test
    fun `running step shows running status`() {
        val dir = tempDir()
        val q   = File(dir, "default").also { it.mkdirs() }
        File(q, "p-step0.json.processing").writeText("""{"id":"p-step0"}""")
        val out = command.execute("pipeline", "status", "p", "--jobs-dir=${dir.absolutePath}")
        assertTrue("running" in out)
    }

    @Test
    fun `progress line shows correct done count`() {
        val dir  = tempDir()
        val done = File(dir, "default/.done").also { it.mkdirs() }
        File(done, "p-step0.result.json").writeText("""{"id":"p-step0","result":"x"}""")
        val q = File(dir, "default").also { it.mkdirs() }
        File(q, "p-step1.json").writeText("""{"id":"p-step1"}""")
        val out = command.execute("pipeline", "status", "p", "--jobs-dir=${dir.absolutePath}")
        assertTrue("1 / 2" in out)
    }
}

// ── PipelineCommand routing ───────────────────────────────────────────────────

class PipelineCommandRoutingTest {

    private val command = PipelineCommand()

    @Test
    fun `no subcommand returns usage`() {
        val out = command.execute("pipeline")
        assertTrue("submit" in out && "status" in out)
    }

    @Test
    fun `unknown subcommand returns usage`() {
        val out = command.execute("pipeline", "foobar")
        assertTrue("submit" in out)
    }

    @Test
    fun `submit without file returns error`() {
        val out = command.execute("pipeline", "submit")
        assertTrue("required" in out.lowercase())
    }

    @Test
    fun `status without id returns error`() {
        val out = command.execute("pipeline", "status")
        assertTrue("required" in out.lowercase())
    }
}
