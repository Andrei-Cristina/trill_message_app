package org.message.trill.encryption.utils

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object TimestampFormatter {
    fun format(epochMillis: Long): String {
        if (epochMillis == 0L) return ""
        return try {
            val instant = Instant.fromEpochMilliseconds(epochMillis)
            val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val date = "${dateTime.year}-${dateTime.monthNumber.toString().padStart(2, '0')}-${dateTime.dayOfMonth.toString().padStart(2, '0')}"
            val time = "${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
            "$date $time"
        } catch (e: Exception) {
            "Invalid date"
        }
    }
}