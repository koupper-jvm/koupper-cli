package com.koupper.cli.commands

import java.io.File

/** Pure helpers for worker retry / dead-letter / queue status (unit-testable). */
object WorkerJobPolicy {

    data class QueueCounts(
        val name: String,
        val pending: Int,
        val processing: Int,
        val failed: Int,
        val dead: Int
    )

    fun readAttempts(jobJson: String): Int =
        Regex(""""attempts"\s*:\s*(-?\d+)""")
            .find(jobJson)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0

    /** Returns JSON with attempts set to [nextAttempts]. Additive / overwrite of existing field. */
    fun withAttempts(jobJson: String, nextAttempts: Int): String {
        val trimmed = jobJson.trim()
        val attemptsRegex = Regex(""""attempts"\s*:\s*-?\d+""")
        return if (attemptsRegex.containsMatchIn(trimmed)) {
            attemptsRegex.replace(trimmed, """"attempts":$nextAttempts""")
        } else {
            val insertAt = trimmed.indexOf('{')
            if (insertAt < 0) return trimmed
            buildString {
                append(trimmed.substring(0, insertAt + 1))
                append("\"attempts\":$nextAttempts,")
                append(trimmed.substring(insertAt + 1))
            }
        }
    }

    /**
     * After a failure, decide destination.
     * [nextAttempts] is the count *after* bumping (1-based failure count).
     * Dead-letter when nextAttempts >= maxRetries.
     */
    fun shouldDeadLetter(nextAttempts: Int, maxRetries: Int): Boolean =
        nextAttempts >= maxRetries.coerceAtLeast(1)

    fun countQueue(qDir: File): QueueCounts {
        val pending = qDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
        val processing = qDir.listFiles { f -> f.isFile && f.name.endsWith(".json.processing") }?.size ?: 0
        val failed = File(qDir, ".failed").listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
        val dead = File(qDir, ".dead").listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
        return QueueCounts(qDir.name, pending, processing, failed, dead)
    }

    fun listQueues(jobsDir: File, excluded: Set<String>): List<File> =
        jobsDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
            ?.sortedBy { it.name }
            ?: emptyList()
}
