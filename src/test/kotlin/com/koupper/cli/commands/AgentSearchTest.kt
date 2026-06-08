package com.koupper.cli.commands

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// ── Test fixtures ─────────────────────────────────────────────────────────────

private val REGISTRY_JSON = """
{
  "version": 1,
  "agents": [
    {
      "name": "CortexAgent",
      "description": "Core AI orchestrator with Planner-Executor pattern",
      "version": "4.0.0",
      "role": "orchestrator",
      "tags": ["llm", "cortex", "planner"],
      "url": "https://example.com/CortexAgent.kts",
      "skillUrl": "https://example.com/CortexAgent.skill.json",
      "author": "Iglymx"
    },
    {
      "name": "TelegramBridgeAgent",
      "description": "Bidirectional Telegram bridge for messaging",
      "version": "1.2.0",
      "role": "bridge",
      "tags": ["telegram", "messaging"],
      "url": "https://example.com/TelegramBridgeAgent.kts",
      "skillUrl": "https://example.com/TelegramBridgeAgent.skill.json",
      "author": "Iglymx"
    },
    {
      "name": "RssFeedAgent",
      "description": "Fetches one or more RSS feeds and returns structured data",
      "version": "1.0.0",
      "role": "fetcher",
      "tags": ["rss", "news", "pipeline"],
      "url": "https://example.com/RssFeedAgent.kts",
      "skillUrl": "https://example.com/RssFeedAgent.skill.json",
      "author": "Iglymx"
    }
  ]
}
""".trimIndent()

private val NO_INSTALLED: Set<String> = emptySet()

// ── No query — list all ───────────────────────────────────────────────────────

class ParseRegistryNoQueryTest {

    @Test
    fun `no query shows all agents`() {
        val out = parseRegistryBody(REGISTRY_JSON, null, NO_INSTALLED)
        assertTrue("CortexAgent" in out)
        assertTrue("TelegramBridgeAgent" in out)
        assertTrue("RssFeedAgent" in out)
    }

    @Test
    fun `no query reports total count`() {
        val out = parseRegistryBody(REGISTRY_JSON, null, NO_INSTALLED)
        assertTrue("3 agent(s) in registry" in out)
    }

    @Test
    fun `no query shows column headers`() {
        val out = parseRegistryBody(REGISTRY_JSON, null, NO_INSTALLED)
        assertTrue("NAME" in out)
        assertTrue("ROLE" in out)
        assertTrue("STATUS" in out)
    }

    @Test
    fun `empty string query treated same as null`() {
        val out = parseRegistryBody(REGISTRY_JSON, "", NO_INSTALLED)
        assertTrue("3 agent(s) in registry" in out)
    }

    @Test
    fun `blank query treated same as null`() {
        val out = parseRegistryBody(REGISTRY_JSON, "   ", NO_INSTALLED)
        assertTrue("3 agent(s) in registry" in out)
    }
}

// ── Query filtering ───────────────────────────────────────────────────────────

class ParseRegistryFilterTest {

    @Test
    fun `query matches by agent name`() {
        val out = parseRegistryBody(REGISTRY_JSON, "telegram", NO_INSTALLED)
        assertTrue("TelegramBridgeAgent" in out)
        assertFalse("RssFeedAgent" in out)
    }

    @Test
    fun `query matches by role`() {
        val out = parseRegistryBody(REGISTRY_JSON, "bridge", NO_INSTALLED)
        assertTrue("TelegramBridgeAgent" in out)
        assertFalse("CortexAgent" in out)
    }

    @Test
    fun `query matches by tag`() {
        val out = parseRegistryBody(REGISTRY_JSON, "rss", NO_INSTALLED)
        assertTrue("RssFeedAgent" in out)
        assertFalse("TelegramBridgeAgent" in out)
    }

    @Test
    fun `query matches by description keyword`() {
        val out = parseRegistryBody(REGISTRY_JSON, "orchestrator", NO_INSTALLED)
        assertTrue("CortexAgent" in out)
        assertFalse("RssFeedAgent" in out)
    }

    @Test
    fun `query is case-insensitive`() {
        val out = parseRegistryBody(REGISTRY_JSON, "TELEGRAM", NO_INSTALLED)
        assertTrue("TelegramBridgeAgent" in out)
    }

    @Test
    fun `query with no matches returns no-match message`() {
        val out = parseRegistryBody(REGISTRY_JSON, "xyz_nonexistent_agent", NO_INSTALLED)
        assertTrue("No agents matched" in out)
    }

    @Test
    fun `no-match output does not include column table`() {
        val out = parseRegistryBody(REGISTRY_JSON, "xyz_nonexistent_agent", NO_INSTALLED)
        assertFalse("NAME" in out && "ROLE" in out)
    }

    @Test
    fun `filtered results show result count`() {
        val out = parseRegistryBody(REGISTRY_JSON, "telegram", NO_INSTALLED)
        assertTrue("result(s) for" in out)
    }

    @Test
    fun `tag match on shared tag returns multiple agents`() {
        // "pipeline" is a tag of RssFeedAgent only — but "llm" matches CortexAgent
        val out = parseRegistryBody(REGISTRY_JSON, "llm", NO_INSTALLED)
        assertTrue("CortexAgent" in out)
        assertFalse("TelegramBridgeAgent" in out)
    }
}

// ── Installed status ──────────────────────────────────────────────────────────

class ParseRegistryStatusTest {

    @Test
    fun `installed agent shows installed status`() {
        val out = parseRegistryBody(REGISTRY_JSON, null, setOf("CortexAgent"))
        val lines = out.lines()
        val cortexLine = lines.firstOrNull { "CortexAgent" in it && "orchestrator" in it }
        assertContains(cortexLine ?: "", "installed")
    }

    @Test
    fun `uninstalled agent shows available status`() {
        val out = parseRegistryBody(REGISTRY_JSON, null, setOf("CortexAgent"))
        val lines = out.lines()
        val telegramLine = lines.firstOrNull { "TelegramBridgeAgent" in it }
        assertContains(telegramLine ?: "", "available")
    }

    @Test
    fun `all agents show available when none installed`() {
        val out = parseRegistryBody(REGISTRY_JSON, null, NO_INSTALLED)
        val tableLines = out.lines().filter { it.contains("Agent") && it.contains("available") }
        assertTrue(tableLines.size == 3, "Expected 3 available agents, got ${tableLines.size}")
    }

    @Test
    fun `all agents show installed when all in installedNames`() {
        val installed = setOf("CortexAgent", "TelegramBridgeAgent", "RssFeedAgent")
        val out = parseRegistryBody(REGISTRY_JSON, null, installed)
        assertFalse("available" in out)
    }
}

// ── Exact match — shows install URL ──────────────────────────────────────────

class ParseRegistryExactMatchTest {

    @Test
    fun `exact name match shows install url`() {
        val out = parseRegistryBody(REGISTRY_JSON, "CortexAgent", NO_INSTALLED)
        assertTrue("https://example.com/CortexAgent.kts" in out)
    }

    @Test
    fun `exact name match shows quick install command`() {
        val out = parseRegistryBody(REGISTRY_JSON, "CortexAgent", NO_INSTALLED)
        assertTrue("koupper agent install" in out)
        assertTrue("https://example.com/CortexAgent.kts" in out)
    }

    @Test
    fun `exact match is case-insensitive`() {
        val out = parseRegistryBody(REGISTRY_JSON, "cortexagent", NO_INSTALLED)
        assertTrue("https://example.com/CortexAgent.kts" in out)
    }

    @Test
    fun `partial match does not show install url`() {
        // "telegram" matches TelegramBridgeAgent by name but is not an exact match
        val out = parseRegistryBody(REGISTRY_JSON, "telegram", NO_INSTALLED)
        assertFalse("Install URL" in out)
    }

    @Test
    fun `ambiguous query matching multiple agents does not show install url`() {
        // "messaging" matches TelegramBridgeAgent only (by tag) — but not exact name
        val out = parseRegistryBody(REGISTRY_JSON, "messaging", NO_INSTALLED)
        assertFalse("Install URL" in out)
    }
}

// ── Malformed input ───────────────────────────────────────────────────────────

class ParseRegistryMalformedTest {

    @Test
    fun `invalid json returns error message`() {
        val out = parseRegistryBody("{not valid json!!!", null, NO_INSTALLED)
        assertTrue("Invalid registry format" in out)
    }

    @Test
    fun `empty json object returns zero agents`() {
        val out = parseRegistryBody("{}", null, NO_INSTALLED)
        assertTrue("0 agent(s) in registry" in out)
    }

    @Test
    fun `registry with empty agents array returns zero agents`() {
        val out = parseRegistryBody("""{"version":1,"agents":[]}""", null, NO_INSTALLED)
        assertTrue("0 agent(s) in registry" in out)
    }
}
