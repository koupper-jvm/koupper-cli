package com.koupper.cli.commands

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// All tests use an isolated temp agentsDir — no real ~/.koupper/agents is touched.

private fun tempAgentsDir(): File =
    Files.createTempDirectory("agent-cmd-test").toFile().also { it.deleteOnExit() }

private fun writeSkill(dir: File, name: String, content: String = defaultSkill(name)) =
    File(dir, "$name.skill.json").writeText(content)

private fun writeAgent(dir: File, name: String) =
    File(dir, "$name.kts").writeText("// $name stub")

private fun defaultSkill(name: String) = """
{
  "name": "$name",
  "version": "1.2.3",
  "description": "Does $name things",
  "role": "worker",
  "persistent": false,
  "triggers": ["manual"],
  "providers": ["FileHandler"],
  "tags": ["cortex"],
  "envVars": [
    {"name": "FOO_TOKEN", "required": true, "description": "API token"},
    {"name": "FOO_LANG",  "required": false, "description": "Language"}
  ]
}
""".trimIndent()

// ── list ──────────────────────────────────────────────────────────────────────

class AgentListTest {

    @Test
    fun `list on empty dir reports no agents installed`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "list")
        assertTrue("No agents installed" in out)
    }

    @Test
    fun `list on nonexistent dir reports no agents installed`() {
        val dir = File(tempAgentsDir(), "missing")
        val out = AgentCommand(dir).execute("agent", "list")
        assertTrue("No agents installed" in out)
    }

    @Test
    fun `list shows agents that have a skill json`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "RssFeedAgent")
        writeSkill(dir, "RssFeedAgent")
        writeAgent(dir, "SummarizerAgent")
        writeSkill(dir, "SummarizerAgent")

        val out = AgentCommand(dir).execute("agent", "list")

        assertTrue("RssFeedAgent"   in out)
        assertTrue("SummarizerAgent" in out)
        assertTrue("1.2.3" in out)
    }

    @Test
    fun `list skips kts files without a matching skill json`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "UnknownAgent")      // no skill.json
        writeAgent(dir, "KnownAgent")
        writeSkill(dir, "KnownAgent")

        val out = AgentCommand(dir).execute("agent", "list")

        assertFalse("UnknownAgent" in out)
        assertTrue("KnownAgent" in out)
    }

    @Test
    fun `list shows count of agents`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "AgentA"); writeSkill(dir, "AgentA")
        writeAgent(dir, "AgentB"); writeSkill(dir, "AgentB")
        writeAgent(dir, "AgentC"); writeSkill(dir, "AgentC")

        val out = AgentCommand(dir).execute("agent", "list")

        assertTrue("3 agent(s)" in out)
    }

    @Test
    fun `list marks persistent agents`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "DaemonAgent")
        File(dir, "DaemonAgent.skill.json").writeText("""
            {"name":"DaemonAgent","version":"1.0.0","description":"d","persistent":true}
        """.trimIndent())

        val out = AgentCommand(dir).execute("agent", "list")

        assertTrue("yes" in out)
    }
}

// ── info ──────────────────────────────────────────────────────────────────────

class AgentInfoTest {

    @Test
    fun `info with no name returns usage hint`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "info")
        assertTrue("Usage" in out || "usage" in out)
    }

    @Test
    fun `info for unknown agent returns not found message`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "info", "Ghost")
        assertTrue("not found" in out.lowercase())
    }

    @Test
    fun `info shows description role and version`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "RssFeedAgent")
        writeSkill(dir, "RssFeedAgent")

        val out = AgentCommand(dir).execute("agent", "info", "RssFeedAgent")

        assertTrue("Does RssFeedAgent things" in out)
        assertTrue("1.2.3" in out)
        assertTrue("worker" in out)
    }

    @Test
    fun `info shows env vars with required flag`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "RssFeedAgent")
        writeSkill(dir, "RssFeedAgent")

        val out = AgentCommand(dir).execute("agent", "info", "RssFeedAgent")

        assertTrue("FOO_TOKEN" in out)
        assertTrue("required"  in out)
        assertTrue("FOO_LANG"  in out)
        assertTrue("optional"  in out)
    }

    @Test
    fun `info shows triggers and providers`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "RssFeedAgent")
        writeSkill(dir, "RssFeedAgent")

        val out = AgentCommand(dir).execute("agent", "info", "RssFeedAgent")

        assertTrue("manual"      in out)
        assertTrue("FileHandler" in out)
    }

    @Test
    fun `info lookup is case-insensitive`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "RssFeedAgent")
        writeSkill(dir, "RssFeedAgent")

        val out = AgentCommand(dir).execute("agent", "info", "rssfeedagent")

        assertTrue("RssFeedAgent" in out)
        assertFalse("not found" in out.lowercase())
    }

    @Test
    fun `info includes run command hint`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "RssFeedAgent")
        writeSkill(dir, "RssFeedAgent")

        val out = AgentCommand(dir).execute("agent", "info", "RssFeedAgent")

        assertTrue("koupper run" in out)
    }
}

// ── remove ────────────────────────────────────────────────────────────────────

class AgentRemoveTest {

    @Test
    fun `remove with no name returns usage hint`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "remove")
        assertTrue("Usage" in out || "usage" in out)
    }

    @Test
    fun `remove unknown agent returns not found error`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "remove", "Ghost")
        assertTrue("not found" in out.lowercase())
    }

    @Test
    fun `remove deletes kts file`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "TempAgent")
        writeSkill(dir, "TempAgent")

        AgentCommand(dir).execute("agent", "remove", "TempAgent")

        assertFalse(File(dir, "TempAgent.kts").exists())
    }

    @Test
    fun `remove also deletes skill json`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "TempAgent")
        writeSkill(dir, "TempAgent")

        AgentCommand(dir).execute("agent", "remove", "TempAgent")

        assertFalse(File(dir, "TempAgent.skill.json").exists())
    }

    @Test
    fun `remove accepts name with kts extension`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "TempAgent")

        val out = AgentCommand(dir).execute("agent", "remove", "TempAgent.kts")

        assertFalse(File(dir, "TempAgent.kts").exists())
        assertTrue("Removed" in out || "removed" in out)
    }

    @Test
    fun `remove returns success message`() {
        val dir = tempAgentsDir()
        writeAgent(dir, "TempAgent")

        val out = AgentCommand(dir).execute("agent", "remove", "TempAgent")

        assertTrue("Removed" in out || "removed" in out || "✓" in out)
    }
}

// ── install (offline paths) ───────────────────────────────────────────────────

class AgentInstallOfflineTest {

    @Test
    fun `install with no target returns usage`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "install")
        assertTrue("Usage" in out || "usage" in out || "koupper agent install" in out)
    }

    @Test
    fun `install rejects non-kts url`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "install", "https://example.com/agent.zip")
        assertTrue(".kts" in out)
    }

    @Test
    fun `install rejects unknown scheme`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "install", "ftp://example.com/Agent.kts")
        assertTrue("Cannot resolve" in out || "resolve" in out.lowercase())
    }
}

// ── routing ───────────────────────────────────────────────────────────────────

class AgentCommandRoutingTest {

    @Test
    fun `no subcommand returns usage text`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent")
        assertTrue("koupper agent" in out)
        assertContains(out, "list")
        assertContains(out, "install")
    }

    @Test
    fun `unknown subcommand returns error`() {
        val dir = tempAgentsDir()
        val out = AgentCommand(dir).execute("agent", "frobnicate")
        assertTrue("Unknown" in out || "unknown" in out)
    }
}
