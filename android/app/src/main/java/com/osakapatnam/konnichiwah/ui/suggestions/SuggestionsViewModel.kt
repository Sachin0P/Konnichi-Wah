package com.osakapatnam.konnichiwah.ui.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.PlanDto
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
    // tick most of them.
    data class Loaded(val plan: PlanDto, val selectedIds: Set<String>) : SuggestionsUiState
}

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val backendRepository: BackendRepository,
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
        if (state !is SuggestionsUiState.Loaded) return@update state
        val selected = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
        state.copy(selectedIds = selected)
    }
}
