package com.osakapatnam.konnichiwah.ui.suggestions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.data.remote.SuggestionDto
import com.osakapatnam.konnichiwah.ui.components.BackLink
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.Pill
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.kindLabel
import com.osakapatnam.konnichiwah.ui.placeTypeLabel
import com.osakapatnam.konnichiwah.ui.theme.Palette
import com.osakapatnam.konnichiwah.ui.userMessage

@Composable
fun SuggestionsScreen(
    onEditAnswers: () -> Unit,
    onItineraryBuilt: () -> Unit,
    viewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show a failed build as a snackbar, once.
    val buildError = (state as? SuggestionsUiState.Loaded)?.buildError?.userMessage()
    LaunchedEffect(buildError) {
        if (buildError != null) {
            snackbarHostState.showSnackbar(buildError)
            viewModel.buildErrorShown()
        }
    }

    Scaffold(
        topBar = { BackLink(stringResource(R.string.sug_edit), onClick = onEditAnswers, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val loaded = state as? SuggestionsUiState.Loaded
            if (loaded != null) {
                PrimaryButton(
                    if (loaded.building) stringResource(R.string.sug_building) else stringResource(R.string.sug_build, loaded.selectedIds.size),
                    onClick = { viewModel.buildItinerary(onItineraryBuilt) },
                    enabled = loaded.selectedIds.isNotEmpty() && !loaded.building,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                SuggestionsUiState.Loading -> LoadingContent()
                is SuggestionsUiState.Error -> ErrorContent(s.error?.userMessage() ?: stringResource(R.string.sug_answer_first), onRetry = viewModel::load)
                is SuggestionsUiState.Loaded -> PlanContent(s.plan, s.selectedIds, onToggle = viewModel::toggle)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Palette.Sun, trackColor = Palette.Petal)
        Text(stringResource(R.string.sug_planning), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.sug_wait),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center)
        PrimaryButton(stringResource(R.string.try_again), onClick = onRetry)
    }
}

@Composable
private fun PlanContent(plan: PlanDto, selectedIds: Set<String>, onToggle: (String) -> Unit) {
    // Group the cards by city, keeping the order the planner gave them in.
    val byCity = plan.suggestions.groupBy { it.city }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.sug_title), subtitle = stringResource(R.string.sug_subtitle)) }
        item { RouteCard(plan) }

        byCity.forEach { (city, suggestions) ->
            item(key = "header-$city") {
                Text(city, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            }
            items(suggestions, key = { it.id }) { suggestion ->
                SuggestionCard(suggestion, selected = suggestion.id in selectedIds, onToggle = { onToggle(suggestion.id) })
            }
        }
    }
}

// The planner's route, on the home screen's pink.
@Composable
private fun RouteCard(plan: PlanDto) {
    KCard(modifier = Modifier.fillMaxWidth(), color = Palette.HeroPink, border = BorderStroke(0.dp, Color.Transparent)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val totalDays = plan.cities.sumOf { it.days }
            Text(stringResource(R.string.sug_route, totalDays), style = MaterialTheme.typography.titleLarge)
            plan.cities.forEach { city ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${city.name} · ${pluralStringResource(R.plurals.days, city.days, city.days)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Palette.Maroon,
                    )
                    Text(city.reason, style = MaterialTheme.typography.bodyMedium, color = Palette.Ink)
                }
            }
            // Be honest when the AI planner wasn't used, without alarming anyone.
            if (plan.source == "catalogue") {
                Text(
                    stringResource(R.string.sug_catalogue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// A suggestion the traveller can pick. Picked cards get a red border and a
// filled tick.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionCard(suggestion: SuggestionDto, selected: Boolean, onToggle: () -> Unit) {
    KCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                role = Role.Checkbox
            },
        onClick = onToggle,
        border = if (selected) BorderStroke(2.dp, Palette.Sun) else BorderStroke(1.dp, Palette.Line),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tone = if (suggestion.kind == "food") Tone.GREEN else Tone.PETAL
                    Pill(kindLabel(suggestion.kind), tone)
                    Pill(placeTypeLabel(suggestion.placeType), Tone.ORANGE)
                }
                Text(suggestion.name, style = MaterialTheme.typography.titleSmall)
                Text(suggestion.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Tick(selected, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun Tick(on: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(if (on) Palette.Sun else Palette.Petal, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) {
            Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
