package com.bossless.companion.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Serializable
data class BusinessHoursDay(
    val open: String = "09:00",
    val close: String = "17:00",
    val closed: Boolean = false,
)

@Serializable
data class BusinessHours(
    val timezone: String? = null,
    val monday: BusinessHoursDay? = null,
    val tuesday: BusinessHoursDay? = null,
    val wednesday: BusinessHoursDay? = null,
    val thursday: BusinessHoursDay? = null,
    val friday: BusinessHoursDay? = null,
    val saturday: BusinessHoursDay? = null,
    val sunday: BusinessHoursDay? = null,
)

object BusinessHoursUtils {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun decode(businessHoursJson: JsonObject?): BusinessHours? {
        if (businessHoursJson == null) return null
        return try {
            json.decodeFromJsonElement(BusinessHours.serializer(), businessHoursJson)
        } catch (_: Exception) {
            null
        }
    }

    fun isWithinBusinessHours(hours: BusinessHours?, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (hours == null) return true

        val zone = try {
            ZoneId.of(hours.timezone ?: now.zone.id)
        } catch (_: Exception) {
            now.zone
        }

        val zonedNow = now.withZoneSameInstant(zone)
        val dayOfWeek = zonedNow.dayOfWeek
        val timeNow = zonedNow.toLocalTime()

        val day = when (dayOfWeek) {
            DayOfWeek.MONDAY -> hours.monday
            DayOfWeek.TUESDAY -> hours.tuesday
            DayOfWeek.WEDNESDAY -> hours.wednesday
            DayOfWeek.THURSDAY -> hours.thursday
            DayOfWeek.FRIDAY -> hours.friday
            DayOfWeek.SATURDAY -> hours.saturday
            DayOfWeek.SUNDAY -> hours.sunday
        } ?: return true

        if (day.closed) return false

        val open = parseTime(day.open) ?: return true
        val close = parseTime(day.close) ?: return true

        return if (close == open) {
            true
        } else if (close.isAfter(open)) {
            !timeNow.isBefore(open) && timeNow.isBefore(close)
        } else {
            // Overnight window (e.g., 22:00 -> 06:00)
            !timeNow.isBefore(open) || timeNow.isBefore(close)
        }
    }

    private fun parseTime(value: String): LocalTime? {
        return try {
            LocalTime.parse(value)
        } catch (_: Exception) {
            null
        }
    }
}
