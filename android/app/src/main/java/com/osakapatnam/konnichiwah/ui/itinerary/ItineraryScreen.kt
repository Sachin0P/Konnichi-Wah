package com.osakapatnam.konnichiwah.ui.itinerary

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.LocalItinerary
import com.osakapatnam.konnichiwah.data.Rulebook
import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.StopDto
import com.osakapatnam.konnichiwah.reminders.DayReminderWorker
import com.osakapatnam.konnichiwah.ui.LanguageViewModel
import com.osakapatnam.konnichiwah.ui.components.ChoiceChips
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.Pill
import com.osakapatnam.konnichiwah.ui.components.PillButton
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.Question
import com.osakapatnam.konnichiwah.ui.components.StatusDot
import com.osakapatnam.konnichiwah.ui.components.TipQuote
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.currentLocale
import com.osakapatnam.konnichiwah.ui.placeTypeLabel
import com.osakapatnam.konnichiwah.ui.theme.Palette
import com.osakapatnam.konnichiwah.ui.timeSlotLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Ask and the menu reader are bottom tabs, so this screen only links to the
// diet cards and (in its ⋮ menu) a new trip and the language switch.
@Composable
fun ItineraryScreen(
    onNewTrip: () -> Unit,
    onCards: () -> Unit,
    onFindFood: (StopDto) -> Unit,
    onDirections: (StopDto) -> Unit,
    viewModel: ItineraryViewModel = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingToDay by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        val itinerary = state.itinerary
        when {
            state.loading -> CircularProgressIndicator(color = Palette.Sun, modifier = Modifier.align(Alignment.Center))
            itinerary == null -> NoTrip(onNewTrip)
            else -> TripContent(
                itinerary = itinerary,
                rulebook = state.rulebook,
                syncing = state.syncing,
                loadingHotels = state.loadingHotels,
                onSyncNow = viewModel::syncNow,
                onRecommendHotels = viewModel::recommendHotels,
                onSetStartDate = viewModel::setStartDate,
                onMoveUp = viewModel::moveUp,
                onMoveDown = viewModel::moveDown,
                onDelete = viewModel::delete,
                onAddStop = { addingToDay = it },
                onFindFood = onFindFood,
                onDirections = onDirections,
                header = {
                    TripHeader(
                        title = itinerary.title,
                        onCards = onCards,
                        onNewTrip = onNewTrip,
                        onSwitchLanguage = languageViewModel::toggle,
                    )
                },
            )
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

// The trip's serif title, with the diet cards and the ⋮ menu beside it.
// "New trip" and the language switch are rarely needed, so they go in the
// menu.
@Composable
private fun TripHeader(title: String, onCards: () -> Unit, onNewTrip: () -> Unit, onSwitchLanguage: () -> Unit) {
    var showMore by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                title.ifBlank { stringResource(R.string.it_your_trip) },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val more = stringResource(R.string.it_more)
            Box {
                IconButton(onClick = { showMore = true }, modifier = Modifier.semantics { contentDescription = more }) {
                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.it_new_trip)) }, onClick = { showMore = false; onNewTrip() })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.switch_language)) },
                        onClick = { showMore = false; onSwitchLanguage() },
                    )
                }
            }
        }
        PillButton("🥗  " + stringResource(R.string.cards_title), onClick = onCards)
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
        Text(stringResource(R.string.it_no_trip), style = MaterialTheme.typography.titleLarge)
        PrimaryButton(stringResource(R.string.it_plan_trip), onClick = onNewTrip)
    }
}

@Composable
private fun TripContent(
    itinerary: LocalItinerary,
    rulebook: Rulebook?,
    syncing: Boolean,
    loadingHotels: Boolean,
    onSyncNow: () -> Unit,
    onRecommendHotels: () -> Unit,
    onSetStartDate: (LocalDate) -> Unit,
    onMoveUp: (Int, Int) -> Unit,
    onMoveDown: (Int, Int) -> Unit,
    onDelete: (Int, Int) -> Unit,
    onAddStop: (Int) -> Unit,
    onFindFood: (StopDto) -> Unit,
    onDirections: (StopDto) -> Unit,
    header: @Composable () -> Unit,
) {
    val lastDay = itinerary.days.lastIndex
    val startDate = itinerary.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    // Which stops are open, by stop id (so it survives a stop moving to
    // another position or day).
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }

    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 24.dp)) {
        item { header() }
        item { SyncStatus(itinerary.pendingSync, syncing, onSyncNow) }
        item { TripDates(startDate, itinerary.days.size, onSetStartDate) }

        val missingHotels = itinerary.days.any { it.hotelName.isNullOrBlank() }
        if (missingHotels) {
            item {
                KCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, end = 8.dp),
                    color = Palette.HeroPink,
                    border = BorderStroke(1.dp, Palette.Petal),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("🏨  Hotel Recommendations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Palette.Maroon)
                            Text("Find real hotels & ryokans within your budget", style = MaterialTheme.typography.bodySmall, color = Palette.Ink)
                        }
                        if (loadingHotels) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Palette.Sun, strokeWidth = 2.dp)
                        } else {
                            PillButton("Find Hotels", onClick = onRecommendHotels)
                        }
                    }
                }
            }
        }

        itinerary.days.forEachIndexed { dayIndex, day ->
            item(key = "day-${day.index}") { DayHeader(day, date = startDate?.plusDays(dayIndex.toLong()), onAdd = { onAddStop(dayIndex) }) }

            val isNewCity = dayIndex == 0 || !day.city.equals(itinerary.days[dayIndex - 1].city, ignoreCase = true)
            if (isNewCity) {
                if (!day.hotelName.isNullOrBlank()) {
                    item(key = "hotel-${day.index}") {
                        HotelStayCard(day.city, day.hotelName, day.hotelPrice, day.hotelReason)
                    }
                } else {
                    item(key = "hotel-missing-${day.index}") {
                        HotelMissingCard(day.city, loadingHotels, onRecommendHotels)
                    }
                }
            }

            if (day.stops.isEmpty()) {
                item(key = "free-${day.index}") {
                    Text(
                        stringResource(R.string.it_free_day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(day.stops, key = { _, stop -> stop.id }) { stopIndex, stop ->
                StopItem(
                    stop = stop,
                    tips = rulebook?.tipsFor(stop).orEmpty(),
                    expanded = stop.id in expanded,
                    onToggle = { expanded = if (stop.id in expanded) expanded - stop.id else expanded + stop.id },
                    dayCity = day.city,
                    lastOfDay = stopIndex == day.stops.lastIndex,
                    canMoveUp = dayIndex > 0 || stopIndex > 0,
                    canMoveDown = dayIndex < lastDay || stopIndex < day.stops.lastIndex,
                    onMoveUp = { onMoveUp(dayIndex, stopIndex) },
                    onMoveDown = { onMoveDown(dayIndex, stopIndex) },
                    onDelete = { onDelete(dayIndex, stopIndex) },
                    onFindFood = { onFindFood(stop) },
                    onDirections = { onDirections(stop) },
                )
            }
        }
    }
}

// "● Saved on this phone and synced", in green, or in orange with a "Sync
// now" button when an edit hasn't reached the server yet.
@Composable
private fun SyncStatus(pendingSync: Boolean, syncing: Boolean, onSyncNow: () -> Unit) {
    Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val text = when {
            syncing -> stringResource(R.string.it_saving)
            pendingSync -> stringResource(R.string.it_not_synced)
            else -> stringResource(R.string.it_synced)
        }
        StatusDot(text, color = if (pendingSync && !syncing) Palette.OrangeInk else Palette.Green, modifier = Modifier.weight(1f))
        if (pendingSync && !syncing) {
            TextButton(onClick = onSyncNow) { Text(stringResource(R.string.it_sync_now), color = Palette.Sun) }
        }
    }
}

// "Sat 21 Nov" for day headers, and "21 Nov 2026" for the trip's dates, in
// the language on screen.
@Composable
private fun dayFormat() = DateTimeFormatter.ofPattern("EEE d MMM", currentLocale())

@Composable
private fun tripDateFormat() = DateTimeFormatter.ofPattern("d MMM yyyy", currentLocale())

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

    KCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, end = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dates = if (startDate == null) {
                stringResource(R.string.it_no_dates)
            } else {
                val end = startDate.plusDays(maxOf(dayCount - 1L, 0))
                val format = tripDateFormat()
                stringResource(R.string.it_date_range, startDate.format(format), end.format(format))
            }
            Text("📅  $dates", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            PillButton(stringResource(if (startDate == null) R.string.it_set_dates else R.string.it_change), onClick = { picking = true })
        }

        val note = when {
            startDate == null -> stringResource(R.string.it_dates_hint)
            canNotify -> stringResource(R.string.it_reminder_on)
            else -> stringResource(R.string.it_notifications_off)
        }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (startDate != null && !canNotify) {
                TextButton(onClick = ::askIfNeeded) { Text(stringResource(R.string.it_turn_on), color = Palette.Sun) }
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
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = pickerState, title = { Text(stringResource(R.string.it_first_day), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

@Composable
private fun DayHeader(day: DayDto, date: LocalDate?, onAdd: () -> Unit) {
    // Mention a day trip in the header, e.g. "Day 5 · Kyoto", "trip to Nara".
    val tripTowns = day.stops.map { it.city }.filter { !it.equals(day.city, ignoreCase = true) }.distinct()
    val title = listOf(stringResource(R.string.it_day, day.index), day.city).joinToString(" · ")
    val subtitle = listOfNotNull(
        date?.format(dayFormat()),
        tripTowns.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.it_trip_to, it.joinToString()) },
    ).joinToString(" · ")

    Row(modifier = Modifier.padding(top = 28.dp, bottom = 14.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PillButton("+ " + stringResource(R.string.it_add_stop), onClick = onAdd)
    }
}

@Composable
private fun HotelStayCard(city: String, hotelName: String, hotelPrice: String?, hotelReason: String?) {
    KCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp, end = 8.dp),
        color = Palette.HeroPink,
        border = BorderStroke(1.dp, Palette.Petal),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "🏨  $hotelName",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.Maroon,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                if (!hotelPrice.isNullOrBlank()) {
                    Text(
                        hotelPrice,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Green,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (!hotelReason.isNullOrBlank()) {
                Text(hotelReason, style = MaterialTheme.typography.bodySmall, color = Palette.Ink)
            }
        }
    }
}

@Composable
private fun HotelMissingCard(city: String, loading: Boolean, onRecommend: () -> Unit) {
    KCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp, end = 8.dp),
        color = Palette.HeroPink,
        border = BorderStroke(1.dp, Palette.Petal),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("🏨  Recommended Stay in $city", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Palette.Maroon)
                Text("Find real hotels & ryokans within your budget", style = MaterialTheme.typography.bodySmall, color = Palette.Ink)
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Palette.Sun, strokeWidth = 2.dp)
            } else {
                PillButton("Find Hotels", onClick = onRecommend)
            }
        }
    }
}

// One stop on the day's timeline: a red dot joined to the next stop by a
// thin line, the time and place, tags, and its culture tip quoted underneath.
// Tapping the stop opens all its tips and the food search.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopItem(
    stop: StopDto,
    tips: List<EtiquetteRuleDto>,
    expanded: Boolean,
    onToggle: () -> Unit,
    dayCity: String,
    lastOfDay: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    onFindFood: () -> Unit,
    onDirections: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The line to the next stop is drawn rather than laid out, so it
            // always reaches the bottom of the stop, however tall it is.
            .drawBehind {
                if (!lastOfDay) {
                    val x = 7.dp.toPx()
                    drawLine(Palette.Line, Offset(x, 22.dp.toPx()), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
            }
            .clickable(onClick = onToggle),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(14.dp)
                .background(Palette.Sun, CircleShape),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(timeSlotLabel(stop.timeSlot), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stop.placeName, style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold))
                }
                EditButtons(stop.placeName, canMoveUp, canMoveDown, onMoveUp, onMoveDown, onDelete)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(placeTypeLabel(stop.placeType), toneFor(stop.placeType))
                if (!stop.city.equals(dayCity, ignoreCase = true)) {
                    Pill(stop.city, Tone.PETAL)
                }
            }

            if (stop.note.isNotBlank()) {
                // Two lines keep the day compact; opening the stop shows the
                // whole description.
                Text(
                    stop.note,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (tips.isNotEmpty()) {
                if (expanded) TipsList(tips) else TipPreview(tips)
            }

            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("🍛  " + stringResource(R.string.it_find_food), onClick = onFindFood, tone = Tone.GREEN)
                    PillButton("🚆  " + stringResource(R.string.dir_from_stop), onClick = onDirections, tone = Tone.ORANGE)
                }
            }
        }
    }
}

// Restaurants are green (food), ways of getting around orange (travel), and
// everything else sakura pink.
private fun toneFor(placeType: String) = when (placeType) {
    "restaurant" -> Tone.GREEN
    "train", "taxi" -> Tone.ORANGE
    else -> Tone.PETAL
}

// Move up, move down and remove. Plain text arrows keep us free of an icon
// library dependency; each button has a content description for screen readers.
@Composable
private fun EditButtons(
    placeName: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    val moveUp = stringResource(R.string.it_move_up)
    val moveDown = stringResource(R.string.it_move_down)
    val remove = stringResource(R.string.it_remove, placeName)
    Row {
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp).semantics { contentDescription = moveUp }) {
            Text("↑", color = if (canMoveUp) Palette.Mute else Palette.Line)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp).semantics { contentDescription = moveDown }) {
            Text("↓", color = if (canMoveDown) Palette.Mute else Palette.Line)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).semantics { contentDescription = remove }) {
            Text("✕", color = Palette.Mute)
        }
    }
}

// The stop's most important tip, quoted, and how many more there are.
@Composable
private fun TipPreview(tips: List<EtiquetteRuleDto>) {
    val first = tips.first()
    val more = tips.size - 1
    TipQuote {
        Text(first.tip, style = MaterialTheme.typography.bodyMedium)
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.it_more_tips, more, more),
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Sun,
            )
        }
    }
}

@Composable
private fun TipsList(tips: List<EtiquetteRuleDto>) {
    TipQuote {
        Text(stringResource(R.string.it_culture_tips), style = MaterialTheme.typography.labelLarge, color = Palette.Maroon)
        tips.forEach { rule ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rule.severity == "important") {
                        Pill(stringResource(R.string.it_important), Tone.MAROON)
                    }
                    Text(rule.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp), modifier = Modifier.weight(1f, fill = false))
                }
                Text(rule.tip, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private data class CuratedStopRec(
    val name: String,
    val placeType: String,
    val suggestedSlot: String,
    val category: String, // "sights", "food", "nightlife"
)

private fun recommendationsForCity(city: String): List<CuratedStopRec> = when (city.lowercase()) {
    "kyoto" -> listOf(
        // Sights
        CuratedStopRec("Fushimi Inari Taisha", "shrine", "morning", "sights"),
        CuratedStopRec("Kinkaku-ji (Golden Pavilion)", "temple", "morning", "sights"),
        CuratedStopRec("Kiyomizu-dera Temple", "temple", "afternoon", "sights"),
        CuratedStopRec("Arashiyama Bamboo Grove", "park", "morning", "sights"),
        CuratedStopRec("Nara Park & Todai-ji", "park", "morning", "sights"),
        // Food (Breakfast, Lunch, Dinner)
        CuratedStopRec("Inoda Coffee Honten", "restaurant", "breakfast", "food"),
        CuratedStopRec("Smart Coffee Sanjo", "restaurant", "breakfast", "food"),
        CuratedStopRec("Nishiki Market Street Food", "shop", "lunch", "food"),
        CuratedStopRec("Shigetsu Shojin Ryori", "restaurant", "lunch", "food"),
        CuratedStopRec("Gion Tsujiri Matcha", "restaurant", "afternoon", "food"),
        CuratedStopRec("Nishiki Warai Okonomiyaki", "restaurant", "dinner", "food"),
        // Nightlife & Pubs
        CuratedStopRec("Pontocho Alley Izakayas", "street", "evening", "nightlife"),
        CuratedStopRec("Before9 Craft Beer & Sake", "restaurant", "dinner", "nightlife"),
        CuratedStopRec("Kiyamachi-dori Canal Bars", "street", "evening", "nightlife"),
        CuratedStopRec("Gion Evening Lantern Walk", "street", "evening", "nightlife"),
        CuratedStopRec("Kamogawa Riverfront Hangout", "street", "evening", "nightlife"),
    )
    "osaka" -> listOf(
        // Sights
        CuratedStopRec("Osaka Castle & Park", "attraction", "morning", "sights"),
        CuratedStopRec("Universal Studios Japan", "attraction", "morning", "sights"),
        CuratedStopRec("Den Den Town Nipponbashi", "shop", "afternoon", "sights"),
        CuratedStopRec("Shinsekai Retro Quarter", "attraction", "afternoon", "sights"),
        CuratedStopRec("Umeda Sky Floating Garden", "attraction", "evening", "sights"),
        // Food (Breakfast, Lunch, Dinner)
        CuratedStopRec("Kuromon Morning Market", "restaurant", "breakfast", "food"),
        CuratedStopRec("Dotonbori Okonomiyaki", "restaurant", "lunch", "food"),
        CuratedStopRec("Takoyaki Wanaka Dotonbori", "restaurant", "afternoon", "food"),
        CuratedStopRec("Shama Vegetarian Indian", "restaurant", "lunch", "food"),
        CuratedStopRec("Kushikatsu Daruma Skewers", "restaurant", "dinner", "food"),
        // Nightlife & Pubs
        CuratedStopRec("Ura-Namba Tachinomi Alleys", "street", "evening", "nightlife"),
        CuratedStopRec("Bar Nayuta Speakeasy", "restaurant", "dinner", "nightlife"),
        CuratedStopRec("Dotonbori Canal Neon Walk", "street", "evening", "nightlife"),
        CuratedStopRec("Amerikamura Youth Street", "street", "afternoon", "nightlife"),
        CuratedStopRec("Misono Building Underground Bars", "street", "evening", "nightlife"),
    )
    "sapporo" -> listOf(
        CuratedStopRec("Odori Park & TV Tower", "park", "morning", "sights"),
        CuratedStopRec("Otaru Canal Day Trip", "street", "afternoon", "sights"),
        CuratedStopRec("Niseko Powder Snow", "park", "morning", "sights"),
        CuratedStopRec("Jozankei Hot Springs", "onsen", "afternoon", "sights"),
        CuratedStopRec("Nijo Morning Fish Market", "restaurant", "breakfast", "food"),
        CuratedStopRec("Sapporo Ramen Alley", "restaurant", "lunch", "food"),
        CuratedStopRec("Susukino Nightlife District", "street", "evening", "nightlife"),
        CuratedStopRec("Sapporo Beer Museum & Hall", "restaurant", "dinner", "nightlife"),
    )
    else -> listOf(
        // Tokyo default
        // Sights
        CuratedStopRec("Senso-ji Temple Asakusa", "temple", "morning", "sights"),
        CuratedStopRec("Meiji Jingu Shrine", "shrine", "morning", "sights"),
        CuratedStopRec("Shibuya Sky Rooftop Deck", "attraction", "evening", "sights"),
        CuratedStopRec("Akihabara Electric Town", "shop", "afternoon", "sights"),
        CuratedStopRec("teamLab Planets Toyosu", "attraction", "afternoon", "sights"),
        CuratedStopRec("Shinjuku Gyoen Garden", "park", "morning", "sights"),
        CuratedStopRec("Harajuku Takeshita Street", "street", "afternoon", "sights"),
        CuratedStopRec("Shimokitazawa Vintage Scene", "street", "afternoon", "sights"),
        // Food (Breakfast, Lunch, Dinner)
        CuratedStopRec("Komeda's Coffee Morning Set", "restaurant", "breakfast", "food"),
        CuratedStopRec("Chatei Hatou Kissaten", "restaurant", "breakfast", "food"),
        CuratedStopRec("Tsukiji Morning Food Stalls", "restaurant", "breakfast", "food"),
        CuratedStopRec("T's Tantan Vegan Ramen", "restaurant", "lunch", "food"),
        CuratedStopRec("Ichiran Tonkotsu Ramen", "restaurant", "lunch", "food"),
        CuratedStopRec("Nirvanam South Indian Thali", "restaurant", "dinner", "food"),
        CuratedStopRec("Kura Sushi Conveyor Belt", "restaurant", "dinner", "food"),
        // Nightlife & Pubs
        CuratedStopRec("Shibuya Yokocho Alleys", "restaurant", "dinner", "nightlife"),
        CuratedStopRec("Nonbei Yokocho (Drinker's Alley)", "street", "evening", "nightlife"),
        CuratedStopRec("Golden Gai & Omoide Yokocho", "street", "evening", "nightlife"),
        CuratedStopRec("Spring Valley Craft Brewery", "restaurant", "dinner", "nightlife"),
        CuratedStopRec("Torikizoku Budget Izakaya", "restaurant", "dinner", "nightlife"),
        CuratedStopRec("Roppongi Hills Sky Deck Bars", "attraction", "evening", "nightlife"),
    )
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) Palette.Maroon else Palette.Petal,
        contentColor = if (selected) Palette.Tip else Palette.Maroon,
        border = if (selected) null else BorderStroke(1.dp, Palette.Line),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddStopDialog(
    day: DayDto,
    onDismiss: () -> Unit,
    onAdd: (name: String, placeType: String, timeSlot: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var placeType by remember { mutableStateOf("attraction") }
    var timeSlot by remember { mutableStateOf("afternoon") }
    var selectedCategory by remember { mutableStateOf("all") }

    val recommendations = remember(day.city) {
        recommendationsForCity(day.city)
    }

    val filteredRecommendations = remember(recommendations, selectedCategory) {
        if (selectedCategory == "all") recommendations
        else recommendations.filter { it.category == selectedCategory }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.it_add_stop_title, day.index)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Curated recommendations
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.it_add_stop_recommendations, day.city),
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.Maroon,
                    )
                    Text(
                        text = stringResource(R.string.it_add_stop_rec_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Category filter row
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CategoryChip(stringResource(R.string.it_rec_category_all), selectedCategory == "all") { selectedCategory = "all" }
                        CategoryChip(stringResource(R.string.it_rec_category_sights), selectedCategory == "sights") { selectedCategory = "sights" }
                        CategoryChip(stringResource(R.string.it_rec_category_food), selectedCategory == "food") { selectedCategory = "food" }
                        CategoryChip(stringResource(R.string.it_rec_category_nightlife), selectedCategory == "nightlife") { selectedCategory = "nightlife" }
                    }

                    // Tappable recommendation pills
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        filteredRecommendations.forEach { rec ->
                            val isCurrent = name == rec.name
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrent) Palette.Maroon else Palette.Petal,
                                contentColor = if (isCurrent) Palette.Tip else Palette.Maroon,
                                border = if (isCurrent) null else BorderStroke(1.dp, Palette.Line),
                                modifier = Modifier.clickable {
                                    name = rec.name
                                    placeType = rec.placeType
                                    timeSlot = rec.suggestedSlot
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = rec.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    )
                                    Text(
                                        text = "• ${timeSlotLabel(rec.suggestedSlot)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrent) Palette.Tip.copy(alpha = 0.8f) else Palette.Maroon.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Palette.Line)

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 100) name = it },
                    label = { Text(stringResource(R.string.it_place_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Question(stringResource(R.string.it_place_kind)) {
                    ChoiceChips(ADDABLE_PLACE_TYPES, { it == placeType }, { placeType = it }, { placeTypeLabel(it) })
                }
                Question(stringResource(R.string.it_when)) {
                    ChoiceChips(TIME_SLOTS, { it == timeSlot }, { timeSlot = it }, { timeSlotLabel(it) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, placeType, timeSlot) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.it_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
