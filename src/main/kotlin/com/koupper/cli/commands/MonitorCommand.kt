package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import com.koupper.cli.ANSIColors.ANSI_YELLOW_229
import com.koupper.cli.commands.AvailableCommands.MONITOR
import java.io.File

class MonitorCommand : Command() {

    private val home = System.getProperty("user.home")

    init {
        super.name        = MONITOR
        super.description = "\n   Launches the IGLY CORTEX real-time swarm dashboard\n"
        super.usage       = "\n   koupper monitor [jobs-dir]\n"
        super.arguments   = mapOf(
            "jobs-dir" to "Root directory that contains the jobs/<queue>/ tree (default: ~/.koupper/jobs)"
        )
        super.additionalInformation = "\n   Press [q] inside the dashboard to exit cleanly.\n"
    }

    override fun name(): String = MONITOR

    override fun execute(vararg args: String): String {
        // args[0] = context (cwd injected by CommandManager), args[1] = optional jobs-dir
        val jarFile = File("$home/.koupper/libs/koupper-monitor.jar")

        if (!jarFile.exists()) {
            return "\n${ANSI_RED}koupper-monitor.jar not found at ${jarFile.absolutePath}.\n" +
                   "Build it first: cd koupper && ./gradlew :koupper-monitor:build\n" +
                   "Then copy the fat JAR: cp koupper-monitor/build/libs/koupper-monitor-*.jar ~/.koupper/libs/koupper-monitor.jar$ANSI_RESET\n"
        }

        val jobsDir = args.getOrNull(1)
            ?.let { File(it).let { f -> if (f.isAbsolute) f else File(args[0], it) } }
            ?: File("$home/.koupper/jobs")

        if (!jobsDir.exists()) {
            jobsDir.mkdirs()
        }

        val java = ProcessHandle.current().info().command().orElse("java")

        val process = ProcessBuilder(java, "-jar", jarFile.absolutePath, jobsDir.absolutePath)
            .inheritIO()
            .start()

        val exitCode = process.waitFor()

        return if (exitCode != 0) "\n${ANSI_YELLOW_229}Monitor exited with code $exitCode$ANSI_RESET\n" else ""
    }
}
