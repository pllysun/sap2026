package edu.csuft.sap.data.schedule

import java.time.LocalDate

/** 每个账号、自然日、模式、事件只保留一条；已确认记录保留到窗口外，避免每天重复发送。 */
internal data class UsageStamp(val owner: String, val date: String, val mode: String, val event: String, val sent: Boolean = false)

internal class ScheduleUsageBuffer(records: List<UsageStamp> = emptyList()) {
    private val entries = linkedMapOf<List<String>, UsageStamp>()
    init { records.forEach { entries[key(it)] = it } }
    private fun key(value: UsageStamp) = listOf(value.owner, value.date, value.mode, value.event)
    fun records(): List<UsageStamp> = entries.values.toList()
    fun prune(today: LocalDate) {
        entries.entries.removeAll { (_, value) ->
            val day = runCatching { LocalDate.parse(value.date) }.getOrNull()
            day == null || day < today.minusDays(89) || day > today || value.owner.isBlank() || value.owner == "_" ||
                value.mode !in listOf("JW", "WEB", "CLASS") ||
                !(value.event == "VIEW" || value.event == "SYNC" && value.mode == "WEB")
        }
    }
    fun add(value: UsageStamp) { entries.putIfAbsent(key(value), value) }
    fun pending(owner: String) = entries.values.filter { it.owner == owner && !it.sent }.take(120)
    fun acknowledge(batch: List<UsageStamp>) {
        batch.forEach { value -> entries[key(value)]?.let { entries[key(value)] = it.copy(sent = true) } }
    }
}
