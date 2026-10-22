package com.osakapatnam.konnichiwah.ui.questionnaire

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.BuildConfig
import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.Interest
import com.osakapatnam.konnichiwah.data.remote.JapaneseLevel
import com.osakapatnam.konnichiwah.data.remote.Pace
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
import com.osakapatnam.konnichiwah.ui.components.ChoiceChips
import com.osakapatnam.konnichiwah.ui.components.Question
import com.osakapatnam.konnichiwah.ui.label
import com.osakapatnam.konnichiwah.ui.monthLabel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionnaireScreen(
    onSubmitted: () -> Unit,
    onOpenDevCheck: () -> Unit,
    viewModel: QuestionnaireViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The system back gesture goes to the previous step rather than leaving
    // the questionnaire.
    BackHandler(enabled = !state.isFirstStep, onBack = viewModel::back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.step.title) },
                actions = {
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = onOpenDevCheck) { Text("Dev") }
                    }
                },
            )
        },
        bottomBar = {
            // Pad for the system navigation bar, and for the keyboard while
            // it's open, so "Plan my trip" stays visible while typing notes.
            // navigationBarsPadding() runs first and consumes the nav bar
            // inset, so imePadding() doesn't add it a second time.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!state.isFirstStep) {
                    TextButton(onClick = viewModel::back) { Text("Back") }
                } else {
                    Spacer(Modifier)
                }

                if (state.isLastStep) {
                    Button(onClick = { viewModel.submit(onSubmitted) }, enabled = state.canContinue && !state.saving) {
                        Text("Plan my trip")
                    }
                } else {
                    Button(onClick = viewModel::next, enabled = state.canContinue) {
                        Text("Next")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            LinearProgressIndicator(
                progress = { (state.step.ordinal + 1f) / Step.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )

            when (state.step) {
                Step.INTERESTS -> InterestsStep(state, viewModel)
                Step.TRIP -> TripStep(state, viewModel)
                Step.STYLE -> StyleStep(state, viewModel)
                Step.TASTE -> TasteStep(state, viewModel)
                Step.EXTRAS -> ExtrasStep(state, viewModel)
            }
        }
    }
}

@Composable
private fun InterestsStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question("Pick everything that excites you", hint = "Choose at least one.") {
        ChoiceChips(
            options = Interest.entries,
            isSelected = { it in state.interests },
            onClick = viewModel::toggleInterest,
            label = { it.label },
        )
    }
}

@Composable
private fun TripStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question("How many days in Japan?") {
        Text(
            if (state.tripDays == 1) "1 day" else "${state.tripDays} days",
            style = MaterialTheme.typography.headlineSmall,
        )
        Slider(
            value = state.tripDays.toFloat(),
            onValueChange = { viewModel.setTripDays(it.roundToInt()) },
            valueRange = 1f..30f,
            steps = 28,
        )
    }

    Question("Which month are you travelling?", hint = "This decides cherry blossoms, snow and festivals.") {
        ChoiceChips(
            options = (1..12).toList(),
            isSelected = { it == state.travelMonth },
            onClick = viewModel::setTravelMonth,
            label = ::monthLabel,
        )
    }
}

@Composable
private fun StyleStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question("Budget per person", hint = "Including flights and stay.") {
        ChoiceChips(
            options = BudgetBand.entries,
            isSelected = { it == state.budgetBand },
            onClick = viewModel::setBudgetBand,
            label = { it.label },
        )
    }

    Question("Who's travelling?") {
        ChoiceChips(
            options = GroupType.entries,
            isSelected = { it == state.groupType },
            onClick = viewModel::setGroupType,
            label = { it.label },
        )
    }

    Question("Pace") {
        ChoiceChips(
            options = Pace.entries,
            isSelected = { it == state.pace },
            onClick = viewModel::setPace,
            label = { it.label },
        )
    }
}

@Composable
private fun TasteStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question("Your diet") {
        ChoiceChips(
            options = Diet.entries,
            isSelected = { it == state.diet },
            onClick = viewModel::setDiet,
            label = { it.label },
        )
    }

    Question("Anything you avoid?", hint = "We'll warn you about hidden ingredients like fish stock (dashi).") {
        ChoiceChips(
            options = AvoidItem.entries,
            isSelected = { it in state.avoid },
            onClick = viewModel::toggleAvoid,
            label = { it.label },
        )
    }

    Question("How spicy do you like it?") {
        ChoiceChips(
            options = SpiceLevel.entries,
            isSelected = { it == state.spiceLevel },
            onClick = viewModel::setSpiceLevel,
            label = { it.label },
        )
    }
}

@Composable
private fun ExtrasStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question("How much Japanese do you speak?") {
        ChoiceChips(
            options = JapaneseLevel.entries,
            isSelected = { it == state.japaneseLevel },
            onClick = viewModel::setJapaneseLevel,
            label = { it.label },
        )
    }

    Question("Anything else we should know?") {
        val tooLong = state.notesBytes > MAX_NOTES_BYTES
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::setNotes,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = { Text("Wheelchair access, elderly parents, must-see places…") },
            isError = tooLong,
            supportingText = {
                if (tooLong) Text("That's a bit long. Please shorten it.") else Text("Optional")
            },
        )
    }
}
