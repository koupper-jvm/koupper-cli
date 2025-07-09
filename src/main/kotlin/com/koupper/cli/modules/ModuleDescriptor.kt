package com.koupper.cli.modules

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.koupper.cli.CommandManager
import org.yaml.snakeyaml.Yaml
import java.io.File
import com.fasterxml.jackson.module.kotlin.readValue

class ModuleDescriptor(private val moduleDir: File) {
    fun describe(): String {
        val result = StringBuilder()

        val initFile = File(moduleDir, "init.kts")

        if (initFile.exists()) {
            result.append(describeInitKts(initFile))
            result.append("\n")
        } else {
            result.append("No init.kts file found on this module level.\n\n")
        }

        val configFile = moduleDir.listFiles()?.find {
            it.extension in listOf("yml", "yaml")
        }

        if (configFile != null) {
            result.append(describeConfig(configFile))
        } else {
            result.append("No configuration file found on this module level.\n")
        }

        return result.toString()
    }

    private fun describeInitKts(file: File): String {
        return CommandManager.commands["run"]?.execute(moduleDir.path, file.name ,"--info") ?: ""
    }

    fun describeConfig(file: File): String {
        val sb = StringBuilder()
        val GREEN = "\u001B[32m"
        val RED = "\u001B[31m"
        val YELLOW = "\u001B[33m"
        val RESET = "\u001B[0m"
        val HEADER_COLOR = "\u001B[36m"

        val controllersJsonFile = File(System.getProperty("user.home"), ".koupper/helpers/controllers.json")

        val controllersData = if (controllersJsonFile.exists()) {
            val objectMapper = jacksonObjectMapper()
            objectMapper.readValue<List<Map<String, Any?>>>(controllersJsonFile)
        } else emptyList()

        controllersJsonFile.delete()

        return try {
            val yaml = Yaml()
            val config = yaml.loadAs(file.inputStream(), ApiConfig::class.java)

            sb.append("${HEADER_COLOR}☁️ Http configuration:${RESET}\n")
            sb.append("Config:\n")
            sb.append("  Config version: ${config.version}\n")
            sb.append("  Description: ${config.description}\n")

            val jsonController = controllersData.firstOrNull()
            val jsonContextPath = jsonController?.get("path")?.toString()
            val yamlContextPath = config.server?.contextPath

            val contextPathStatus = when {
                jsonContextPath == null -> "$yamlContextPath ${YELLOW}(No controllers.json)$RESET"
                yamlContextPath == jsonContextPath -> "$yamlContextPath ${GREEN}✔️$RESET"
                else -> "$yamlContextPath ${RED}❌ Differs from $jsonContextPath$RESET"
            }

            val jsonPort = jsonController?.get("port")?.toString()
            val yamlPort = config.server?.port?.toString()

            val portStatus = when {
                jsonPort == null -> "$yamlPort ${YELLOW}(No port in JSON)$RESET"
                yamlPort == jsonPort -> "$yamlPort $GREEN✔️$RESET"
                else -> "$yamlPort $RED❌ Differs from $jsonPort$RESET"
            }

            sb.append("Server:\n")
            sb.append("  Port: $portStatus\n")
            sb.append("  Context path: $contextPathStatus\n\n")

            config.apis?.forEachIndexed { idx, api ->
                sb.append("API #${idx + 1}:\n")
                sb.append("  Name: ${api.name ?: "N/A"}\n")

                val jsonEndpoint = jsonController?.get("endpoints")
                    ?.let { it as? List<Map<String, Any?>> }
                    ?.find {
                        it["path"]?.toString()?.trim() == api.path?.trim() &&
                                it["method"]?.toString()?.trim()?.uppercase() == api.method?.trim()?.uppercase()
                    }

                val pathStatus = compareProperty(api.path, jsonEndpoint?.get("path")?.toString(), "Path", GREEN, RED, RESET)
                val methodStatus = compareProperty(api.method, jsonEndpoint?.get("method")?.toString(), "Method", GREEN, RED, RESET)
                val handlerStatus = compareProperty("RequestHandler" + api.handler?.replaceFirstChar { it.uppercaseChar() }, jsonEndpoint?.get("handler")?.toString(), "Handler", GREEN, RED, RESET)

                sb.append("  Path: $pathStatus\n")
                sb.append("  Method: $methodStatus\n")
                sb.append("  Handler: $handlerStatus\n")
                sb.append("  Description: ${api.description ?: "N/A"}\n")

                val consumesStatus = if (jsonEndpoint?.get("consumes") != null) {
                    "(${jsonEndpoint["consumes"]}) ${YELLOW}Not in YAML$RESET"
                } else {
                    "None"
                }

                val producesStatus = if (jsonEndpoint?.get("produces") != null) {
                    "(${jsonEndpoint["produces"]}) ${YELLOW}Not in YAML$RESET"
                } else {
                    "None"
                }

                sb.append("  Consumes: $consumesStatus\n")
                sb.append("  Produces: $producesStatus\n\n")
            }

            sb.toString()
        } catch (e: Exception) {
            "A configuration file was found, but it does not follow the required layout. Please check https://koupper.com/build/config.yml\n"
        }
    }

    private fun compareProperty(
        yamlValue: String?,
        jsonValue: String?,
        label: String,
        green: String,
        red: String,
        reset: String
    ): String {
        return when {
            jsonValue == null -> "$yamlValue ${red}❌ Not in JSON$reset"
            yamlValue == jsonValue -> "$yamlValue $green✔️$reset"
            else -> "$yamlValue $red❌ Differs from $jsonValue$reset"
        }
    }

}

fun main() {
    ModuleDescriptor(File("C:\\Users\\dosek\\develop\\quizztea.com\\quizztea-auth-service-scripts"))
        .describeConfig(File("C:\\Users\\dosek\\develop\\quizztea.com\\quizztea-auth-service-scripts\\registration-application.yml"))

}
