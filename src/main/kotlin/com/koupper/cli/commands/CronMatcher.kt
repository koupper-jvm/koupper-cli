package com.koupper.cli.commands

import java.time.LocalDateTime

// Minimal 5-field cron matcher: "minute hour day-of-month month day-of-week"
// Supports: * (any), N (exact), */N (step), N-M (range), N,M,... (list)
// Examples: "0 8 * * *" = every day at 08:00
//           "*/15 * * * *" = every 15 minutes
//           "0 9-17 * * 1-5" = weekdays 9am-5pm every hour
object CronMatcher {

    fun matches(cron: String, dt: LocalDateTime): Boolean {
        val parts = cron.trim().split("\\s+".toRegex())
        if (parts.size != 5) return false
        val (minP, hourP, domP, monP, dowP) = parts
        return fieldMatches(minP, dt.minute)
            && fieldMatches(hourP, dt.hour)
            && fieldMatches(domP, dt.dayOfMonth)
            && fieldMatches(monP, dt.monthValue)
            && fieldMatches(dowP, dt.dayOfWeek.value % 7)  // 0=Sun..6=Sat
    }

    // Returns ms until the next cron fire after [from].
    // Checks minute-by-minute up to 366 days ahead.
    fun nextFireMs(cron: String, from: LocalDateTime): Long? {
        var candidate = from.withSecond(0).withNano(0).plusMinutes(1)
        val limit     = from.plusDays(366)
        while (candidate.isBefore(limit)) {
            if (matches(cron, candidate)) {
                val epochFrom      = from.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val epochCandidate = candidate.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                return epochCandidate - epochFrom
            }
            candidate = candidate.plusMinutes(1)
        }
        return null
    }

    private fun fieldMatches(field: String, value: Int): Boolean {
        if (field == "*") return true
        return field.split(",").any { token -> tokenMatches(token.trim(), value) }
    }

    private fun tokenMatches(token: String, value: Int): Boolean = when {
        token == "*"              -> true
        token.startsWith("*/")   -> {
            val step = token.removePrefix("*/").toIntOrNull() ?: return false
            step > 0 && value % step == 0
        }
        token.contains("-")      -> {
            val (lo, hi) = token.split("-").map { it.toIntOrNull() ?: return false }
            value in lo..hi
        }
        else                     -> token.toIntOrNull() == value
    }
}
