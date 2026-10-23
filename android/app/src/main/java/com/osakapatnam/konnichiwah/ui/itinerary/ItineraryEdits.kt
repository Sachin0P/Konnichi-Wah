package com.osakapatnam.konnichiwah.ui.itinerary

import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.data.remote.StopDto
import java.util.UUID

// Pure functions for editing a trip. Each takes the current days and returns
// new ones, which keeps them easy to test and lets the ViewModel save the
// result in one step.

// Time slots in the order they happen during a day (matches the backend).
val TIME_SLOTS = listOf("morning", "lunch", "afternoon", "evening", "dinner")

// Place types a user can pick for a stop they add themselves. Train and taxi
// are left out: they're ways of getting around, not places to visit.
val ADDABLE_PLACE_TYPES = listOf("temple", "shrine", "onsen", "ryokan", "restaurant", "shop", "street", "park", "attraction")

// Moving within a day swaps the two stops' places *and* their time slots, so
// the day still reads in time order (moving the afternoon shrine above the
// morning temple makes the shrine the morning stop). A stop moved to another
// day keeps its own time slot.
//
// Moving up past the top of a day moves the stop to the end of the day before.
fun moveStopUp(days: List<DayDto>, dayIndex: Int, stopIndex: Int): List<DayDto> {
    val day = days[dayIndex]
    return when {
        stopIndex > 0 -> days.replaceDay(dayIndex, day.copy(stops = day.stops.swapKeepingSlots(stopIndex, stopIndex - 1)))
        dayIndex > 0 -> {
            val stop = day.stops[stopIndex]
            val previous = days[dayIndex - 1]
            days
                .replaceDay(dayIndex, day.copy(stops = day.stops - stop))
                .replaceDay(dayIndex - 1, previous.copy(stops = previous.stops + stop))
        }
        else -> days
    }
}

// Moving down past the bottom of a day moves the stop to the start of the next.
fun moveStopDown(days: List<DayDto>, dayIndex: Int, stopIndex: Int): List<DayDto> {
    val day = days[dayIndex]
    return when {
        stopIndex < day.stops.lastIndex -> days.replaceDay(dayIndex, day.copy(stops = day.stops.swapKeepingSlots(stopIndex, stopIndex + 1)))
        dayIndex < days.lastIndex -> {
            val stop = day.stops[stopIndex]
            val next = days[dayIndex + 1]
            days
                .replaceDay(dayIndex, day.copy(stops = day.stops - stop))
                .replaceDay(dayIndex + 1, next.copy(stops = listOf(stop) + next.stops))
        }
        else -> days
    }
}

fun deleteStop(days: List<DayDto>, dayIndex: Int, stopIndex: Int): List<DayDto> {
    val day = days[dayIndex]
    return days.replaceDay(dayIndex, day.copy(stops = day.stops.filterIndexed { i, _ -> i != stopIndex }))
}

// Adds a stop in time order: after every stop in the same or an earlier time
// slot. The id is a UUID so it can be made offline; the server fills in the
// etiquette tips when it syncs.
fun addStop(
    days: List<DayDto>,
    dayIndex: Int,
    placeName: String,
    placeType: String,
    timeSlot: String,
    newId: () -> String = { UUID.randomUUID().toString() },
): List<DayDto> {
    val day = days[dayIndex]
    val stop = StopDto(
        id = newId(),
        timeSlot = timeSlot,
        placeName = placeName.trim(),
        placeType = placeType,
        city = day.city,
    )

    val slotOrder = TIME_SLOTS.indexOf(timeSlot)
    val position = day.stops.indexOfFirst { TIME_SLOTS.indexOf(it.timeSlot) > slotOrder }
        .takeIf { it >= 0 } ?: day.stops.size

    val stops = day.stops.toMutableList().apply { add(position, stop) }
    return days.replaceDay(dayIndex, day.copy(stops = stops))
}

private fun List<DayDto>.replaceDay(index: Int, day: DayDto): List<DayDto> =
    mapIndexed { i, d -> if (i == index) day else d }

// Swaps two stops but leaves each position's time slot where it was.
private fun List<StopDto>.swapKeepingSlots(i: Int, j: Int): List<StopDto> =
    toMutableList().also {
        val a = it[i]
        val b = it[j]
        it[i] = b.copy(timeSlot = a.timeSlot)
        it[j] = a.copy(timeSlot = b.timeSlot)
    }
