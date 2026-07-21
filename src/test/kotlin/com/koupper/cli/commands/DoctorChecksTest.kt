package com.koupper.cli.commands

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoctorChecksTest {

    @Test
    fun `parseJavaMajor handles modern and legacy formats`() {
        assertEquals(17, DoctorChecks.parseJavaMajor("17.0.9"))
        assertEquals(21, DoctorChecks.parseJavaMajor("21"))
        assertEquals(8, DoctorChecks.parseJavaMajor("1.8.0_392"))
    }

    @Test
    fun `javaRuntimeCheck requires Java 17+`() {
        val ok = DoctorChecks.javaRuntimeCheck("17.0.9")
        assertEquals(DoctorChecks.Level.OK, ok.level)

        val bad = DoctorChecks.javaRuntimeCheck("11.0.2")
        assertEquals(DoctorChecks.Level.ERROR, bad.level)
    }

    @Test
    fun `pathContainsDir matches absolute normalized entries`() {
        val dir = createTempDir(prefix = "koupper-bin-")
        try {
            val path = listOf("/usr/bin", dir.absolutePath, "/opt/bin").joinToString(File.pathSeparator)
            assertTrue(DoctorChecks.pathContainsDir(path, dir))
            assertFalse(DoctorChecks.pathContainsDir("/usr/bin", dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `installationFiles includes powershell shim on Windows`() {
        val home = createTempDir(prefix = "koupper-home-")
        try {
            val win = DoctorChecks.installationFiles(home, isWindows = true)
            assertTrue(win.any { it.label == "koupper.ps1 shim" })
            assertFalse(win.first { it.label == "koupper-monitor.jar" }.required)

            val unix = DoctorChecks.installationFiles(home, isWindows = false)
            assertFalse(unix.any { it.label == "koupper.ps1 shim" })
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `doctor reports Java and PATH without requiring LLM`() {
        val home = createTempDir(prefix = "koupper-doctor-home-")
        try {
            val koupper = File(home, ".koupper").also { it.mkdirs() }
            val bin = File(koupper, "bin").also { it.mkdirs() }
            val libs = File(koupper, "libs").also { it.mkdirs() }
            File(bin, "koupper").writeText("shim")
            File(bin, "koupper.ps1").writeText("shim")
            File(libs, "octopus.jar").writeText("x")
            File(libs, "koupper-cli.jar").writeText("x")
            File(libs, "koupper-monitor.jar").writeText("x")
            File(koupper, "jobs").mkdirs()

            val output = DoctorCommand(
                homeDir = home,
                env = { null },
                javaVersion = { "17.0.9" },
                pathEnv = { bin.absolutePath },
                isWindows = true,
                portProbe = { false },
                scheduleLoader = { emptyList() }
            ).execute(".")

            assertTrue(output.contains("Java runtime"))
            assertTrue(output.contains("PATH"))
            assertTrue(output.contains("KOUPPER_LLM_MODEL_PATH"))
            // LLM missing is warning, not a hard error for API/infra installs
            assertTrue(output.contains("warning") || output.contains("⚠"))
            assertFalse(output.contains("All checks passed"))
        } finally {
            home.deleteRecursively()
        }
    }
}
