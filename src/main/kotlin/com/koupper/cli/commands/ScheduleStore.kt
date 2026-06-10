package com.koupper.cli.commands

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class ScheduleEntry(
    val id: String,
    val agent: String,
    val queue: String        = "default",
    val type: String,        // "cron" | "rate" | "once"
    val cron: String?        = null,   // "0 8 * * *"
    val rateMs: Long?        = null,   // 3600000 = hourly
    val runAt: String?       = null,   // ISO datetime for "once"
    val enabled: Boolean     = true,
    val createdAt: String    = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
)

object ScheduleStore {
    private val mapper = jacksonObjectMapper()
    private val home   = System.getProperty("user.home")!!
    val file: File get() = File(home, ".koupper/schedules.json")

    fun load(): List<ScheduleEntry> {
        if (!file.exists()) return emptyList()
        return runCatching { mapper.readValue<List<ScheduleEntry>>(file) }.getOrDefault(emptyList())
    }

    fun save(entries: List<ScheduleEntry>) {
        file.parentFile.mkdirs()
        file.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(entries))
    }

    fun add(entry: ScheduleEntry): ScheduleEntry {
        val existing = load().toMutableList()
        existing.removeAll { it.id == entry.id }
        existing.add(entry)
        save(existing)
        return entry
    }

    fun remove(id: String): Boolean {
        val existing = load().toMutableList()
        val before   = existing.size
        existing.removeAll { it.id == id }
        if (existing.size < before) { save(existing); return true }
        return false
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val existing = load().toMutableList()
        val idx = existing.indexOfFirst { it.id == id }
        if (idx < 0) return false
        existing[idx] = existing[idx].copy(enabled = enabled)
        save(existing)
        return true
    }
}
