package com.osakapatnam.konnichiwah.reminders

import com.osakapatnam.konnichiwah.data.Rulebook
import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.ui.timeSlotLabel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Reminders go out at 7 pm Japan time on the evening before each day.
//
// We use Japan time rather than the phone's time zone because the trip days
// are Japan dates, and WorkManager only knows "run after this delay": a
// reminder scheduled for 7 pm in India would fire at 10:30 pm once the phone
// has switched to Japan time. The one reminder sent from India, the evening
// before day 1, arrives at 3:30 pm IST, which is fine for "your trip starts
// tomorrow".
val TRIP_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
val REMINDER_TIME: LocalTime = LocalTime.of(19, 0)

// dayIndex is the day's position in the trip, starting at 1 (the same as
// DayDto.index, which the server numbers by position).
data class DayReminder(val dayIndex: Int, val date: LocalDate, val at: Instant)

// The reminders still to come for a trip starting on startDate. Ones whose
// time has passed are left out.
fun dayReminders(startDate: LocalDate, dayCount: Int, now: Instant): List<DayReminder> =
    (1..dayCount).map { index ->
        val date = startDate.plusDays(index - 1L)
        DayReminder(index, date, date.minusDays(1).atTime(REMINDER_TIME).atZone(TRIP_ZONE).toInstant())
    }.filter { it.at.isAfter(now) }

data class ReminderText(val title: String, val body: String)

// What the notification says, e.g.
//
//   Tomorrow: Day 2 in Kyoto
//   Morning · Fushimi Inari Taisha
//   Afternoon · Kiyomizu-dera
//   Tip: Bow once before passing through a torii
//
// The tip is the most important one for the first stop that has any, from
// the bundled rulebook, so it works offline like the rest of the trip.
fun reminderText(day: DayDto, rulebook: Rulebook?): ReminderText {
    val title = "Tomorrow: Day ${day.index} in ${day.city}"

    if (day.stops.isEmpty()) {
        return ReminderText(title, "A free day. Rest, or open the app to add a stop.")
    }

    val lines = day.stops.map { "${timeSlotLabel(it.timeSlot)} · ${it.placeName}" }.toMutableList()
    val tip = rulebook?.let { book -> day.stops.firstNotNullOfOrNull { book.tipsFor(it).firstOrNull() } }
    if (tip != null) lines += "Tip: ${tip.title}"

    return ReminderText(title, lines.joinToString("\n"))
}
