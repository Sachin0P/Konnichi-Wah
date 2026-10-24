package com.osakapatnam.konnichiwah.ui.questionnaire

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.remote.AvoidItem
import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.Interest
import com.osakapatnam.konnichiwah.data.remote.JapaneseLevel
import com.osakapatnam.konnichiwah.data.remote.Pace
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
import com.osakapatnam.konnichiwah.data.remote.TripRequestDto
import com.osakapatnam.konnichiwah.data.remote.UserProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// The backend rejects notes longer than this many bytes (UTF-8), so we check
// the same limit before sending.
const val MAX_NOTES_BYTES = 1000

enum class Step(@StringRes val title: Int) {
    INTERESTS(R.string.q_step_interests),
    TRIP(R.string.q_step_trip),
    STYLE(R.string.q_step_style),
    TASTE(R.string.q_step_taste),
    EXTRAS(R.string.q_step_extras),
}

data class QuestionnaireUiState(
    val step: Step = Step.INTERESTS,
    val interests: Set<Interest> = emptySet(),
    val tripDays: Int = 7,
    val travelMonth: Int = LocalDate.now().plusMonths(1).monthValue,
    val budgetBand: BudgetBand? = null,
    val pace: Pace = Pace.BALANCED,
    val groupType: GroupType? = null,
    val diet: Diet? = null,
    val avoid: Set<AvoidItem> = emptySet(),
    val spiceLevel: SpiceLevel = SpiceLevel.MEDIUM,
    val japaneseLevel: JapaneseLevel = JapaneseLevel.NONE,
    val notes: String = "",
    val saving: Boolean = false,
) {
    val notesBytes: Int get() = notes.encodeToByteArray().size

    // Each step only lets you continue once its required answers are given.
    // Questions with a sensible default (pace, spice, Japanese) never block.
    val canContinue: Boolean
        get() = when (step) {
            Step.INTERESTS -> interests.isNotEmpty()
            Step.TRIP -> true
            Step.STYLE -> budgetBand != null && groupType != null
            Step.TASTE -> diet != null
            Step.EXTRAS -> notesBytes <= MAX_NOTES_BYTES
        }

    val isFirstStep: Boolean get() = step == Step.entries.first()
    val isLastStep: Boolean get() = step == Step.entries.last()

    // Only valid once every step can continue, which submit() guarantees.
    fun toRequest(): TripRequestDto = TripRequestDto(
        profile = UserProfileDto(
            interests = interests.sorted(),
            budgetBand = checkNotNull(budgetBand),
            pace = pace,
            groupType = checkNotNull(groupType),
            diet = checkNotNull(diet),
            avoid = avoid.sorted(),
            spiceLevel = spiceLevel,
            japaneseLevel = japaneseLevel,
            notes = notes.trim(),
        ),
        tripDays = tripDays,
        travelMonth = travelMonth,
    )
}

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QuestionnaireUiState())
    val state: StateFlow<QuestionnaireUiState> = _state.asStateFlow()

    init {
        // Prefill from the last saved answers, so editing a trip doesn't mean
        // starting from scratch.
        viewModelScope.launch {
            val saved = profileRepository.tripRequest.first() ?: return@launch
            val p = saved.profile
            _state.update {
                it.copy(
                    interests = p.interests.toSet(),
                    tripDays = saved.tripDays,
                    travelMonth = saved.travelMonth,
                    budgetBand = p.budgetBand,
                    pace = p.pace,
                    groupType = p.groupType,
                    diet = p.diet,
                    avoid = p.avoid.toSet(),
                    spiceLevel = p.spiceLevel,
                    japaneseLevel = p.japaneseLevel,
                    notes = p.notes,
                )
            }
        }
    }

    fun toggleInterest(interest: Interest) = _state.update { it.copy(interests = it.interests.toggle(interest)) }
    fun setTripDays(days: Int) = _state.update { it.copy(tripDays = days.coerceIn(1, 30)) }
    fun setTravelMonth(month: Int) = _state.update { it.copy(travelMonth = month) }
    fun setBudgetBand(band: BudgetBand) = _state.update { it.copy(budgetBand = band) }
    fun setPace(pace: Pace) = _state.update { it.copy(pace = pace) }
    fun setGroupType(group: GroupType) = _state.update { it.copy(groupType = group) }
    fun toggleAvoid(item: AvoidItem) = _state.update { it.copy(avoid = it.avoid.toggle(item)) }
    fun setSpiceLevel(level: SpiceLevel) = _state.update { it.copy(spiceLevel = level) }
    fun setJapaneseLevel(level: JapaneseLevel) = _state.update { it.copy(japaneseLevel = level) }
    fun setNotes(notes: String) = _state.update { it.copy(notes = notes) }

    // Jain diets exclude onion and garlic, so we tick that for the user. They
    // can still untick it if their family's practice differs.
    fun setDiet(diet: Diet) = _state.update {
        val avoid = if (diet == Diet.JAIN) it.avoid + AvoidItem.ONION_GARLIC else it.avoid
        it.copy(diet = diet, avoid = avoid)
    }

    fun next() = _state.update {
        if (!it.canContinue || it.isLastStep) it else it.copy(step = Step.entries[it.step.ordinal + 1])
    }

    fun back() = _state.update {
        if (it.isFirstStep) it else it.copy(step = Step.entries[it.step.ordinal - 1])
    }

    // Save the answers, then hand over to the suggestions screen, which reads
    // them back from the repository.
    fun submit(onSaved: () -> Unit) {
        val current = _state.value
        if (!current.isLastStep || !current.canContinue || current.saving) return

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            profileRepository.save(current.toRequest())
            _state.update { it.copy(saving = false) }
            onSaved()
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
