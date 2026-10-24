package com.osakapatnam.konnichiwah.ui.questionnaire

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.BuildConfig
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.Interest
import com.osakapatnam.konnichiwah.data.remote.JapaneseLevel
import com.osakapatnam.konnichiwah.data.remote.Pace
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
import com.osakapatnam.konnichiwah.ui.LanguageSwitch
import com.osakapatnam.konnichiwah.ui.components.ChoiceChips
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.Question
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.Segmented
import com.osakapatnam.konnichiwah.ui.currentLocale
import com.osakapatnam.konnichiwah.ui.labelRes
import com.osakapatnam.konnichiwah.ui.monthLabel
import com.osakapatnam.konnichiwah.ui.theme.Palette
import kotlin.math.roundToInt

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
            // Step counter on the left; language switch (and, in debug
            // builds, the developer check) on the right.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.q_step_of, state.step.ordinal + 1, Step.entries.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                LanguageSwitch()
                if (BuildConfig.DEBUG) {
                    TextButton(onClick = onOpenDevCheck) { Text("Dev") }
                }
            }
        },
        bottomBar = {
            // Pad for the keyboard while it's open, so "Plan my trip" stays
            // visible while typing notes.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.isFirstStep) {
                    TextButton(onClick = viewModel::back) {
                        Text(stringResource(R.string.back), color = Palette.Maroon, style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (state.isLastStep) {
                    PrimaryButton(
                        stringResource(R.string.q_plan_my_trip),
                        onClick = { viewModel.submit(onSubmitted) },
                        enabled = state.canContinue && !state.saving,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PrimaryButton(
                        stringResource(R.string.q_next),
                        onClick = viewModel::next,
                        enabled = state.canContinue,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ScreenHeader(stringResource(state.step.title), subtitle = stringResource(R.string.q_takes_a_minute))
                StepBar(current = state.step.ordinal, count = Step.entries.size)
            }

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

// Progress as a row of short bars: red for done and current, pink for to come.
@Composable
private fun StepBar(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(count) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(if (i <= current) Palette.Sun else Palette.Petal, CircleShape),
            )
        }
    }
}

@Composable
private fun InterestsStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question(stringResource(R.string.q_interests), hint = stringResource(R.string.q_interests_hint)) {
        ChoiceChips(
            options = Interest.entries,
            isSelected = { it in state.interests },
            onClick = viewModel::toggleInterest,
            label = { stringResource(it.labelRes) },
        )
    }
}

@Composable
private fun TripStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question(stringResource(R.string.q_days)) {
        Text(
            pluralStringResource(R.plurals.days, state.tripDays, state.tripDays),
            style = MaterialTheme.typography.headlineMedium,
        )
        Slider(
            value = state.tripDays.toFloat(),
            onValueChange = { viewModel.setTripDays(it.roundToInt()) },
            valueRange = 1f..30f,
            steps = 28,
        )
    }

    Question(stringResource(R.string.q_month), hint = stringResource(R.string.q_month_hint)) {
        ChoiceChips(
            options = (1..12).toList(),
            isSelected = { it == state.travelMonth },
            onClick = viewModel::setTravelMonth,
            label = { monthLabel(it, currentLocale()) },
        )
    }
}

@Composable
private fun StyleStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question(stringResource(R.string.q_budget), hint = stringResource(R.string.q_budget_hint)) {
        ChoiceChips(
            options = BudgetBand.entries,
            isSelected = { it == state.budgetBand },
            onClick = viewModel::setBudgetBand,
            label = { stringResource(it.labelRes) },
        )
    }

    Question(stringResource(R.string.q_group)) {
        ChoiceChips(
            options = GroupType.entries,
            isSelected = { it == state.groupType },
            onClick = viewModel::setGroupType,
            label = { stringResource(it.labelRes) },
        )
    }

    Question(stringResource(R.string.q_pace)) {
        Segmented(
            options = Pace.entries,
            selected = state.pace,
            onClick = viewModel::setPace,
            label = { stringResource(it.labelRes) },
        )
    }
}

@Composable
private fun TasteStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question(stringResource(R.string.q_diet)) {
        ChoiceChips(
            options = Diet.entries,
            isSelected = { it == state.diet },
            onClick = viewModel::setDiet,
            label = { stringResource(it.labelRes) },
        )
    }

    Question(stringResource(R.string.q_avoid), hint = stringResource(R.string.q_avoid_hint)) {
        ChoiceChips(
            options = AvoidItem.entries,
            isSelected = { it in state.avoid },
            onClick = viewModel::toggleAvoid,
            label = { stringResource(it.labelRes) },
        )
    }

    Question(stringResource(R.string.q_spice)) {
        Segmented(
            options = SpiceLevel.entries,
            selected = state.spiceLevel,
            onClick = viewModel::setSpiceLevel,
            label = { stringResource(it.labelRes) },
        )
    }
}

@Composable
private fun ExtrasStep(state: QuestionnaireUiState, viewModel: QuestionnaireViewModel) {
    Question(stringResource(R.string.q_japanese)) {
        Segmented(
            options = JapaneseLevel.entries,
            selected = state.japaneseLevel,
            onClick = viewModel::setJapaneseLevel,
            label = { stringResource(it.labelRes) },
        )
    }

    Question(stringResource(R.string.q_notes)) {
        val tooLong = state.notesBytes > MAX_NOTES_BYTES
        OutlinedTextField(
            value = state.notes,
            onValueChange = viewModel::setNotes,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Palette.Card,
                focusedContainerColor = Palette.Card,
                unfocusedBorderColor = Palette.Line,
                focusedBorderColor = Palette.Sun,
            ),
            placeholder = { Text(stringResource(R.string.q_notes_placeholder)) },
            isError = tooLong,
            supportingText = {
                Text(stringResource(if (tooLong) R.string.q_notes_too_long else R.string.q_optional))
            },
        )
    }
}
