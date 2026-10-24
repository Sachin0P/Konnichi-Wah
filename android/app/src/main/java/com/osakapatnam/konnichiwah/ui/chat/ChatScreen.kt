package com.osakapatnam.konnichiwah.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.data.remote.PhraseDto
import com.osakapatnam.konnichiwah.ui.components.EnlargedPhrase
import com.osakapatnam.konnichiwah.ui.components.PhraseCard

// Starter questions for an empty chat: one per thing the companion is good at.
private val SUGGESTIONS = listOf(
    "How do I get to my next stop?",
    "What should I know before visiting a temple?",
    "How do I tell the waiter what I can't eat?",
    "How do I ask where the toilet is?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Keep the newest message (or the "Thinking…" bubble) in view.
    val itemCount = state.entries.size + if (state.sending) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask Konnichi") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        bottomBar = {
            InputBar(
                input = state.input,
                sending = state.sending,
                onInput = viewModel::setInput,
                onSend = { viewModel.send() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.days.isNotEmpty()) {
                DayPicker(state, onSelect = viewModel::selectDay)
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.entries.isEmpty()) {
                    Suggestions(onAsk = viewModel::send)
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.entries, key = { it.id }) { entry ->
                            Message(entry, onEnlarge = viewModel::enlarge)
                        }
                        if (state.sending) {
                            item(key = "thinking") { Bubble(fromUser = false) { Text("Thinking…") } }
                        }
                    }
                }
            }
        }
    }

    state.enlarged?.let { phrase ->
        EnlargedPhrase(phrase, onDismiss = viewModel::dismissEnlarged)
    }
}

// Which day the questions are about; its stops are sent as context.
@Composable
private fun DayPicker(state: ChatUiState, onSelect: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.days) { index, day ->
            FilterChip(
                selected = index == state.selectedDay,
                onClick = { onSelect(index) },
                label = { Text("Day ${day.index} · ${day.city}") },
            )
        }
    }
}

@Composable
private fun Suggestions(onAsk: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Ask me anything about your trip", style = MaterialTheme.typography.titleMedium)
        Text(
            "Train routes use live timetables, and etiquette and diet cards come from our checked rulebook.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        SUGGESTIONS.forEach { question ->
            SuggestionChip(onClick = { onAsk(question) }, label = { Text(question) })
        }
    }
}

@Composable
private fun Message(entry: ChatEntry, onEnlarge: (PhraseDto) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (entry.fromUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Bubble(fromUser = entry.fromUser, isError = entry.isError) { Text(entry.text) }

        entry.phrases.forEach { phrase ->
            PhraseCard(phrase, onClick = { onEnlarge(phrase) }, modifier = Modifier.widthIn(max = 320.dp))
        }

        sourceLabel(entry.toolsUsed)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Tells the traveller where an answer came from, which is what makes it
// trustworthy: real timetables and our own rulebook, not the AI's memory.
private fun sourceLabel(toolsUsed: List<String>): String? {
    val labels = toolsUsed.mapNotNull {
        when (it) {
            "get_train_route" -> "Live train times"
            "get_etiquette" -> "From our etiquette rulebook"
            "show_diet_card" -> "Checked diet card"
            "find_food" -> "Desi Match from OpenStreetMap"
            "find_places" -> "Shops from OpenStreetMap"
            else -> null
        }
    }
    return labels.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@Composable
private fun Bubble(fromUser: Boolean, isError: Boolean = false, content: @Composable () -> Unit) {
    val colour = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        fromUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .background(colour, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        content()
    }
}

@Composable
private fun InputBar(input: String, sending: Boolean, onInput: (String) -> Unit, onSend: () -> Unit) {
    // Pad for the nav bar and the keyboard, so the field stays visible while
    // typing (navigationBarsPadding first, so imePadding doesn't add it twice).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask about trains, food, manners…") },
            maxLines = 4,
        )
        Button(onClick = onSend, enabled = input.isNotBlank() && !sending) { Text("Send") }
    }
}
