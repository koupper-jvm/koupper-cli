package com.koupper.cli.commands

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_WHITE
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import java.io.File
import java.net.Socket
import java.util.UUID

class WatchCommand : Command() {
    private val mapper = jacksonObjectMapper()

    private fun runtimeOctopusHost(): String {
        return System.getProperty("koupper.octopus.host")?.trim() 
            ?: System.getenv("KOUPPER_OCTOPUS_HOST")?.trim() 
            ?: "localhost"
    }

    private fun runtimeOctopusPort(): Int {
        return System.getProperty("koupper.octopus.port")?.trim()?.toIntOrNull()
            ?: System.getenv("KOUPPER_OCTOPUS_PORT")?.trim()?.toIntOrNull()
            ?: 9998
    }

    private fun runtimeOctopusToken(): String? {
        return System.getProperty("koupper.octopus.token")?.trim()
            ?: System.getenv("KOUPPER_OCTOPUS_TOKEN")?.trim()
    }

    init {
        super.name = "watch"
        super.usage = "\n   koupper ${ANSI_GREEN_155}$name${ANSI_RESET} [path]\n"
        super.description = "\n   Watch a project and manage dependencies automatically via Octopus Sentinel\n"
        super.arguments = emptyMap()
        super.additionalInformation = ""
    }

    override fun execute(vararg args: String): String {
        val targetPath = if (args.size > 1 && args[1].isNotBlank()) args[1] else args[0]
        val projectDir = File(targetPath).absoluteFile

        if (!projectDir.exists() || !projectDir.isDirectory) {
            return "\n${ANSI_YELLOW_229} Invalid project path: ${projectDir.path}${ANSI_RESET}\n"
        }

        println("\n🛡️  ${ANSI_GREEN_155}Octopus Sentinel${ANSI_RESET} is initializing for: ${projectDir.path}")
        
        return try {
            Socket(runtimeOctopusHost(), runtimeOctopusPort()).use { socket ->
                val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)

                runtimeOctopusToken()?.let { token ->
                    writer.write("AUTH::$token")
                    writer.newLine()
                }

                writer.write(
                    mapper.writeValueAsString(
                        mapOf(
                            "type" to "WATCH",
                            "requestId" to UUID.randomUUID().toString(),
                            "context" to projectDir.path
                        )
                    )
                )
                writer.newLine()
                writer.flush()

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("{")) {
                        val node = runCatching { mapper.readTree(line) }.getOrNull()
                        if (node != null) {
                            val type = node.get("type")?.asText().orEmpty().lowercase()
                            if (type == "result") {
                                return "\n✅ ${ANSI_GREEN_155}${node.get("result")?.asText()}${ANSI_RESET}\n"
                            }
                            if (type == "error") {
                                return "\n❌ ${ANSI_YELLOW_229}${node.get("error")?.asText()}${ANSI_RESET}\n"
                            }
                            if (type == "print") {
                                println(node.get("message")?.asText())
                            }
                        }
                    } else if (line.startsWith("ERROR::")) {
                        return "\n❌ ${ANSI_YELLOW_229}${line.removePrefix("ERROR::")}${ANSI_RESET}\n"
                    }
                }
                "Sentinel command sent successfully."
            }
        } catch (e: Exception) {
            "\n❌ ${ANSI_YELLOW_229}Error connecting to Octopus Engine: ${e.message}${ANSI_RESET}\n"
        }
    }

    override fun name(): String = "watch"

    override fun showArguments(): String = ""
}
