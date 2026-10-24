package com.osakapatnam.konnichiwah.reminders

import com.osakapatnam.konnichiwah.data.Rulebook
import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.StopDto
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class DayRemindersTest {

    private val start = LocalDate.parse("2026-11-21")

    @Test
    fun eachReminderIsAt7pmJapanTimeTheEveningBefore() {
        val reminders = dayReminders(start, dayCount = 3, now = Instant.parse("2026-11-01T00:00:00Z"))

        // 19:00 JST is 10:00 UTC (Japan has no daylight saving).
        assertEquals(
            listOf(
                DayReminder(1, LocalDate.parse("2026-11-21"), Instant.parse("2026-11-20T10:00:00Z")),
                DayReminder(2, LocalDate.parse("2026-11-22"), Instant.parse("2026-11-21T10:00:00Z")),
                DayReminder(3, LocalDate.parse("2026-11-23"), Instant.parse("2026-11-22T10:00:00Z")),
            ),
            reminders,
        )
    }

    @Test
    fun remindersThatHavePassedAreLeftOut() {
        // Mid-trip, on the evening of day 1 after its 7 pm reminder for day 2.
        val reminders = dayReminders(start, dayCount = 3, now = Instant.parse("2026-11-21T11:00:00Z"))

        assertEquals(listOf(3), reminders.map { it.dayIndex })
    }

    private fun stop(name: String, timeSlot: String, placeType: String) =
        StopDto(id = name, timeSlot = timeSlot, placeName = name, placeType = placeType, city = "Kyoto")

    private val rulebook = Rulebook(
        listOf(
            EtiquetteRuleDto(id = "shrine-bow", placeTypes = listOf("shrine"), title = "Bow at the torii", tip = "", severity = "important"),
            EtiquetteRuleDto(id = "nara-deer", placeTypes = listOf("park"), title = "Bow to the deer", tip = "", severity = "important", cities = listOf("Nara")),
        ),
    )

    @Test
    fun theTextListsTheStopsAndOneTip() {
        val day = DayDto(
            index = 2,
            city = "Kyoto",
            stops = listOf(
                // A park in Kyoto has no tips (the deer rule is Nara only), so
                // the tip comes from the next stop.
                stop("Maruyama Park", "morning", "park"),
                stop("Fushimi Inari Taisha", "afternoon", "shrine"),
            ),
        )

        val text = reminderText(day, rulebook)

        assertEquals("Tomorrow: Day 2 in Kyoto", text.title)
        assertEquals("Morning · Maruyama Park\nAfternoon · Fushimi Inari Taisha\nTip: Bow at the torii", text.body)
    }

    @Test
    fun aFreeDaySaysSo() {
        val text = reminderText(DayDto(index = 4, city = "Osaka", stops = emptyList()), rulebook)

        assertEquals("Tomorrow: Day 4 in Osaka", text.title)
        assertEquals("A free day. Rest, or open the app to add a stop.", text.body)
    }
}
