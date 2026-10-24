package com.osakapatnam.konnichiwah.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.ItineraryActionDto
import com.osakapatnam.konnichiwah.data.remote.PhraseDto
import com.osakapatnam.konnichiwah.ui.components.EnlargedPhrase
import com.osakapatnam.konnichiwah.ui.components.Pill
import com.osakapatnam.konnichiwah.ui.components.PillButton
import com.osakapatnam.konnichiwah.ui.components.PhraseCard
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.Tone
import com.osakapatnam.konnichiwah.ui.theme.Palette
import com.osakapatnam.konnichiwah.ui.userMessage

// The companion chat. It's a bottom tab, so it has no Back button.
@Composable
fun ChatScreen(onOpenPlan: () -> Unit = {}, viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Keep the newest message (or the "Thinking…" bubble) in view.
    val itemCount = state.entries.size + if (state.sending) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScreenHeader(
                    stringResource(R.string.chat_title),
                    subtitle = stringResource(R.string.chat_subtitle),
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                if (state.days.isNotEmpty()) {
                    DayPicker(state, onSelect = viewModel::selectDay)
                }
            }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.entries.isEmpty()) {
                Suggestions(onAsk = viewModel::send)
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        Message(entry, onEnlarge = viewModel::enlarge, onOpenPlan = onOpenPlan)
                    }
                    if (state.sending) {
                        item(key = "thinking") {
                            Bubble(fromUser = false) { Text(stringResource(R.string.chat_thinking), color = Palette.Mute) }
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
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.days) { index, day ->
            PillButton(
                stringResource(R.string.chat_day, day.index, day.city),
                onClick = { onSelect(index) },
                tone = if (index == state.selectedDay) Tone.MAROON else Tone.PETAL,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(onAsk: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The companion's opening line, as its first chat bubble.
        Bubble(fromUser = false) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.chat_empty_title), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.chat_empty_hint), style = MaterialTheme.typography.bodyMedium, color = Palette.Mute)
            }
        }
        // Starter questions: one per thing the companion is good at.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stringArrayResource(R.array.chat_suggestions).forEach { question ->
                PillButton(question, onClick = { onAsk(question) })
            }
        }
    }
}

@Composable
private fun Message(entry: ChatEntry, onEnlarge: (PhraseDto) -> Unit, onOpenPlan: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (entry.fromUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Bubble(fromUser = entry.fromUser, isError = entry.error != null) {
            Text(entry.error?.userMessage() ?: entry.text, color = if (entry.fromUser) Palette.Tip else Palette.Ink)
        }

        entry.itineraryActions.forEach { action ->
            ItineraryActionBadge(action, onOpenPlan)
        }

        entry.phrases.forEach { phrase ->
            PhraseCard(phrase, onClick = { onEnlarge(phrase) }, modifier = Modifier.widthIn(max = 320.dp))
        }

        sourceLabel(entry.toolsUsed)?.let { Pill("✓ $it", Tone.GREEN) }
    }
}

@Composable
private fun ItineraryActionBadge(action: ItineraryActionDto, onOpenPlan: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Palette.Petal,
        border = BorderStroke(1.dp, Palette.Maroon.copy(alpha = 0.25f)),
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clickable(onClick = onOpenPlan),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val actionText = when (action.type.lowercase()) {
                    "add" -> stringResource(R.string.chat_itinerary_added, action.placeName, action.dayIndex)
                    "remove" -> stringResource(R.string.chat_itinerary_removed, action.placeName, action.dayIndex)
                    "move" -> stringResource(R.string.chat_itinerary_moved, action.placeName, action.targetDay ?: action.dayIndex)
                    else -> stringResource(R.string.chat_itinerary_updated, action.placeName)
                }
                Text(
                    text = actionText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Palette.Maroon,
                )
                if (!action.timeSlot.isNullOrBlank()) {
                    Text(
                        text = action.timeSlot.replaceFirstChar { it.uppercase() },
                        fontSize = 11.sp,
                        color = Palette.Mute,
                    )
                }
            }
            Text(
                text = stringResource(R.string.chat_view_in_plan),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Maroon,
            )
        }
    }
}

// Tells the traveller where an answer came from, which is what makes it
// trustworthy: real timetables and our own rulebook, not the AI's memory.
@Composable
private fun sourceLabel(toolsUsed: List<String>): String? {
    val labels = toolsUsed.mapNotNull {
        when (it) {
            "get_train_route" -> R.string.source_train
            "get_etiquette" -> R.string.source_etiquette
            "show_diet_card" -> R.string.source_diet_card
            "find_food" -> R.string.source_food
            "find_places" -> R.string.source_shops
            "search_web" -> R.string.source_web_search
            "add_itinerary_stop", "remove_itinerary_stop", "move_itinerary_stop" -> R.string.source_itinerary_update
            else -> null
        }
    }
    return labels.takeIf { it.isNotEmpty() }?.map { stringResource(it) }?.joinToString(" · ")
}

// The traveller's messages are maroon on the right; the companion's are
// cream cards on the left (pink if something went wrong).
@Composable
private fun Bubble(fromUser: Boolean, isError: Boolean = false, content: @Composable () -> Unit) {
    val colour = when {
        isError -> Palette.Petal
        fromUser -> Palette.Maroon
        else -> Palette.Card
    }
    val shape = if (fromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
    Surface(
        shape = shape,
        color = colour,
        border = if (fromUser || isError) null else BorderStroke(1.dp, Palette.Line),
        modifier = Modifier.widthIn(max = 300.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

// A rounded field with "Send" inside it, as in the design.
@Composable
private fun InputBar(input: String, sending: Boolean, onInput: (String) -> Unit, onSend: () -> Unit) {
    // Pad for the keyboard, so the field stays visible while typing
    // (navigationBarsPadding first, so imePadding doesn't add it twice).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_placeholder)) },
            maxLines = 4,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Palette.Card,
                focusedContainerColor = Palette.Card,
                unfocusedBorderColor = Palette.Line,
                focusedBorderColor = Palette.Sun,
            ),
            trailingIcon = {
                TextButton(onClick = onSend, enabled = input.isNotBlank() && !sending) {
                    Text(
                        stringResource(R.string.chat_send),
                        fontWeight = FontWeight.Bold,
                        color = if (input.isNotBlank() && !sending) Palette.Sun else Color(0x667A6868),
                    )
                }
            },
        )
    }
}
