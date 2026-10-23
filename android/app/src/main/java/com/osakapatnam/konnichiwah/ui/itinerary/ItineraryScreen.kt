package com.osakapatnam.konnichiwah.ui.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.data.LocalItinerary
import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.data.remote.StopDto
import com.osakapatnam.konnichiwah.ui.components.ChoiceChips
import com.osakapatnam.konnichiwah.ui.components.Question
import com.osakapatnam.konnichiwah.ui.placeTypeLabel
import com.osakapatnam.konnichiwah.ui.timeSlotLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    onNewTrip: () -> Unit,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingToDay by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.itinerary?.title ?: "Your trip", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = { TextButton(onClick = onNewTrip) { Text("New trip") } },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val itinerary = state.itinerary
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                itinerary == null -> NoTrip(onNewTrip)
                else -> TripContent(
                    itinerary = itinerary,
                    syncing = state.syncing,
                    onSyncNow = viewModel::syncNow,
                    onMoveUp = viewModel::moveUp,
                    onMoveDown = viewModel::moveDown,
                    onDelete = viewModel::delete,
                    onAddStop = { addingToDay = it },
                )
            }
        }
    }

    addingToDay?.let { dayIndex ->
        val day = state.itinerary?.days?.getOrNull(dayIndex)
        if (day != null) {
            AddStopDialog(
                day = day,
                onDismiss = { addingToDay = null },
                onAdd = { name, placeType, timeSlot ->
                    viewModel.add(dayIndex, name, placeType, timeSlot)
                    addingToDay = null
                },
            )
        }
    }
}

@Composable
private fun NoTrip(onNewTrip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("You haven't planned a trip yet.")
        TextButton(onClick = onNewTrip) { Text("Plan a trip") }
    }
}

@Composable
private fun TripContent(
    itinerary: LocalItinerary,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    onMoveUp: (Int, Int) -> Unit,
    onMoveDown: (Int, Int) -> Unit,
    onDelete: (Int, Int) -> Unit,
    onAddStop: (Int) -> Unit,
) {
    val lastDay = itinerary.days.lastIndex

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SyncStatus(itinerary.pendingSync, syncing, onSyncNow) }

        itinerary.days.forEachIndexed { dayIndex, day ->
            item(key = "day-${day.index}") { DayHeader(day, onAdd = { onAddStop(dayIndex) }) }

            if (day.stops.isEmpty()) {
                item(key = "free-${day.index}") {
                    Text(
                        "Free day. Rest, or add a stop.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(day.stops, key = { _, stop -> stop.id }) { stopIndex, stop ->
                StopCard(
                    stop = stop,
                    dayCity = day.city,
                    canMoveUp = dayIndex > 0 || stopIndex > 0,
                    canMoveDown = dayIndex < lastDay || stopIndex < day.stops.lastIndex,
                    onMoveUp = { onMoveUp(dayIndex, stopIndex) },
                    onMoveDown = { onMoveDown(dayIndex, stopIndex) },
                    onDelete = { onDelete(dayIndex, stopIndex) },
                )
            }
        }
    }
}

@Composable
private fun SyncStatus(pendingSync: Boolean, syncing: Boolean, onSyncNow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val text = when {
            syncing -> "Saving…"
            pendingSync -> "Saved on this phone. Not synced yet."
            else -> "Saved on this phone and synced."
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (pendingSync && !syncing) {
            TextButton(onClick = onSyncNow) { Text("Sync now") }
        }
    }
}

@Composable
private fun DayHeader(day: DayDto, onAdd: () -> Unit) {
    // Mention a day trip in the header, e.g. "Day 5 · Kyoto · trip to Nara".
    val tripTowns = day.stops.map { it.city }.filter { !it.equals(day.city, ignoreCase = true) }.distinct()
    val title = buildString {
        append("Day ${day.index} · ${day.city}")
        if (tripTowns.isNotEmpty()) append(" · trip to ${tripTowns.joinToString()}")
    }

    Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) { Text("Add stop") }
    }
}

@Composable
private fun StopCard(
    stop: StopDto,
    dayCity: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val where = if (stop.city.equals(dayCity, ignoreCase = true)) "" else " · ${stop.city}"
                Text(
                    "${timeSlotLabel(stop.timeSlot)} · ${placeTypeLabel(stop.placeType)}$where",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stop.placeName, style = MaterialTheme.typography.titleSmall)
                if (stop.note.isNotBlank()) {
                    Text(stop.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            // Plain text arrows keep us free of an icon library dependency.
            // Each button has a content description for screen readers.
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.semantics { contentDescription = "Move up" }) {
                Text("↑")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.semantics { contentDescription = "Move down" }) {
                Text("↓")
            }
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = "Remove ${stop.placeName}" }) {
                Text("✕")
            }
        }
    }
}

@Composable
private fun AddStopDialog(
    day: DayDto,
    onDismiss: () -> Unit,
    onAdd: (name: String, placeType: String, timeSlot: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var placeType by remember { mutableStateOf("attraction") }
    var timeSlot by remember { mutableStateOf("afternoon") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a stop to day ${day.index}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it },
                    label = { Text("Place name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Question("What kind of place?") {
                    ChoiceChips(ADDABLE_PLACE_TYPES, { it == placeType }, { placeType = it }, ::placeTypeLabel)
                }
                Question("When?") {
                    ChoiceChips(TIME_SLOTS, { it == timeSlot }, { timeSlot = it }, ::timeSlotLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, placeType, timeSlot) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
