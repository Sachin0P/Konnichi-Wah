package com.osakapatnam.konnichiwah.ui.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.PlanDto
import com.osakapatnam.konnichiwah.ui.monthLabel
import com.osakapatnam.konnichiwah.ui.userMessage
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
    data class Error(val message: String) : SuggestionsUiState

    // Everything starts selected: it's quicker to remove a few cards than to
    // tick most of them. buildError is a one-off message for a snackbar.
    data class Loaded(
        val plan: PlanDto,
        val selectedIds: Set<String>,
        val building: Boolean = false,
        val buildError: String? = null,
    ) : SuggestionsUiState
}

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val backendRepository: BackendRepository,
    private val itineraryRepository: ItineraryRepository,
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
                _state.value = SuggestionsUiState.Error("Please answer the questions first.")
                return@launch
            }

            _state.value = when (val result = backendRepository.suggest(request)) {
                is ApiResult.Success -> SuggestionsUiState.Loaded(
                    plan = result.value,
                    selectedIds = result.value.suggestions.map { it.id }.toSet(),
                )
                is ApiResult.Failure -> SuggestionsUiState.Error(result.error.userMessage())
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
            val request = profileRepository.tripRequest.first()
            val title = if (request == null) {
                "My Japan trip"
            } else {
                "${request.tripDays}-day Japan trip · ${monthLabel(request.travelMonth)}"
            }

            when (val result = itineraryRepository.create(title, loaded.plan, loaded.selectedIds)) {
                is ApiResult.Success -> onBuilt()
                is ApiResult.Failure -> _state.update {
                    (it as? SuggestionsUiState.Loaded)?.copy(building = false, buildError = result.error.userMessage()) ?: it
                }
            }
        }
    }

    fun buildErrorShown() = _state.update {
        (it as? SuggestionsUiState.Loaded)?.copy(buildError = null) ?: it
    }
}
