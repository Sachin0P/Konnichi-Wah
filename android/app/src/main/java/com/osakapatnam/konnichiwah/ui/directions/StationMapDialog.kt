package com.osakapatnam.konnichiwah.ui.directions

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.StationFloorDto
import com.osakapatnam.konnichiwah.data.StationGuideDto
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.MapPin
import com.osakapatnam.konnichiwah.ui.components.PillButton
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.RouteMap
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.openWalkingDirections
import com.osakapatnam.konnichiwah.ui.theme.Palette

/** Authentic line brand colors across Tokyo, Kyoto, Osaka, and Nara networks. */
fun stationLineColor(line: String): Color {
    val lower = line.lowercase()
    return when {
        lower.contains("yamanote") -> Color(0xFF80C241) // Light green
        lower.contains("chuo") -> Color(0xFFF15A22) // Orange
        lower.contains("keihin") -> Color(0xFF00B2E5) // Sky blue
        lower.contains("shinkansen") -> Color(0xFF0068B7) // Blue
        lower.contains("marunouchi") || lower.contains("midosuji") -> Color(0xFFE60012) // Red
        lower.contains("ginza") -> Color(0xFFFF9500) // Orange
        lower.contains("hanzomon") -> Color(0xFF8F76D6) // Purple
        lower.contains("fukutoshin") -> Color(0xFF9C5E31) // Brown
        lower.contains("toei oedo") || lower.contains("oedo") -> Color(0xFFB6007A) // Magenta
        lower.contains("toei shinjuku") -> Color(0xFF6CBB5A) // Leaf green
        lower.contains("saikyo") -> Color(0xFF00AC9B) // Teal
        lower.contains("keiyo") -> Color(0xFFC9252F) // Keiyo red
        lower.contains("kyoto") || lower.contains("kobe") || lower.contains("tokaido main") -> Color(0xFF0068B7) // JR Kyoto blue
        lower.contains("nara") -> Color(0xFFC1A470) // JR Nara gold
        lower.contains("sagano") || lower.contains("san-in") -> Color(0xFF9370DB) // Sagano purple
        lower.contains("osaka loop") -> Color(0xFFE60012) // Osaka Loop red
        lower.contains("tanimachi") -> Color(0xFF800080) // Tanimachi purple
        lower.contains("yotsubashi") -> Color(0xFF0078D7) // Yotsubashi blue
        lower.contains("yamatoji") -> Color(0xFF00B26B) // Yamatoji green
        else -> Palette.Maroon
    }
}

/**
 * Rich multi-floor station map and navigation guide for major Japanese train hubs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationMapDialog(
    guide: StationGuideDto,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 0 = Overview, 1..n = Floor levels
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            color = Palette.Cream,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            ) {
                // Header with Station Name, Japanese Kanji, City badge, and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                guide.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Maroon,
                            )
                            Box(
                                modifier = Modifier
                                    .background(Palette.Petal, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    guide.city,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Palette.Maroon,
                                )
                            }
                        }
                        if (guide.name_ja != null) {
                            Text(
                                guide.name_ja,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Mute,
                            )
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.card_close), color = Palette.Mute, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Floor level switcher pills: [Overview, 2F, 1F, B1F, B5F...]
                if (guide.floors.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        item {
                            PillButton(
                                text = stringResource(R.string.dir_station_floor_overview),
                                onClick = { selectedTab = 0 },
                                tone = if (selectedTab == 0) Tone.MAROON else Tone.PETAL,
                            )
                        }
                        itemsIndexed(guide.floors) { index, floor ->
                            val tabIndex = index + 1
                            PillButton(
                                text = floor.level,
                                onClick = { selectedTab = tabIndex },
                                tone = if (selectedTab == tabIndex) Tone.MAROON else Tone.PETAL,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }

                // Main Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (selectedTab == 0 || guide.floors.isEmpty()) {
                        // --- OVERVIEW MODE ---

                        // 1. Vertical Floor Schematic Cross-Section
                        if (guide.floors.isNotEmpty()) {
                            item {
                                KCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        stringResource(R.string.dir_station_floors),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Ink,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for ((idx, floor) in guide.floors.withIndex()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Palette.Cream)
                                                    .border(BorderStroke(1.dp, Palette.Line), RoundedCornerShape(12.dp))
                                                    .clickable { selectedTab = idx + 1 }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Palette.Maroon, CircleShape),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        floor.level,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        floor.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Palette.Ink,
                                                    )
                                                    if (floor.lines.isNotEmpty()) {
                                                        Text(
                                                            floor.lines.joinToString(" · "),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Palette.Mute,
                                                            maxLines = 1,
                                                        )
                                                    }
                                                }
                                                Text("›", fontSize = 18.sp, color = Palette.Mute)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Interactive Area Map (MapLibre vector tiles)
                        item {
                            KCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(R.string.dir_station_area_map),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Ink,
                                )
                                Spacer(Modifier.height(8.dp))
                                RouteMap(
                                    lines = emptyList(),
                                    pins = listOf(MapPin(guide.lat, guide.lng, Palette.Maroon)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                )
                            }
                        }

                        // 3. Exits and connecting areas
                        if (guide.exits.isNotEmpty()) {
                            item {
                                KCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        stringResource(R.string.dir_station_guide_exits),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Ink,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (exit in guide.exits) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text("🚪", fontSize = 14.sp)
                                                Column {
                                                    Text(
                                                        exit.name + if (exit.name_ja != null) " (${exit.name_ja})" else "",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Palette.Ink,
                                                    )
                                                    Text(
                                                        exit.leads_to,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Palette.Mute,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Station Navigation Tips
                        if (guide.tips.isNotEmpty()) {
                            item {
                                KCard(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        stringResource(R.string.dir_station_guide_tips),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Palette.Ink,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (tip in guide.tips) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text("💡", fontSize = 13.sp)
                                                Text(
                                                    tip,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Palette.Ink,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // --- SPECIFIC FLOOR MODE ---
                        val floor = guide.floors.getOrNull(selectedTab - 1)
                        if (floor != null) {
                            item {
                                FloorDetailCard(floor = floor)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!guide.map_url.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, guide.map_url.toUri())
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = CircleShape,
                            border = BorderStroke(1.5.dp, Palette.Maroon),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Maroon),
                        ) {
                            Text(
                                "🗺️  " + stringResource(R.string.dir_station_official_map),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }

                    PrimaryButton(
                        text = stringResource(R.string.dir_walk_to_station, guide.name),
                        onClick = {
                            openWalkingDirections(
                                context = context,
                                destLat = guide.lat,
                                destLng = guide.lng,
                                destName = guide.name,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Detailed card displaying platforms, exits, and amenities on a specific floor level. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloorDetailCard(floor: StationFloorDto) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Floor header banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Palette.Card)
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(Palette.Maroon, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            floor.level,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    Text(
                        floor.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Ink,
                    )
                }
                if (floor.name_ja != null) {
                    Text(
                        floor.name_ja,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Mute,
                    )
                }
            }
        }

        // Platforms & Lines
        if (floor.lines.isNotEmpty()) {
            KCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.dir_station_platforms),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (line in floor.lines) {
                        val color = stationLineColor(line)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(color, CircleShape),
                            )
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Palette.Ink,
                            )
                        }
                    }
                }
            }
        }

        // Exits from this floor
        if (floor.exits.isNotEmpty()) {
            KCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.dir_station_guide_exits),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (exit in floor.exits) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("🚪", fontSize = 13.sp)
                            Text(exit, style = MaterialTheme.typography.bodySmall, color = Palette.Ink)
                        }
                    }
                }
            }
        }

        // Key Amenities on this floor
        if (floor.amenities.isNotEmpty()) {
            KCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.dir_station_amenities),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Ink,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (amenity in floor.amenities) {
                        val emoji = when {
                            amenity.contains("Locker", ignoreCase = true) || amenity.contains("लॉक", ignoreCase = true) -> "🛅"
                            amenity.contains("Toilet", ignoreCase = true) || amenity.contains("Restroom", ignoreCase = true) || amenity.contains("शौच", ignoreCase = true) -> "🚻"
                            amenity.contains("Ticket", ignoreCase = true) || amenity.contains("टिकट", ignoreCase = true) -> "🎫"
                            amenity.contains("Tourist", ignoreCase = true) || amenity.contains("Information", ignoreCase = true) || amenity.contains("पर्यटन", ignoreCase = true) -> "ℹ️"
                            amenity.contains("ATM", ignoreCase = true) || amenity.contains("एटीएम", ignoreCase = true) -> "🏧"
                            amenity.contains("Bus", ignoreCase = true) || amenity.contains("बस", ignoreCase = true) -> "🚌"
                            amenity.contains("Food", ignoreCase = true) || amenity.contains("Bento", ignoreCase = true) || amenity.contains("भोजन", ignoreCase = true) -> "🍱"
                            else -> "📍"
                        }
                        Box(
                            modifier = Modifier
                                .background(Palette.Petal, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(emoji, fontSize = 13.sp)
                                Text(
                                    amenity,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = Palette.Maroon,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floor tip
        if (!floor.tips.isNullOrBlank()) {
            KCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("💡", fontSize = 14.sp)
                    Text(
                        floor.tips,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Palette.Ink,
                    )
                }
            }
        }
    }
}
