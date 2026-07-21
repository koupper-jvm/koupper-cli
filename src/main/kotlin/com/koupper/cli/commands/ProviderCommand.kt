package com.koupper.cli.commands

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.koupper.cli.ANSIColors
import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_WHITE
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import com.koupper.cli.commands.AvailableCommands.PROVIDER
import java.io.File

data class ProviderCatalog(
    val version: String = "1.0",
    val providers: List<ProviderEntry> = emptyList()
)

data class ProviderEntry(
    val id: String,
    val serviceProvider: String,
    val description: String,
    val bindings: List<ProviderBinding> = emptyList(),
    val env: List<ProviderEnvVar> = emptyList(),
    val docs: String? = null
)

data class ProviderBinding(
    val contract: String,
    val implementations: List<ProviderImplementation> = emptyList()
)

data class ProviderImplementation(
    val `class`: String,
    val tag: String? = null
)

data class ProviderEnvVar(
    val name: String,
    val required: Boolean,
    val description: String
)

class ProviderCommand : Command() {
    private val mapper = jacksonObjectMapper()
    private val userHome = System.getProperty("user.home")
    private val configuredCatalogPath = System.getProperty("koupper.providers.catalog.path")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("KOUPPER_PROVIDERS_CATALOG")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    private val catalogPath = "$userHome/.koupper/catalog/providers.json"

    init {
        super.name = PROVIDER
        super.usage = "\n" + """
   koupper provider list                          Lists available service providers.
   koupper provider info <provider-id-or-class>  Shows detailed provider information.
   koupper provider new <provider-id>             Generates a new Service Provider scaffold.
        """
        super.description = "\n   Lists service providers and their environment requirements\n"
        super.arguments = mapOf(
            "list" to "Shows all providers with a short description.",
            "info <name>" to "Shows contracts, implementations, tags and environment variables.",
            "new <name>" to "Generates code, tests, and documentation for a new provider."
        )
        super.additionalInformation = "\n   For provider setup details, see official documentation."
    }

    override fun name(): String = PROVIDER

    override fun execute(vararg args: String): String {
        val realArgs = args.drop(1)

        if (realArgs.isEmpty()) {
            return "${showDescription()}${showUsage()}${showArguments()}"
        }

        val subcommand = realArgs.first().lowercase()

        if (subcommand == "new") {
            val name = realArgs.getOrNull(1)
                ?: return "\n${ANSIColors.ANSI_RED}Missing provider name. Use: koupper provider new <name>${ANSI_RESET}\n"
            return scaffoldProvider(name)
        }

        val catalog = loadCatalog() ?: return missingCatalogMessage()

        return when (subcommand) {
            "list" -> listProviders(catalog)
            "info" -> {
                val needle = realArgs.getOrNull(1)
                    ?: return "\n${ANSIColors.ANSI_RED}Missing provider identifier. Use: koupper provider info <name>${ANSI_RESET}\n"
                providerInfo(catalog, needle)
            }

            else -> "\n${ANSIColors.ANSI_RED}Unknown provider subcommand: '$subcommand'${ANSI_RESET}\n${showUsage()}"
        }
    }

    private fun scaffoldProvider(name: String): String {
        val id = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        val className = name.split(Regex("[-_ ]"))
            .joinToString("") { it.replaceFirstChar { char -> char.uppercase() } }
        val packageName = id

        val sb = StringBuilder()
        sb.appendLine("\n${ANSI_YELLOW_229}Scaffolding new provider: $ANSI_GREEN_155$className$ANSI_RESET\n")

        val root = System.getProperty("user.dir")
        
        // 1. Core Provider Interface
        val providerPath = "koupper/providers/src/main/kotlin/com/koupper/providers/$packageName/${className}Provider.kt"
        val providerContent = """
            package com.koupper.providers.$packageName
            
            import com.koupper.providers.ServiceProvider
            
            /**
             * Contract for the $className provider.
             */
            interface ${className}Provider {
                /**
                 * Example operation for $className.
                 */
                fun ping(): ${className}Response
            }
            
            data class ${className}Response(
                val ok: Boolean,
                val message: String,
                val metadata: Map<String, Any?> = emptyMap()
            )
        """.trimIndent()
        writeFile(root, providerPath, providerContent, sb)

        // 2. Service Provider Implementation
        val serviceProviderPath = "koupper/providers/src/main/kotlin/com/koupper/providers/$packageName/${className}ServiceProvider.kt"
        val serviceProviderContent = """
            package com.koupper.providers.$packageName
            
            import com.koupper.container.interfaces.Container
            import com.koupper.providers.ServiceProvider
            
            class ${className}ServiceProvider(private val container: Container) : ServiceProvider {
                override fun up() {
                    this.container.bind(${className}Provider::class) {
                        ${className}Impl()
                    }
                }
            }
            
            class ${className}Impl : ${className}Provider {
                override fun ping(): ${className}Response {
                    return ${className}Response(true, "pong from $className")
                }
            }
        """.trimIndent()
        writeFile(root, serviceProviderPath, serviceProviderContent, sb)

        // 3. Unit Test
        val testPath = "koupper/providers/src/test/kotlin/com/koupper/providers/$packageName/${className}ProviderTest.kt"
        val testContent = """
            package com.koupper.providers.$packageName
            
            import kotlin.test.Test
            import kotlin.test.assertTrue
            import kotlin.test.assertEquals
            
            class ${className}ProviderTest {
                @Test
                fun `should ping successfully`() {
                    val provider = ${className}Impl()
                    val response = provider.ping()
                    
                    assertTrue(response.ok)
                    assertEquals("pong from $className", response.message)
                }
            }
        """.trimIndent()
        writeFile(root, testPath, testContent, sb)

        // 4. Documentation
        val docsPath = "koupper-docs/docs/providers/$id.md"
        val docsContent = """
            # $className Provider
            
            Brief description of what the $className provider does.
            
            ## Environment Variables
            
            | Name | Required | Description |
            | --- | --- | --- |
            | `${className.uppercase()}_API_KEY` | Yes | API key for $className. |
            
            ## Usage Example
            
            ```kotlin
            val $id = app.getInstance(${className}Provider::class)
            val response = ${id}.ping()
            
            println(response.message)
            ```
        """.trimIndent()
        writeFile(root, docsPath, docsContent, sb)

        sb.appendLine("\n${ANSI_YELLOW_229}Manual steps remaining:${ANSI_RESET}")
        sb.appendLine("1. Register ${ANSI_GREEN_155}${className}ServiceProvider${ANSI_RESET} in ${ANSI_WHITE}koupper/providers/src/main/kotlin/com/koupper/providers/ServiceProviderManager.kt${ANSI_RESET}")
        sb.appendLine("2. Add entry to ${ANSI_WHITE}koupper/providers/src/main/resources/providers-catalog.json${ANSI_RESET}")
        sb.appendLine("3. Run tests: ${ANSI_WHITE}./gradlew :providers:test --tests \"com.koupper.providers.$packageName.*\"${ANSI_RESET}")

        return sb.toString()
    }

    private fun writeFile(root: String, relativePath: String, content: String, sb: StringBuilder) {
        val file = File(root, relativePath)
        try {
            file.parentFile.mkdirs()
            file.writeText(content)
            sb.appendLine("  ${ANSI_GREEN_155}✓${ANSI_RESET} Created $relativePath")
        } catch (e: Exception) {
            sb.appendLine("  ${ANSIColors.ANSI_RED}✗${ANSI_RESET} Failed to create $relativePath: ${e.message}")
        }
    }

    private fun loadCatalog(): ProviderCatalog? {
        return catalogCandidates()
            .map(::File)
            .firstNotNullOfOrNull { file ->
                if (!file.exists() || !file.isFile) return@firstNotNullOfOrNull null
                runCatching { mapper.readValue<ProviderCatalog>(file) }.getOrNull()
            }
    }

    private fun catalogCandidates(): List<String> {
        val candidates = mutableListOf<String>()
        configuredCatalogPath?.let { candidates += it }
        candidates += catalogPath
        candidates += "koupper/providers/src/main/resources/providers-catalog.json"
        candidates += "../koupper/providers/src/main/resources/providers-catalog.json"
        return candidates.distinct()
    }

    private fun missingCatalogMessage(): String {
        return "\n${ANSIColors.ANSI_RED}Providers catalog not found at $catalogPath.${ANSI_RESET}\n" +
                "Run ${ANSI_GREEN_155}kotlinc -script install.kts -- --force${ANSI_RESET} to refresh local artifacts, " +
                "or set ${ANSI_GREEN_155}koupper.providers.catalog.path${ANSI_RESET}/" +
                "${ANSI_GREEN_155}KOUPPER_PROVIDERS_CATALOG${ANSI_RESET}.\n"
    }

    private fun listProviders(catalog: ProviderCatalog): String {
        if (catalog.providers.isEmpty()) {
            return "\n${ANSIColors.ANSI_RED}No providers found in catalog.${ANSI_RESET}\n"
        }

        val maxName = catalog.providers.maxOf { "${it.id} (${it.serviceProvider})".length }
        val header = "\n ${ANSI_YELLOW_229}- Available providers:${ANSI_RESET}\n"

        val rows = catalog.providers
            .sortedBy { it.id }
            .joinToString("\n") { provider ->
                val left = "${provider.id} (${provider.serviceProvider})".padEnd(maxName + 3)
                "   $ANSI_GREEN_155$left$ANSI_WHITE${provider.description}$ANSI_RESET"
            }

        return "$header$rows\n\n   Use ${ANSI_GREEN_155}koupper provider info <name>${ANSI_RESET} for details.\n"
    }

    private fun providerInfo(catalog: ProviderCatalog, needle: String): String {
        val normalized = needle.trim().lowercase()

        val provider = catalog.providers.firstOrNull {
            it.id.lowercase() == normalized ||
                    it.serviceProvider.lowercase() == normalized ||
                    it.bindings.any { binding -> binding.contract.lowercase() == normalized }
        } ?: return "\n${ANSIColors.ANSI_RED}Provider '$needle' was not found.${ANSI_RESET}\n"

        val bindings = if (provider.bindings.isEmpty()) {
            "   ${ANSI_WHITE}none${ANSI_RESET}"
        } else {
            provider.bindings.joinToString("\n") { binding ->
                val impls = if (binding.implementations.isEmpty()) {
                    "none"
                } else {
                    binding.implementations.joinToString(", ") { impl ->
                        if (impl.tag.isNullOrBlank()) impl.`class` else "${impl.`class`} [tag=${impl.tag}]"
                    }
                }
                "   $ANSI_GREEN_155${binding.contract}$ANSI_RESET -> $ANSI_WHITE$impls$ANSI_RESET"
            }
        }

        val envVars = if (provider.env.isEmpty()) {
            "   ${ANSI_WHITE}none${ANSI_RESET}"
        } else {
            provider.env.joinToString("\n") { variable ->
                val req = if (variable.required) "required" else "optional"
                "   $ANSI_GREEN_155${variable.name}$ANSI_RESET ($req) - ${variable.description}"
            }
        }

        val docsLine = provider.docs?.let { "\n ${ANSI_YELLOW_229}- Docs:$ANSI_RESET\n   $ANSI_WHITE$it$ANSI_RESET\n" } ?: ""

        return "\n ${ANSI_YELLOW_229}- Provider:$ANSI_RESET\n" +
                "   $ANSI_GREEN_155${provider.id}$ANSI_RESET (${provider.serviceProvider})\n\n" +
                " ${ANSI_YELLOW_229}- Description:$ANSI_RESET\n" +
                "   $ANSI_WHITE${provider.description}$ANSI_RESET\n\n" +
                " ${ANSI_YELLOW_229}- Bindings:$ANSI_RESET\n$bindings\n\n" +
                " ${ANSI_YELLOW_229}- Environment variables:$ANSI_RESET\n$envVars" +
                docsLine
    }
}
