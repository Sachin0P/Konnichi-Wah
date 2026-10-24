package com.osakapatnam.konnichiwah.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.ui.components.EnlargedPhrase
import com.osakapatnam.konnichiwah.ui.components.PhraseCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(
    onBack: () -> Unit,
    viewModel: CardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diet cards") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Show these to restaurant staff. They're checked Japanese and work without internet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.yours.isNotEmpty()) {
                item { Text("Your cards", style = MaterialTheme.typography.titleMedium) }
                items(state.yours, key = { it.id }) { card ->
                    PhraseCard(card.phrase(), onClick = { viewModel.enlarge(card) }, modifier = Modifier.fillMaxWidth())
                }
            }

            item { Text("All cards", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(state.others, key = { it.id }) { card ->
                PhraseCard(card.phrase(), onClick = { viewModel.enlarge(card) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    state.enlarged?.let { EnlargedPhrase(it, onDismiss = viewModel::dismissEnlarged) }
}
