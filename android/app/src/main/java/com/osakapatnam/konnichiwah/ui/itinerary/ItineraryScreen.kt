package com.osakapatnam.konnichiwah.ui.itinerary

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.data.LocalItinerary
import com.osakapatnam.konnichiwah.data.Rulebook
import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.StopDto
import com.osakapatnam.konnichiwah.reminders.DayReminderWorker
import com.osakapatnam.konnichiwah.ui.components.ChoiceChips
import com.osakapatnam.konnichiwah.ui.components.Question
import com.osakapatnam.konnichiwah.ui.placeTypeLabel
import com.osakapatnam.konnichiwah.ui.timeSlotLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    onNewTrip: () -> Unit,
    onAsk: () -> Unit,
    onCards: () -> Unit,
    onMenu: () -> Unit,
    onFindFood: (StopDto) -> Unit,
    viewModel: ItineraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingToDay by remember { mutableStateOf<Int?>(null) }
    var showMore by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.itinerary?.title ?: "Your trip", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    TextButton(onClick = onAsk) { Text("Ask") }
                    TextButton(onClick = onMenu) { Text("Menu") }
                    TextButton(onClick = onCards) { Text("Cards") }
                    // "New trip" is rarely needed, so it goes in a menu to
                    // leave room for the title.
                    Box {
                        IconButton(onClick = { showMore = true }, modifier = Modifier.semantics { contentDescription = "More" }) {
                            Text("⋮", style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(text = { Text("New trip") }, onClick = { showMore = false; onNewTrip() })
                        }
                    }
                },
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
                    rulebook = state.rulebook,
                    syncing = state.syncing,
                    onSyncNow = viewModel::syncNow,
                    onSetStartDate = viewModel::setStartDate,
                    onMoveUp = viewModel::moveUp,
                    onMoveDown = viewModel::moveDown,
                    onDelete = viewModel::delete,
                    onAddStop = { addingToDay = it },
                    onFindFood = onFindFood,
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
    rulebook: Rulebook?,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    onSetStartDate: (LocalDate) -> Unit,
    onMoveUp: (Int, Int) -> Unit,
    onMoveDown: (Int, Int) -> Unit,
    onDelete: (Int, Int) -> Unit,
    onAddStop: (Int) -> Unit,
    onFindFood: (StopDto) -> Unit,
) {
    val lastDay = itinerary.days.lastIndex
    val startDate = itinerary.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // Which stops have their tips open, by stop id (so it survives a stop
    // moving to another position or day).
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SyncStatus(itinerary.pendingSync, syncing, onSyncNow) }
        item { TripDates(startDate, itinerary.days.size, onSetStartDate) }

        itinerary.days.forEachIndexed { dayIndex, day ->
            item(key = "day-${day.index}") { DayHeader(day, date = startDate?.plusDays(dayIndex.toLong()), onAdd = { onAddStop(dayIndex) }) }

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
                    tips = rulebook?.tipsFor(stop).orEmpty(),
                    expanded = stop.id in expanded,
                    onToggleTips = { expanded = if (stop.id in expanded) expanded - stop.id else expanded + stop.id },
                    dayCity = day.city,
                    canMoveUp = dayIndex > 0 || stopIndex > 0,
                    canMoveDown = dayIndex < lastDay || stopIndex < day.stops.lastIndex,
                    onMoveUp = { onMoveUp(dayIndex, stopIndex) },
                    onMoveDown = { onMoveDown(dayIndex, stopIndex) },
                    onDelete = { onDelete(dayIndex, stopIndex) },
                    onFindFood = { onFindFood(stop) },
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

// "Sat 21 Nov" for day headers, and "21 Nov 2026" for the trip's dates.
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val tripDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

// The trip's dates, and the button to set or change them. Dates are what turn
// on the day-before reminders, so this is also where we ask for permission to
// show notifications (Android 13+).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDates(startDate: LocalDate?, dayCount: Int, onSetStartDate: (LocalDate) -> Unit) {
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(DayReminderWorker.canNotify(context)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
    }

    fun askIfNeeded() {
        if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dates = if (startDate == null) {
                "No dates yet"
            } else {
                val end = startDate.plusDays(maxOf(dayCount - 1L, 0))
                "${startDate.format(tripDateFormat)} – ${end.format(tripDateFormat)}"
            }
            Text(dates, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { picking = true }) { Text(if (startDate == null) "Set dates" else "Change") }
        }

        val note = when {
            startDate == null -> "Add your dates to get a reminder the evening before each day."
            canNotify -> "Reminder at 7 pm Japan time the evening before each day."
            else -> "Notifications are off, so reminders won't show."
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (startDate != null && !canNotify) {
                TextButton(onClick = ::askIfNeeded) { Text("Turn on") }
            }
        }
    }

    if (picking) {
        // The date picker works in UTC milliseconds. Only today onwards can be
        // picked.
        val today = LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (startDate ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)

                override fun isSelectableYear(year: Int) = year >= today.year
            },
        )

        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onSetStartDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                            askIfNeeded()
                        }
                        picking = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState, title = { Text("First day in Japan", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

@Composable
private fun DayHeader(day: DayDto, date: LocalDate?, onAdd: () -> Unit) {
    // Mention a day trip in the header, e.g. "Day 5 · Kyoto · trip to Nara".
    val tripTowns = day.stops.map { it.city }.filter { !it.equals(day.city, ignoreCase = true) }.distinct()
    val title = buildString {
        append("Day ${day.index}")
        if (date != null) append(" · ${date.format(dayFormat)}")
        append(" · ${day.city}")
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
    tips: List<EtiquetteRuleDto>,
    expanded: Boolean,
    onToggleTips: () -> Unit,
    dayCity: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onFindFood: () -> Unit,
) {
    // Tapping the card opens and closes its culture tips.
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tips.isNotEmpty(), onClick = onToggleTips),
    ) {
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
                    // Two lines keep the day list compact; tapping the stop
                    // opens its tips and shows the whole description too.
                    Text(
                        stop.note,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (tips.isNotEmpty() && !expanded) {
                    TipsPreview(tips)
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

        if (expanded) {
            TipsList(tips, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            TextButton(onClick = onFindFood, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
                Text("Find food nearby")
            }
        }
    }
}

// One line under a collapsed stop: its most important tip, and how many more
// there are, e.g. "⚠ Remove your shoes · 4 more tips".
@Composable
private fun TipsPreview(tips: List<EtiquetteRuleDto>) {
    val first = tips.first()
    val more = tips.size - 1
    val marker = if (first.severity == "important") "⚠ " else ""
    val suffix = when (more) {
        0 -> ""
        1 -> " · 1 more tip"
        else -> " · $more more tips"
    }

    Text(
        "$marker${first.title}$suffix",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun TipsList(tips: List<EtiquetteRuleDto>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Culture tips", style = MaterialTheme.typography.labelLarge)
        tips.forEach { rule ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rule.severity == "important") {
                        Text(
                            "IMPORTANT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(rule.title, style = MaterialTheme.typography.titleSmall)
                }
                Text(rule.tip, style = MaterialTheme.typography.bodyMedium)
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
