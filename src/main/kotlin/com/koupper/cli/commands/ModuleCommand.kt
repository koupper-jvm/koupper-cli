package com.koupper.cli.commands

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import com.koupper.cli.CommandManager
import com.koupper.cli.commands.AvailableCommands.MODULE
import com.koupper.cli.modules.ModuleDescriptor
import java.io.File
import java.io.InputStream

class ModuleCommand : Command() {

    private val icons = mapOf(
        "[init]" to "\u001B[35m[init]\u001B[0m",
        "[cfg]" to "\u001B[36m[cfg]\u001B[0m",
        "[envs]" to "\u001B[33m[envs]\u001B[0m",
        "[kt]" to "\u001B[32m[kt]\u001B[0m",
        "[kts]" to "\u001B[33m[kts]\u001B[0m",
        "[proj]" to "\u001B[34m[proj]\u001B[0m",
        "[kts folder]" to "\u001B[33m[kts folder]\u001B[0m",
        "[hndlrs]" to "\u001B[34m[handlers]\u001B[0m",
        "[ctrls]" to "\u001B[31m[ctrls]\u001B[0m",
        "[script]" to "\u001B[36m[script]\u001B[0m"
    )

    override fun name(): String = MODULE

    override fun execute(vararg args: String): String {
        val flags = mutableListOf<String>()
        var moduleName: String? = null

        for (arg in args.drop(1)) {
            if (arg.startsWith("-")) {
                flags.add(arg)
            } else {
                moduleName = arg
                break
            }
        }

        val rawCurrent = args.getOrNull(0) ?: "."
        val currentDir = File(rawCurrent).absoluteFile
        val results = mutableListOf<String>()

        if (flags.contains("-i")) {
            val koupperHelpersDirectory = System.getProperty("user.home") + File.separator + ".koupper" + File.separator + "helpers" + File.separator
            val finalScript = "${koupperHelpersDirectory}list.kts"

            this::class.java.classLoader.getResourceAsStream("list.txt")?.toFile(finalScript)
            val finalScriptContent = File(finalScript).readText(Charsets.UTF_8)

            val targetDir = if (moduleName != null) File(currentDir, moduleName) else currentDir
            if (!targetDir.exists() || !targetDir.isDirectory) {
                return "\n$ANSI_YELLOW_229 Module not found: ${targetDir.path} $ANSI_RESET\n"
            }

            val escapedPath = targetDir.path.replace("\\", "\\\\")
            val replacedScript = finalScriptContent.replace("%TARGET%", escapedPath)
            File(finalScript).writeText(replacedScript, Charsets.UTF_8)

            CommandManager.commands["run"]?.execute(koupperHelpersDirectory, "list.kts", "-l") ?: ""

            val scanned = scanModules()
            results.add(scanned)
        }

        if (flags.contains("-d")) {
            val descriptor = ModuleDescriptor(
                if (moduleName != null) File(currentDir, moduleName) else currentDir
            )
            results.add(descriptor.describe())
        }

        if (results.isEmpty()) {
            return "\n$ANSI_YELLOW_229 No modules found.$ANSI_RESET\n"
        }

        return "\n" + results.joinToString("\n")
    }

    private fun InputStream.toFile(path: String) {
        File(path).outputStream().use { this.copyTo(it) }
    }

    private fun scanModules(): String {
        val result = StringBuilder()
        val jsonFile = File(System.getProperty("user.home"), ".koupper/helpers/module-analysis.json")
        if (!jsonFile.exists()) return ""

        val jsonData = jacksonObjectMapper().readValue(jsonFile.inputStream(), object : TypeReference<Map<String, Any>>() {})

        val folders = jsonData["folders"] as? List<Map<String, Any?>> ?: emptyList()
        val files = jsonData["files"] as? List<Map<String, Any?>> ?: emptyList()

        val allNames = folders.map { it["folder"] as? String ?: "" } +
                files.map { it["file"] as? String ?: "" }

        val maxNameLength = allNames.maxOfOrNull { it.length } ?: 0

        for (folder in folders) {
            val folderName = folder["folder"] as String
            val tags = (folder["tags"] as? List<String>)?.map { tag -> icons[tag] ?: tag } ?: emptyList()
            val padded = folderName.padEnd(maxNameLength + 2)
            result.append("$padded${tags.joinToString(" ")}\n")
        }

        for (file in files) {
            val fileName = file["file"] as String
            val tags = (file["tags"] as? List<String>)?.map { tag -> icons[tag] ?: tag } ?: emptyList()
            val signature = file["signature"]?.toString() ?: ""
            val padded = fileName.padEnd(maxNameLength + 2)
            result.append("$padded${tags.joinToString(" ")} $signature\n")
        }

        jsonFile.delete()

        val controllersJsonFile =File(System.getProperty("user.home"), ".koupper/helpers/project-controllers.json")

        val GREEN = "\u001B[32m"
        val CYAN = "\u001B[36m"
        val YELLOW = "\u001B[33m"
        val RESET = "\u001B[0m"

        if (controllersJsonFile.exists()) {
            val objectMapper = jacksonObjectMapper()
            val controllersData: List<Map<String, Any?>> = objectMapper.readValue(controllersJsonFile)

            result.append("\n ⚙\uFE0F Controllers found:\n\n")
            controllersData.forEach { controller ->
                val name = controller["controller"] as? String ?: "Unknown"
                val port = controller["port"] ?: "Unknown"
                val path = controller["path"] ?: "/"
                val endpoints = controller["endpoints"] as? List<Map<String, Any?>> ?: emptyList()

                result.append("🔹 Controller: ${CYAN}$name${RESET} (port ${YELLOW}$port${RESET}, base path: ${YELLOW}$path${RESET})\n")
                if (endpoints.isEmpty()) {
                    result.append("   └ No endpoints found.\n")
                } else {
                    endpoints.forEach { endpoint ->
                        val method = endpoint["method"]
                        val endpointPath = endpoint["path"]
                        val consumes = endpoint["consumes"]
                        val produces = endpoint["produces"]
                        val function = endpoint["function"]
                        val handler = endpoint["handler"]

                        result.append(
                            """
                       └ ${GREEN}${method ?: "Unknown"}${RESET} ${endpointPath}
                           ↳ Function: ${CYAN}$function${RESET}
                           ↳ Consumes: ${YELLOW}$consumes${RESET} | Produces: ${YELLOW}$produces${RESET}
                           ↳ Handler: ${CYAN}$handler${RESET}

                    """.trimIndent()
                        )
                    }
                }
            }

            controllersJsonFile.delete()
        } else {
            result.append("\nNo controllers analysis found for this module.\n")
        }

        controllersJsonFile.delete()

        return result.toString()
    }
}
