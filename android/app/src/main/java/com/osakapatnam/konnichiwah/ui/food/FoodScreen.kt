package com.osakapatnam.konnichiwah.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.data.remote.FoodDto
import com.osakapatnam.konnichiwah.data.remote.FoodResultDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(
    onBack: () -> Unit,
    onDietCards: () -> Unit,
    viewModel: FoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food near ${viewModel.route.placeName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                FoodUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is FoodUiState.Error -> Message(s.message, button = "Try again", onClick = viewModel::load)
                is FoodUiState.Loaded ->
                    if (s.food.results.isEmpty()) {
                        Message(
                            "We don't know a restaurant suited to your diet within 1 km. Show your diet card at any restaurant, or try a convenience store.",
                            button = "Show my diet cards",
                            onClick = onDietCards,
                        )
                    } else {
                        Results(s.food, onDietCards)
                    }
            }
        }
    }
}

@Composable
private fun Results(food: FoodDto, onDietCards: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Ranked by Desi Match: how well each place suits your diet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(food.results, key = { it.id }) { RestaurantCard(it) }
        item {
            TextButton(onClick = onDietCards) { Text("Show my diet cards") }
        }
        item {
            // OpenStreetMap's licence requires this credit wherever its data is shown.
            Text(food.attribution, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RestaurantCard(r: FoodResultDto) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val score = r.desiMatch.score.coerceIn(1, 5)
            Text(
                "★".repeat(score) + "☆".repeat(5 - score) + "  Desi Match $score/5",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(r.displayName, style = MaterialTheme.typography.titleMedium)

            val cuisine = r.tags["cuisine"]?.split(";")?.joinToString(", ") { it.replace('_', ' ').replaceFirstChar(Char::uppercase) }
            Text(
                listOfNotNull(cuisine, distanceLabel(r.distanceM)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )

            if (r.desiMatch.reasons.isNotEmpty()) {
                Text(r.desiMatch.reasons.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            r.desiMatch.caution?.let {
                Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            r.tags["opening_hours"]?.let {
                Text("Hours (from OpenStreetMap, may be out of date): $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// "300 m · about 4 min walk", at a gentle 80 metres a minute.
private fun distanceLabel(metres: Int): String {
    val distance = if (metres < 1000) "$metres m" else "%.1f km".format(metres / 1000.0)
    val minutes = maxOf(1, metres / 80)
    return "$distance · about $minutes min walk"
}

@Composable
private fun Message(text: String, button: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center)
        Button(onClick = onClick) { Text(button) }
    }
}
