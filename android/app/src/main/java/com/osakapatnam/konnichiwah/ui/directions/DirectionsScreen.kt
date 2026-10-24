package com.osakapatnam.konnichiwah.ui.directions

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.JourneyDto
import com.osakapatnam.konnichiwah.data.remote.JourneyLegDto
import com.osakapatnam.konnichiwah.data.remote.JourneyStopDto
import com.osakapatnam.konnichiwah.data.remote.PlaceSuggestionDto
import com.osakapatnam.konnichiwah.ui.components.BackLink
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.MapLine
import com.osakapatnam.konnichiwah.ui.components.MapPin
import com.osakapatnam.konnichiwah.ui.components.PillButton
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.RouteMap
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.TipQuote
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.openWalkingDirections
import com.osakapatnam.konnichiwah.ui.theme.Palette
import com.osakapatnam.konnichiwah.ui.userMessage

// How to get somewhere by train, subway and bus: up to three journeys, each
// on a map and as cards a traveller can follow on the platform: which
// station to walk to and which exit to go in by, which line (in its colour,
// with the code on the signs) and towards where, how many stops, and which
// exit to leave by.
@Composable
fun DirectionsScreen(onBack: () -> Unit, viewModel: DirectionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Ask for location the first time it's needed, then search.
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.search() else viewModel.locationDenied()
    }
    val search = {
        if (state.fromHere && !viewModel.hasLocationPermission) {
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else {
            viewModel.search()
        }
    }

    val journeys = state.result?.journeys.orEmpty()
    val journey = journeys.getOrNull(state.selected)
    val problem = state.apiError?.userMessage() ?: state.message?.let { stringResource(it) }

    var activeGuideSteps by remember { mutableStateOf<List<GuideStep>?>(null) }
    var guideDestName by remember { mutableStateOf("") }
    var guideDestNameJa by remember { mutableStateOf("") }
    var guideDestLat by remember { mutableStateOf<Double?>(null) }
    var guideDestLng by remember { mutableStateOf<Double?>(null) }

    val matchingGuides = remember(journey, state.stationGuides) {
        if (journey == null) emptyList() else {
            val names = journey.legs.flatMap { listOf(it.from.name, it.to.name) }.map { it.lowercase() }
            state.stationGuides.filter { guide ->
                val base = guide.name.lowercase().replace("station", "").trim()
                names.any { n -> n.contains(base) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackLink(stringResource(R.string.back), onBack) }
        item { ScreenHeader(stringResource(R.string.dir_title), stringResource(R.string.dir_subtitle)) }

        item {
            KCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.dir_from), style = MaterialTheme.typography.labelLarge)
                if (state.fromHere) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(stringResource(R.string.dir_my_location), onClick = {}, tone = Tone.GREEN)
                        TextButton(onClick = { viewModel.setFromHere(false) }) { Text(stringResource(R.string.dir_type_place)) }
                    }
                } else {
                    PlaceBox(
                        value = state.fromText,
                        onValueChange = viewModel::setFrom,
                        hint = stringResource(R.string.dir_from_hint),
                        suggestions = state.fromSuggestions,
                        onPick = { suggestion, label -> viewModel.pick(suggestion, label, from = true) },
                    )
                    TextButton(onClick = { viewModel.setFromHere(true) }) { Text(stringResource(R.string.dir_use_location)) }
                }

                Text(stringResource(R.string.dir_to), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                PlaceBox(
                    value = state.toText,
                    onValueChange = viewModel::setTo,
                    hint = stringResource(R.string.dir_to_hint),
                    suggestions = state.toSuggestions,
                    onPick = { suggestion, label -> viewModel.pick(suggestion, label, from = false) },
                )

                PrimaryButton(
                    stringResource(if (state.loading) R.string.dir_finding else R.string.dir_find),
                    onClick = search,
                    enabled = state.canSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }

        if (problem != null) {
            item { Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }

        if (journeys.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(journeys) { i, j ->
                        PillButton(optionLabel(j), onClick = { viewModel.select(i) }, tone = if (i == state.selected) Tone.MAROON else Tone.PETAL)
                    }
                }
            }
        }

        if (journey != null) {
            item {
                if (journeys.size == 1) Text(optionLabel(journey), style = MaterialTheme.typography.titleMedium)
                RouteMap(
                    lines = journey.legs.map { leg ->
                        MapLine(leg.path.mapNotNull { p -> if (p.size == 2) p[0] to p[1] else null }, legColor(leg), walk = leg.mode == "walk")
                    },
                    pins = listOf(
                        MapPin(journey.legs.first().from.lat, journey.legs.first().from.lng, Palette.Green),
                        MapPin(journey.legs.last().to.lat, journey.legs.last().to.lng, Palette.Sun),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // Short enough to scroll past: drags on the map move the map.
                        .height(240.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        PillButton(
                            stringResource(R.string.dir_try_guide),
                            onClick = {
                                activeGuideSteps = sampleGuide
                                guideDestName = "Asakusa"
                                guideDestNameJa = "浅草"
                                guideDestLat = 35.7118
                                guideDestLng = 139.7967
                            },
                            tone = Tone.ORANGE,
                        )
                    }
                    items(matchingGuides) { guide ->
                        PillButton(
                            stringResource(R.string.dir_station_guide_btn, guide.name),
                            onClick = { viewModel.viewGuide(guide) },
                            tone = Tone.MAROON,
                        )
                    }
                }
            }

            itemsIndexed(journey.legs) { i, leg ->
                LegCard(
                    leg = leg,
                    first = i == 0,
                    last = i == journey.legs.lastIndex,
                    steps = rideSteps(journey.legs, i),
                    onOpenVisualGuide = {
                        activeGuideSteps = buildGuideFromLeg(leg)
                        guideDestName = leg.to.name
                        guideDestNameJa = leg.to.nameJa.orEmpty()
                        guideDestLat = leg.to.lat
                        guideDestLng = leg.to.lng
                    },
                )
            }

            // Station numbers are the easiest way to follow a line, once
            // someone has explained them.
            journey.legs.firstNotNullOfOrNull { leg -> leg.from.code?.takeIf { leg.mode != "walk" && it.isNotBlank() } }?.let { code ->
                item {
                    TipQuote { Text(stringResource(R.string.dir_station_numbers, code), style = MaterialTheme.typography.bodyMedium) }
                }
            }

            item {
                Text(
                    state.result?.attribution.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    state.viewingGuide?.let { guide ->
        AlertDialog(
            onDismissRequest = { viewModel.viewGuide(null) },
            confirmButton = {
                TextButton(onClick = { viewModel.viewGuide(null) }) {
                    Text(stringResource(R.string.card_close), color = Palette.Sun)
                }
            },
            title = {
                Text(
                    guide.name + if (guide.name_ja != null) " (${guide.name_ja})" else "",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Maroon,
                )
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (guide.lines.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.dir_station_guide_lines) + ": " + guide.lines.joinToString(", "), style = MaterialTheme.typography.labelMedium, color = Palette.Mute)
                        }
                    }
                    if (guide.exits.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.dir_station_guide_exits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.Ink)
                        }
                        items(guide.exits) { exit ->
                            Text("• ${exit.name}: ${exit.leads_to}", style = MaterialTheme.typography.bodySmall, color = Palette.Ink)
                        }
                    }
                    if (guide.tips.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.dir_station_guide_tips), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.Ink)
                        }
                        items(guide.tips) { tip ->
                            Text("• $tip", style = MaterialTheme.typography.bodySmall, color = Palette.Ink)
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                openWalkingDirections(
                                    context = context,
                                    destLat = guide.lat,
                                    destLng = guide.lng,
                                    destName = guide.name,
                                )
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, Palette.Green),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Green),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("🚶", fontSize = 16.sp)
                                Text(
                                    stringResource(R.string.dir_walk_to_station, guide.name),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                activeGuideSteps = sampleGuide
                                guideDestName = guide.name
                                guideDestNameJa = guide.name_ja.orEmpty()
                                guideDestLat = guide.lat
                                guideDestLng = guide.lng
                                viewModel.viewGuide(null)
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, Palette.Sun),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Sun),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LifesaverIcon(color = Palette.Sun, modifier = Modifier.size(16.dp))
                                Text(
                                    stringResource(R.string.dir_visual_guide_btn),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Palette.Card,
        )
    }

    activeGuideSteps?.let { steps ->
        RouteGuideDialog(
            steps = steps,
            destinationName = guideDestName,
            destinationNameJa = guideDestNameJa,
            destinationLat = guideDestLat,
            destinationLng = guideDestLng,
            onDismiss = { activeGuideSteps = null },
        )
    }
}

// A place box with a dropdown of matching places under it, updated as the
// traveller types. The list is drawn in the page rather than as a popup, so
// it never covers the keyboard or fights the text box for focus.
@Composable
private fun PlaceBox(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    suggestions: List<PlaceSuggestionDto>,
    onPick: (PlaceSuggestionDto, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Column {
                    suggestions.forEachIndexed { i, suggestion ->
                        val label = suggestionLabel(suggestion)
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(suggestion, label) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(kindEmoji(suggestion.kind), fontSize = 18.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                val detail = listOfNotNull(
                                    suggestion.nameJa?.takeIf { it.isNotBlank() && it != suggestion.name },
                                    if (suggestion.kind == TRIP_STOP) stringResource(R.string.dir_on_your_trip) else null,
                                    suggestion.detail?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ")
                                if (detail.isNotEmpty()) {
                                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// "Shinjuku Station" for stations, so they read like the signs; other places
// by their own name.
@Composable
private fun suggestionLabel(s: PlaceSuggestionDto): String =
    if (s.kind == "station") stringResource(R.string.dir_station, s.name) else s.name

private fun kindEmoji(kind: String): String = when (kind) {
    "station" -> "🚉"
    TRIP_STOP -> "⭐"
    "place_of_worship", "shrine", "temple", "religious" -> "⛩️"
    "hotel", "hostel", "guest_house" -> "🏨"
    "hospital", "clinic" -> "🏥"
    "restaurant", "fast_food", "cafe", "pub", "bar" -> "🍽️"
    "park", "garden" -> "🌳"
    "museum", "gallery" -> "🏛️"
    "tower", "attraction", "viewpoint", "castle" -> "🗼"
    "mall", "department_store", "supermarket" -> "🛍️"
    else -> "📍"
}

// "09:04–09:46 · 42 min · 1 change".
@Composable
private fun optionLabel(j: JourneyDto): String {
    val changes = if (j.transfers == 0) stringResource(R.string.dir_direct) else pluralStringResource(R.plurals.dir_changes, j.transfers, j.transfers)
    return stringResource(R.string.dir_option, j.departs, j.arrives, j.minutes, changes)
}

private val walkGrey = Color(0xFF7A6868)

private fun legColor(leg: JourneyLegDto): Color =
    if (leg.mode == "walk") walkGrey else leg.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: Palette.Maroon

private fun modeEmoji(mode: String): String = when (mode) {
    "walk" -> "🚶"
    "shinkansen" -> "🚄"
    "subway" -> "🚇"
    "tram" -> "🚋"
    "bus" -> "🚌"
    "ferry" -> "⛴️"
    else -> "🚆"
}

// "Sendagaya 千駄ケ谷 (JB12)": the English name, the Japanese on the signs,
// and the station number.
private fun stopLabel(s: JourneyStopDto): String = buildString {
    append(s.name)
    if (!s.nameJa.isNullOrBlank()) append(" ").append(s.nameJa)
    if (!s.code.isNullOrBlank()) append(" (").append(s.code).append(")")
}

// What to do at the station for the ride at legs[i], in order: which signs
// to follow (by colour and letter), the ticket gate, which platform and
// direction, and where to get off. Built from the timetable data only, so
// nothing is guessed. Empty for walks.
@Composable
private fun rideSteps(legs: List<JourneyLegDto>, i: Int): List<String> {
    val leg = legs[i]
    if (leg.mode == "walk") return emptyList()

    val steps = mutableListOf<String>()
    val line = leg.line ?: leg.operator.orEmpty()
    val colour = LineColour.nearest(leg.color)?.let { stringResource(it.label) }
    val code = leg.lineCode?.takeIf { it.isNotBlank() }
    steps += when {
        colour != null && code != null -> stringResource(R.string.dir_step_signs, colour, code, line)
        colour != null -> stringResource(R.string.dir_step_signs_no_code, colour, line)
        else -> stringResource(R.string.dir_step_signs_plain, line)
    }

    // The ticket gate on the way in to the first train; on a change, only
    // when the next line belongs to another company (JR to Tokyo Metro).
    val previousRide = legs.subList(0, i).lastOrNull { it.mode != "walk" }
    when {
        previousRide == null -> steps += stringResource(R.string.dir_step_gate)
        previousRide.operator != null && leg.operator != null && previousRide.operator != leg.operator ->
            steps += stringResource(R.string.dir_step_new_company)
    }

    // The platform comes from the walk to the train, when there is one.
    val platform = legs.getOrNull(i - 1)?.takeIf { it.mode == "walk" }?.platform
    val towards = leg.towards?.takeIf { it.isNotBlank() }
    when {
        platform != null && towards != null -> steps += stringResource(R.string.dir_step_platform_towards, platform, towards)
        platform != null -> steps += stringResource(R.string.dir_step_platform, platform)
        towards != null -> steps += stringResource(R.string.dir_step_towards, towards)
    }

    steps += stringResource(R.string.dir_step_get_off, stopLabel(leg.to))
    return steps
}

@Composable
private fun LegCard(
    leg: JourneyLegDto,
    first: Boolean,
    last: Boolean,
    steps: List<String>,
    onOpenVisualGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    val walkAction: (() -> Unit)? = if (leg.mode == "walk") {
        {
            openWalkingDirections(
                context = context,
                destLat = leg.to.lat,
                destLng = leg.to.lng,
                destName = stopLabel(leg.to),
                originLat = leg.from.lat.takeUnless { it.isNaN() || it == 0.0 },
                originLng = leg.from.lng.takeUnless { it.isNaN() || it == 0.0 },
            )
        }
    } else null

    KCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = walkAction,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LineBadge(leg)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (leg.mode == "walk") {
                    val distance = if (leg.distanceM < 1000) stringResource(R.string.food_metres, leg.distanceM) else stringResource(R.string.food_km, leg.distanceM / 1000.0)
                    val title = if (first || last) {
                        stringResource(R.string.dir_walk, leg.minutes, distance, stopLabel(leg.to))
                    } else {
                        stringResource(R.string.dir_change, leg.minutes, stopLabel(leg.to))
                    }
                    Text(title, style = MaterialTheme.typography.titleSmall)

                    // The exit: walking out of a station on every walk but
                    // the first, which walks into one.
                    leg.exit?.let {
                        Text(stringResource(if (first) R.string.dir_enter_exit else R.string.dir_leave_exit, it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Palette.Maroon)
                    }
                    leg.platform?.let { Text(stringResource(R.string.dir_platform, it), style = MaterialTheme.typography.bodyMedium) }
                    if (leg.via.isNotEmpty()) {
                        Text(stringResource(R.string.dir_via, leg.via.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PillButton(
                            text = "🚶  " + if (first || !leg.to.code.isNullOrBlank() || leg.to.name.contains("Station", ignoreCase = true)) {
                                stringResource(R.string.dir_walk_to_station, stopLabel(leg.to))
                            } else {
                                stringResource(R.string.dir_open_walk)
                            },
                            onClick = { walkAction?.invoke() },
                            tone = Tone.GREEN,
                        )
                    }
                } else {
                    Text(leg.line ?: leg.operator.orEmpty(), style = MaterialTheme.typography.titleSmall)
                    if (!leg.lineJa.isNullOrBlank()) {
                        Text(leg.lineJa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    leg.towards?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(R.string.dir_towards, it), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("${leg.departs}  ${stopLabel(leg.from)}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text("${leg.arrives}  ${stopLabel(leg.to)}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        pluralStringResource(R.plurals.dir_stops, leg.stops, leg.stops, leg.minutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (steps.isNotEmpty()) {
                        Text(stringResource(R.string.dir_steps), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        steps.forEachIndexed { n, step ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(legColor(leg), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("${n + 1}", color = textOn(legColor(leg)), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenVisualGuide,
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, Palette.Sun),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Sun),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LifesaverIcon(color = Palette.Sun, modifier = Modifier.size(16.dp))
                            Text(
                                stringResource(R.string.dir_visual_guide_btn),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// A round badge in the line's colour with its code ("JB"), like the signs;
// or the mode's emoji when there's no code (walks, most buses).
@Composable
private fun LineBadge(leg: JourneyLegDto) {
    val code = leg.lineCode?.takeIf { leg.mode != "walk" && it.isNotBlank() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(if (code != null) legColor(leg) else Palette.Petal, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (code != null) {
            Text(code, color = textOn(legColor(leg)), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else {
            Text(modeEmoji(leg.mode), fontSize = 18.sp)
        }
    }
}

// Black or white text, whichever reads better on the line's colour (the
// Chuo-Sobu Line is yellow, the Tsukuba Express dark blue).
private fun textOn(background: Color): Color {
    val luminance = 0.299 * background.red + 0.587 * background.green + 0.114 * background.blue
    return if (luminance > 0.6) Color(0xFF1B1B1B) else Color.White
}
