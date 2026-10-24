package com.osakapatnam.konnichiwah.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.FoodDto
import com.osakapatnam.konnichiwah.data.remote.FoodResultDto
import com.osakapatnam.konnichiwah.ui.components.BackLink
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.Pill
import com.osakapatnam.konnichiwah.ui.components.PillButton
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.theme.Palette
import com.osakapatnam.konnichiwah.ui.userMessage

@Composable
fun FoodScreen(
    onBack: () -> Unit,
    onDietCards: () -> Unit,
    viewModel: FoodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        BackLink(stringResource(R.string.back), onClick = onBack, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        ScreenHeader(
            stringResource(R.string.food_title, viewModel.route.placeName),
            subtitle = stringResource(R.string.food_ranked),
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            when (val s = state) {
                FoodUiState.Loading -> CircularProgressIndicator(color = Palette.Sun, modifier = Modifier.align(Alignment.Center))
                is FoodUiState.Error -> Message(s.error.userMessage(), button = stringResource(R.string.try_again), onClick = viewModel::load)
                is FoodUiState.Loaded ->
                    if (s.food.results.isEmpty()) {
                        Message(
                            stringResource(R.string.food_none),
                            button = stringResource(R.string.show_my_diet_cards),
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
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(food.results, key = { it.id }) { RestaurantCard(it) }
        item {
            PillButton("🥗  " + stringResource(R.string.show_my_diet_cards), onClick = onDietCards)
        }
        item {
            // OpenStreetMap's licence requires this credit wherever its data is shown.
            Text(food.attribution, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// A restaurant with its Desi Match score as stars and a tag: green for a
// good match (4–5), orange for "check first" (1–3).
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestaurantCard(r: FoodResultDto) {
    KCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val score = r.desiMatch.score.coerceIn(1, 5)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(stringResource(R.string.food_score, score), if (score >= 4) Tone.GREEN else Tone.ORANGE)
                Text(
                    "★".repeat(score) + "☆".repeat(5 - score),
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Sun,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Text(r.displayName, style = MaterialTheme.typography.titleMedium)

            val cuisine = r.tags["cuisine"]?.split(";")?.joinToString(", ") { it.replace('_', ' ').replaceFirstChar(Char::uppercase) }
            Text(
                listOfNotNull(cuisine, distanceLabel(r.distanceM)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (r.desiMatch.reasons.isNotEmpty()) {
                Text(r.desiMatch.reasons.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
            r.desiMatch.caution?.let {
                Pill("⚠ $it", Tone.PETAL)
            }
            r.tags["opening_hours"]?.let {
                Text(stringResource(R.string.food_hours, it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// "300 m · about 4 min walk", at a gentle 80 metres a minute.
@Composable
private fun distanceLabel(metres: Int): String {
    val distance = if (metres < 1000) stringResource(R.string.food_metres, metres) else stringResource(R.string.food_km, metres / 1000.0)
    val minutes = maxOf(1, metres / 80)
    return stringResource(R.string.food_walk, distance, minutes)
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
        PrimaryButton(button, onClick = onClick)
    }
}
