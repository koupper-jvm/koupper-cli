package com.koupper.cli.commands

import java.io.File

/** Pure helpers for `koupper doctor` (unit-testable). */
object DoctorChecks {

    enum class Level { OK, WARN, ERROR, INFO }

    data class Check(val level: Level, val label: String, val detail: String)

    fun parseJavaMajor(version: String): Int? {
        val v = version.trim()
        if (v.isEmpty()) return null
        // "17.0.9", "1.8.0_392", "21"
        val normalized = if (v.startsWith("1.")) v.removePrefix("1.") else v
        return normalized.substringBefore('.').substringBefore('_').toIntOrNull()
    }

    fun javaRuntimeCheck(version: String, minMajor: Int = 17): Check {
        val major = parseJavaMajor(version)
        return when {
            major == null -> Check(Level.ERROR, "Java runtime", "unable to read version")
            major < minMajor -> Check(Level.ERROR, "Java runtime", "found $version — need Java $minMajor+")
            else -> Check(Level.OK, "Java runtime", "Java $major ($version)")
        }
    }

    fun pathContainsDir(pathEnv: String?, dir: File): Boolean {
        if (pathEnv.isNullOrBlank()) return false
        val target = dir.absoluteFile.normalize().absolutePath
        return pathEnv.split(File.pathSeparatorChar).any { entry ->
            entry.isNotBlank() && File(entry).absoluteFile.normalize().absolutePath.equals(target, ignoreCase = true)
        }
    }

    data class InstallFile(
        val label: String,
        val file: File,
        val required: Boolean = true,
        val sizeMb: Boolean = false
    )

    fun installationFiles(koupperDir: File, isWindows: Boolean): List<InstallFile> {
        val libs = File(koupperDir, "libs")
        val bin = File(koupperDir, "bin")
        val files = mutableListOf(
            InstallFile("koupper binary", File(bin, "koupper")),
            InstallFile("octopus.jar", File(libs, "octopus.jar"), sizeMb = true),
            InstallFile("koupper-cli.jar", File(libs, "koupper-cli.jar"), sizeMb = true),
            // Optional observability sidecar — not required for API/infra workloads
            InstallFile("koupper-monitor.jar", File(libs, "koupper-monitor.jar"), required = false)
        )
        if (isWindows) {
            files.add(InstallFile("koupper.ps1 shim", File(bin, "koupper.ps1")))
        }
        return files
    }
}
