package com.osakapatnam.konnichiwah.ui.itinerary

import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.data.remote.StopDto
import org.junit.Assert.assertEquals
import org.junit.Test

class ItineraryEditsTest {

    private fun stop(name: String, slot: String = "morning") =
        StopDto(id = name, timeSlot = slot, placeName = name, placeType = "temple", city = "Tokyo")

    // Day 1: A, B. Day 2: C.
    private val days = listOf(
        DayDto(index = 1, city = "Tokyo", stops = listOf(stop("A"), stop("B"))),
        DayDto(index = 2, city = "Tokyo", stops = listOf(stop("C"))),
    )

    private fun names(days: List<DayDto>) = days.map { day -> day.stops.map { it.placeName } }

    @Test
    fun moveUpWithinADay() {
        assertEquals(listOf(listOf("B", "A"), listOf("C")), names(moveStopUp(days, 0, 1)))
    }

    @Test
    fun movingWithinADayKeepsTimeSlotsInOrder() {
        val day = listOf(
            DayDto(index = 1, city = "Tokyo", stops = listOf(stop("Temple", "morning"), stop("Shrine", "afternoon"))),
        )

        val moved = moveStopUp(day, 0, 1)[0].stops

        assertEquals(listOf("Shrine" to "morning", "Temple" to "afternoon"), moved.map { it.placeName to it.timeSlot })
    }

    @Test
    fun movingToAnotherDayKeepsTheStopsOwnTimeSlot() {
        val twoDays = listOf(
            DayDto(index = 1, city = "Tokyo", stops = listOf(stop("A", "morning"))),
            DayDto(index = 2, city = "Tokyo", stops = listOf(stop("Gion", "evening"))),
        )

        val moved = moveStopUp(twoDays, 1, 0)[0].stops.last()

        assertEquals("Gion" to "evening", moved.placeName to moved.timeSlot)
    }

    @Test
    fun moveUpFromTheTopOfADayGoesToTheEndOfThePreviousDay() {
        assertEquals(listOf(listOf("A", "B", "C"), listOf()), names(moveStopUp(days, 1, 0)))
    }

    @Test
    fun moveUpFromTheVeryFirstStopDoesNothing() {
        assertEquals(names(days), names(moveStopUp(days, 0, 0)))
    }

    @Test
    fun moveDownFromTheBottomOfADayGoesToTheStartOfTheNextDay() {
        assertEquals(listOf(listOf("A"), listOf("B", "C")), names(moveStopDown(days, 0, 1)))
    }

    @Test
    fun moveDownFromTheVeryLastStopDoesNothing() {
        assertEquals(names(days), names(moveStopDown(days, 1, 0)))
    }

    @Test
    fun deleteRemovesOnlyThatStop() {
        assertEquals(listOf(listOf("B"), listOf("C")), names(deleteStop(days, 0, 0)))
    }

    @Test
    fun addStopGoesInTimeOrder() {
        val day = listOf(
            DayDto(index = 1, city = "Kyoto", stops = listOf(stop("Temple", "morning"), stop("Lunch", "lunch"), stop("Dinner", "dinner"))),
        )

        val result = addStop(day, 0, "  Gion walk  ", "street", "evening", newId = { "new-1" })

        assertEquals(listOf(listOf("Temple", "Lunch", "Gion walk", "Dinner")), names(result))

        val added = result[0].stops[2]
        assertEquals("new-1", added.id)
        assertEquals("Kyoto", added.city)
        assertEquals(emptyList<String>(), added.tipIds)
    }
}
