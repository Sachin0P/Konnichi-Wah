package com.osakapatnam.konnichiwah.ui.nearby

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.PlaceSummaryDto
import com.osakapatnam.konnichiwah.ui.components.BackLink
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.MapPin
import com.osakapatnam.konnichiwah.ui.components.Pill
import com.osakapatnam.konnichiwah.ui.components.PillButton
import com.osakapatnam.konnichiwah.ui.components.RouteMap
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.TipQuote
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.theme.Palette
import com.osakapatnam.konnichiwah.ui.userMessage

// Hospitals, pharmacies, police boxes, ATMs, toilets and convenience stores
// near the traveller, or anything else by search, on a map and as a list.
// Each place can be called (when OpenStreetMap has its number) or navigated
// to with the directions screen.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearbyScreen(
    onBack: () -> Unit,
    onDirections: (PlaceSummaryDto) -> Unit,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // What to search for once the permission is granted.
    var pending by remember { mutableStateOf<String?>(null) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.search(pending) else viewModel.locationDenied()
    }
    val search: (String?) -> Unit = { kind ->
        if (viewModel.hasLocationPermission) {
            viewModel.search(kind)
        } else {
            pending = kind
            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val results = state.result?.results.orEmpty()
    val problem = state.apiError?.userMessage() ?: state.message?.let { stringResource(it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackLink(stringResource(R.string.back), onBack) }
        item { ScreenHeader(stringResource(R.string.near_title), stringResource(R.string.near_subtitle)) }

        // Always on screen: in an emergency, the numbers matter more than
        // the map.
        item {
            TipQuote {
                Text(stringResource(R.string.near_emergency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HELP_KINDS.forEach { kind ->
                    PillButton(
                        stringResource(kindLabel(kind)),
                        onClick = { search(kind) },
                        tone = if (kind == state.kind) Tone.MAROON else Tone.PETAL,
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.near_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                PillButton(stringResource(R.string.near_search), onClick = { search(null) }, enabled = state.query.isNotBlank())
            }
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        }
        if (problem != null) {
            item { Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }

        state.result?.takeIf { results.isNotEmpty() }?.let { result ->
            item {
                RouteMap(
                    lines = emptyList(),
                    pins = listOf(MapPin(result.near.lat, result.near.lng, Palette.Green)) + results.map { MapPin(it.lat, it.lng, Palette.Sun) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(20.dp)),
                )
            }
        }

        items(results) { place ->
            PlaceCard(
                place,
                onCall = { phone -> runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, ("tel:" + phone.filter { it.isDigit() || it == '+' }).toUri())) } },
                onDirections = { onDirections(place) },
            )
        }

        state.result?.takeIf { results.isNotEmpty() }?.let {
            item { Text(it.attribution, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@StringRes
private fun kindLabel(kind: String): Int = when (kind) {
    "hospital" -> R.string.near_kind_hospital
    "clinic" -> R.string.near_kind_clinic
    "pharmacy" -> R.string.near_kind_pharmacy
    "police" -> R.string.near_kind_police
    "atm" -> R.string.near_kind_atm
    "toilet" -> R.string.near_kind_toilet
    else -> R.string.near_kind_convenience_store
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceCard(place: PlaceSummaryDto, onCall: (String) -> Unit, onDirections: () -> Unit) {
    KCard(modifier = Modifier.fillMaxWidth()) {
        Text(place.name, style = MaterialTheme.typography.titleSmall)
        if (!place.nameJa.isNullOrBlank()) {
            Text(place.nameJa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val distance = if (place.distanceM < 1000) stringResource(R.string.food_metres, place.distanceM) else stringResource(R.string.food_km, place.distanceM / 1000.0)
        Text(stringResource(R.string.food_walk, distance, maxOf(1, place.distanceM / 80)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        place.address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        place.openingHours?.let {
            Text(stringResource(R.string.food_hours, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            if (place.emergency) Pill(stringResource(R.string.near_emergency_dept), Tone.ORANGE)
            place.phone?.let { phone -> PillButton("📞 " + stringResource(R.string.near_call), onClick = { onCall(phone) }, tone = Tone.GREEN) }
            PillButton(stringResource(R.string.near_directions), onClick = onDirections)
        }
    }
}
