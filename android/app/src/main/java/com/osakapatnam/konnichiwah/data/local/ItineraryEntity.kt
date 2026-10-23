package com.osakapatnam.konnichiwah.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// The phone's copy of an itinerary, which the screens always read from, so the
// trip opens with no network.
//
// The days and stops are kept as one JSON column (the same shape the API
// uses) rather than separate tables. Every edit rewrites the whole list
// anyway, and it keeps the local and server copies trivially in step.
//
// pendingSync is true when there are local edits the server hasn't seen yet.
// updatedAt changes on every local edit; the sync uses it to tell whether the
// user edited again while a PATCH was in flight.
@Entity(tableName = "itineraries")
data class ItineraryEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long?,
    val title: String,
    val startDate: String?,
    val version: Int,
    val daysJson: String,
    val pendingSync: Boolean,
    val updatedAt: Long,
)
