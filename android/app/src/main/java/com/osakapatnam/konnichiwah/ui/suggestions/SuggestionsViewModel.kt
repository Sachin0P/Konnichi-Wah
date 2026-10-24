package com.osakapatnam.konnichiwah.ui.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.ui.monthLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SuggestionsUiState {
    data object Loading : SuggestionsUiState

    // error is null when the questionnaire hasn't been answered yet.
    data class Error(val error: ApiError?) : SuggestionsUiState

    // Everything starts selected: it's quicker to remove a few cards than to
    // tick most of them. buildError is a one-off message for a snackbar.
    data class Loaded(
        val plan: PlanDto,
        val selectedIds: Set<String>,
        val building: Boolean = false,
        val buildError: ApiError? = null,
    ) : SuggestionsUiState
}

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val backendRepository: BackendRepository,
    private val itineraryRepository: ItineraryRepository,
    private val languageRepository: LanguageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SuggestionsUiState>(SuggestionsUiState.Loading)
    val state: StateFlow<SuggestionsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = SuggestionsUiState.Loading

            val request = profileRepository.tripRequest.first()
            if (request == null) {
                _state.value = SuggestionsUiState.Error(null)
                return@launch
            }

            _state.value = when (val result = backendRepository.suggest(request)) {
                is ApiResult.Success -> SuggestionsUiState.Loaded(
                    plan = result.value,
                    selectedIds = result.value.suggestions.map { it.id }.toSet(),
                )
                is ApiResult.Failure -> SuggestionsUiState.Error(result.error)
            }
        }
    }

    fun toggle(id: String) = _state.update { state ->
        if (state !is SuggestionsUiState.Loaded || state.building) return@update state
        val selected = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
        state.copy(selectedIds = selected)
    }

    // Sends the chosen cards to the backend, which arranges them into days,
    // and saves the result on the phone.
    fun buildItinerary(onBuilt: () -> Unit) {
        val loaded = _state.value as? SuggestionsUiState.Loaded ?: return
        if (loaded.building || loaded.selectedIds.isEmpty()) return

        _state.value = loaded.copy(building = true, buildError = null)
        viewModelScope.launch {
            // The title is saved with the trip, so it's written in the language
            // the traveller is using when they build it.
            val request = profileRepository.tripRequest.first()
            val res = languageRepository.resources()
            val title = if (request == null) {
                res.getString(R.string.trip_title_default)
            } else {
                res.getString(R.string.trip_title, request.tripDays, monthLabel(request.travelMonth, res.configuration.locales[0]))
            }

            when (val result = itineraryRepository.create(title, loaded.plan, loaded.selectedIds)) {
                is ApiResult.Success -> onBuilt()
                is ApiResult.Failure -> _state.update {
                    (it as? SuggestionsUiState.Loaded)?.copy(building = false, buildError = result.error) ?: it
                }
            }
        }
    }

    fun buildErrorShown() = _state.update {
        (it as? SuggestionsUiState.Loaded)?.copy(buildError = null) ?: it
    }
}
