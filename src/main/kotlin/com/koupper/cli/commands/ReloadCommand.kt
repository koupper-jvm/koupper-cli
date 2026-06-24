package com.koupper.cli.commands

import com.koupper.cli.ANSIColors.ANSI_GREEN_155
import com.koupper.cli.ANSIColors.ANSI_RED
import com.koupper.cli.ANSIColors.ANSI_RESET
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class ReloadCommand : Command() {
    override val name: String = "reload"

    override fun execute(vararg args: String): String {
        try {
            val host = System.getenv("KOUPPER_OCTOPUS_HOST") ?: "127.0.0.1"
            val port = System.getenv("KOUPPER_OCTOPUS_PORT")?.toIntOrNull() ?: 9998
            val token = System.getenv("KOUPPER_OCTOPUS_TOKEN")

            Socket(host, port).use { socket ->
                val out = PrintWriter(socket.getOutputStream(), true)
                val inReader = BufferedReader(InputStreamReader(socket.getInputStream()))

                if (!token.isNullOrBlank()) {
                    out.println("AUTH::$token")
                    // Wait for auth verification if required by protocol
                }

                out.println("RELOAD_PROVIDERS")
                val response = inReader.readLine() ?: "No response from daemon"

                return if (response.contains("\"ok\":true") || response.contains("true")) {
                    "$ANSI_GREEN_155✅ Providers reloaded successfully in the daemon.$ANSI_RESET"
                } else {
                    "$ANSI_RED❌ Failed to reload providers: $response$ANSI_RESET"
                }
            }
        } catch (e: Exception) {
            return "$ANSI_RED❌ Error connecting to Octopus daemon: ${e.message}$ANSI_RESET"
        }
    }
}
