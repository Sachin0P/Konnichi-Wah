package com.osakapatnam.konnichiwah.ui.suggestions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.data.remote.SuggestionDto
import com.osakapatnam.konnichiwah.ui.kindLabel
import com.osakapatnam.konnichiwah.ui.placeTypeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionsScreen(
    onEditAnswers: () -> Unit,
    onItineraryBuilt: () -> Unit,
    viewModel: SuggestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show a failed build as a snackbar, once.
    val buildError = (state as? SuggestionsUiState.Loaded)?.buildError
    LaunchedEffect(buildError) {
        if (buildError != null) {
            snackbarHostState.showSnackbar(buildError)
            viewModel.buildErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your suggestions") },
                navigationIcon = { TextButton(onClick = onEditAnswers) { Text("Edit") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val loaded = state as? SuggestionsUiState.Loaded
            if (loaded != null) {
                Button(
                    onClick = { viewModel.buildItinerary(onItineraryBuilt) },
                    enabled = loaded.selectedIds.isNotEmpty() && !loaded.building,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Text(if (loaded.building) "Building your itinerary…" else "Build itinerary (${loaded.selectedIds.size} selected)")
                }
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
                is SuggestionsUiState.Error -> ErrorContent(s.message, onRetry = viewModel::load)
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
        CircularProgressIndicator()
        Text("Planning your trip…", style = MaterialTheme.typography.titleMedium)
        Text(
            "This can take up to 20 seconds.",
            style = MaterialTheme.typography.bodyMedium,
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
        Button(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun PlanContent(plan: PlanDto, selectedIds: Set<String>, onToggle: (String) -> Unit) {
    // Group the cards by city, keeping the order the planner gave them in.
    val byCity = plan.suggestions.groupBy { it.city }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { RouteCard(plan) }

        byCity.forEach { (city, suggestions) ->
            item(key = "header-$city") {
                Text(city, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
            items(suggestions, key = { it.id }) { suggestion ->
                SuggestionCard(suggestion, selected = suggestion.id in selectedIds, onToggle = { onToggle(suggestion.id) })
            }
        }
    }
}

@Composable
private fun RouteCard(plan: PlanDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val totalDays = plan.cities.sumOf { it.days }
            Text("Your $totalDays-day route", style = MaterialTheme.typography.titleMedium)
            plan.cities.forEach { city ->
                Text("${city.name} · ${city.days} ${if (city.days == 1) "day" else "days"}", style = MaterialTheme.typography.labelLarge)
                Text(city.reason, style = MaterialTheme.typography.bodySmall)
            }
            // Be honest when the AI planner wasn't used, without alarming anyone.
            if (plan.source == "catalogue") {
                Text(
                    "Our AI planner was busy, so these are our hand-picked suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: SuggestionDto, selected: Boolean, onToggle: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${kindLabel(suggestion.kind)} · ${placeTypeLabel(suggestion.placeType)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(suggestion.name, style = MaterialTheme.typography.titleSmall)
                Text(suggestion.reason, style = MaterialTheme.typography.bodySmall)
            }
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        }
    }
}
