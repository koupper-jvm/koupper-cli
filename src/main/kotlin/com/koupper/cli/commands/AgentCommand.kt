package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Manage installed Koupper agents.
//
// Usage:
//   koupper agent list                       — list installed agents
//   koupper agent info <name>                — show agent details
//   koupper agent install <url>              — install agent from URL
//   koupper agent install github:<user/repo/Agent.kts>
//   koupper agent remove <name>              — uninstall an agent
class AgentCommand : Command() {

    override fun name(): String = "agent"

    private val home      = System.getProperty("user.home")!!
    private val agentsDir = File(home, ".koupper/agents")

    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun execute(vararg args: String): String {
        val sub = args.drop(1).firstOrNull() ?: return usage()
        val rest = args.drop(2).toTypedArray()
        return when (sub) {
            "list"    -> listAgents()
            "info"    -> infoAgent(rest.firstOrNull())
            "install" -> installAgent(rest.firstOrNull())
            "remove"  -> removeAgent(rest.firstOrNull())
            else      -> "\n  Unknown subcommand '$sub'. Run 'koupper agent' for usage.\n"
        }
    }

    // ── list ──────────────────────────────────────────────────────────────────

    private fun listAgents(): String {
        val sb = StringBuilder()
        sb.appendLine("\n${ANSI_GREEN_155}  ◈ INSTALLED AGENTS${ANSI_RESET}  (${agentsDir.absolutePath})\n")

        val agents = loadSkills()
        if (agents.isEmpty()) {
            sb.appendLine("  No agents installed. Install with: koupper agent install <url>")
            return sb.toString()
        }

        val nameW = agents.maxOf { it["name"]?.toString()?.length ?: 0 }.coerceAtLeast(4) + 2
        val roleW = agents.maxOf { it["role"]?.toString()?.length ?: 0 }.coerceAtLeast(4) + 2

        sb.append("  ${"NAME".padEnd(nameW)}${"ROLE".padEnd(roleW)}${"VER".padEnd(8)}PERSISTENT\n")
        sb.append("  ${"─".repeat(nameW)}${"─".repeat(roleW)}${"─".repeat(8)}──────────\n")

        agents.forEach { skill ->
            val name      = skill["name"]?.toString() ?: "?"
            val role      = skill["role"]?.toString() ?: ""
            val version   = skill["version"]?.toString() ?: "?"
            val persistent= if (skill["persistent"] == true) "${ANSI_GREEN_155}yes${ANSI_RESET}" else "no"
            sb.appendLine("  ${name.padEnd(nameW)}${role.padEnd(roleW)}${version.padEnd(8)}$persistent")
        }

        sb.appendLine("\n  ${agents.size} agent(s) installed.")
        return sb.toString()
    }

    // ── info ──────────────────────────────────────────────────────────────────

    private fun infoAgent(name: String?): String {
        if (name.isNullOrBlank()) return "\n  Usage: koupper agent info <name>\n"

        val skill = loadSkills().firstOrNull {
            it["name"]?.toString().equals(name, ignoreCase = true) ||
            it["name"]?.toString().equals("${name}Agent", ignoreCase = true)
        } ?: return "\n  ${ANSI_RED}Agent '$name' not found.${ANSI_RESET} Run 'koupper agent list'.\n"

        val sb = StringBuilder()
        val agentName = skill["name"]?.toString() ?: name
        val version   = skill["version"]?.toString() ?: "?"

        sb.appendLine("\n${ANSI_GREEN_155}  ◈ $agentName  v$version${ANSI_RESET}")
        sb.appendLine("  ${"─".repeat(40)}")

        skill["description"]?.let { sb.appendLine("  ${ANSI_YELLOW_229}Description${ANSI_RESET} : $it") }
        skill["role"]?.let        { sb.appendLine("  ${ANSI_YELLOW_229}Role${ANSI_RESET}        : $it") }
        skill["persistent"]?.let  { sb.appendLine("  ${ANSI_YELLOW_229}Persistent${ANSI_RESET}  : $it") }

        @Suppress("UNCHECKED_CAST")
        val triggers = skill["triggers"] as? List<String>
        if (!triggers.isNullOrEmpty())
            sb.appendLine("  ${ANSI_YELLOW_229}Triggers${ANSI_RESET}    : ${triggers.joinToString(", ")}")

        @Suppress("UNCHECKED_CAST")
        val providers = skill["providers"] as? List<String>
        if (!providers.isNullOrEmpty())
            sb.appendLine("  ${ANSI_YELLOW_229}Providers${ANSI_RESET}   : ${providers.joinToString(", ")}")

        @Suppress("UNCHECKED_CAST")
        val tags = skill["tags"] as? List<String>
        if (!tags.isNullOrEmpty())
            sb.appendLine("  ${ANSI_YELLOW_229}Tags${ANSI_RESET}        : ${tags.joinToString(", ")}")

        @Suppress("UNCHECKED_CAST")
        val envVars = skill["envVars"] as? List<Map<String, Any>>
        if (!envVars.isNullOrEmpty()) {
            sb.appendLine("\n  ${ANSI_YELLOW_229}Env vars:${ANSI_RESET}")
            envVars.forEach { ev ->
                val req = if (ev["required"] == true) "${ANSI_RED}required${ANSI_RESET}" else "optional"
                sb.appendLine("    ${ev["name"]}  ($req)  — ${ev["description"]}")
            }
        }

        val usage = skill["usage"]?.toString()
        sb.appendLine("\n  ${ANSI_YELLOW_229}Run:${ANSI_RESET}")
        sb.appendLine(usage ?: "    koupper run ~/.koupper/agents/$agentName.kts")

        skill["docs"]?.let { sb.appendLine("\n  ${ANSI_YELLOW_229}Docs:${ANSI_RESET} $it") }
        sb.appendLine()
        return sb.toString()
    }

    // ── install ───────────────────────────────────────────────────────────────

    private fun installAgent(target: String?): String {
        if (target.isNullOrBlank()) return """
  Usage:
    koupper agent install <url>
    koupper agent install github:<user>/<repo>/<path/to/Agent.kts>

  Examples:
    koupper agent install https://raw.githubusercontent.com/user/repo/main/MyAgent.kts
    koupper agent install github:koupper-jvm/agents/RssFeedAgent.kts

"""
        val sb = StringBuilder()
        sb.appendLine("\n${ANSI_GREEN_155}  ◈ INSTALLING AGENT${ANSI_RESET}")

        val rawUrl = resolveUrl(target) ?: return "\n  ${ANSI_RED}Cannot resolve URL for: $target${ANSI_RESET}\n"
        val scriptName = rawUrl.substringAfterLast("/").substringBefore("?")

        if (!scriptName.endsWith(".kts")) {
            return "\n  ${ANSI_RED}Target must be a .kts file. Got: $scriptName${ANSI_RESET}\n"
        }

        sb.appendLine("  Fetching: $rawUrl")

        val code = fetch(rawUrl) ?: return "  ${ANSI_RED}✗ Failed to download agent.${ANSI_RESET}\n"

        agentsDir.mkdirs()
        val agentFile = File(agentsDir, scriptName)
        agentFile.writeText(code)
        sb.appendLine("  ${ANSI_GREEN_155}✓${ANSI_RESET} Saved  : ${agentFile.absolutePath}")

        // Try to also download the skill.json
        val skillUrl  = rawUrl.removeSuffix(".kts") + ".skill.json"
        val skillJson = fetch(skillUrl)
        if (skillJson != null) {
            val skillFile = File(agentsDir, scriptName.removeSuffix(".kts") + ".skill.json")
            skillFile.writeText(skillJson)
            sb.appendLine("  ${ANSI_GREEN_155}✓${ANSI_RESET} Skill  : ${skillFile.absolutePath}")
        }

        sb.appendLine("\n  Run with:")
        sb.appendLine("    koupper run ~/.koupper/agents/$scriptName")
        sb.appendLine()
        return sb.toString()
    }

    // ── remove ────────────────────────────────────────────────────────────────

    private fun removeAgent(name: String?): String {
        if (name.isNullOrBlank()) return "\n  Usage: koupper agent remove <name>\n"

        val baseName  = if (name.endsWith(".kts")) name.removeSuffix(".kts") else name
        val agentFile = File(agentsDir, "$baseName.kts")
        val skillFile = File(agentsDir, "$baseName.skill.json")
        val draftFile = File(agentsDir, "draft_$baseName.json")

        if (!agentFile.exists())
            return "\n  ${ANSI_RED}Agent '$baseName' not found in ${agentsDir.absolutePath}${ANSI_RESET}\n"

        agentFile.delete()
        skillFile.takeIf { it.exists() }?.delete()
        draftFile.takeIf { it.exists() }?.delete()

        return "\n  ${ANSI_GREEN_155}✓${ANSI_RESET}  Removed $baseName\n"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadSkills(): List<Map<String, Any>> {
        if (!agentsDir.exists()) return emptyList()
        return agentsDir.listFiles { f -> f.name.endsWith(".skill.json") }
            ?.mapNotNull { f ->
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                        .readValue(f, Map::class.java) as Map<String, Any>
                }.getOrNull()
            }
            ?.sortedBy { it["name"]?.toString() }
            ?: emptyList()
    }

    private fun resolveUrl(target: String): String? = when {
        target.startsWith("http://") || target.startsWith("https://") -> target
        target.startsWith("github:") -> {
            // github:user/repo/path/to/Agent.kts
            val path = target.removePrefix("github:")
            "https://raw.githubusercontent.com/$path"
        }
        else -> null
    }

    private fun fetch(url: String): String? = runCatching {
        val req  = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET().build()
        val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() == 200) resp.body() else null
    }.getOrNull()

    private fun usage() = """
  ${ANSI_GREEN_155}koupper agent${ANSI_RESET} — manage installed agents

  ${ANSI_YELLOW_229}Subcommands:${ANSI_RESET}
    list                    List all installed agents
    info <name>             Show agent details
    install <url>           Install agent from URL or GitHub shorthand
    remove <name>           Uninstall an agent

  ${ANSI_YELLOW_229}Examples:${ANSI_RESET}
    koupper agent list
    koupper agent info RssFeedAgent
    koupper agent install https://raw.githubusercontent.com/user/repo/main/MyAgent.kts
    koupper agent install github:koupper-jvm/agents/WeatherAgent.kts
    koupper agent remove WeatherAgent

"""
}
