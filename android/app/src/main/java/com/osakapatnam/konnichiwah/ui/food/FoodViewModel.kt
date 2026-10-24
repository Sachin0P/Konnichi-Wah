package com.osakapatnam.konnichiwah.ui.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.FoodDto
import com.osakapatnam.konnichiwah.ui.FoodRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FoodUiState {
    data object Loading : FoodUiState
    data class Error(val error: ApiError) : FoodUiState
    data class Loaded(val food: FoodDto) : FoodUiState
}

// Finds restaurants near a stop and ranks them with Desi Match for the
// traveller's saved diet. The stop comes from the navigation route.
@HiltViewModel
class FoodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val backendRepository: BackendRepository,
    private val profileRepository: ProfileRepository,
    private val languageRepository: LanguageRepository,
) : ViewModel() {

    val route: FoodRoute = savedStateHandle.toRoute<FoodRoute>()

    private val _state = MutableStateFlow<FoodUiState>(FoodUiState.Loading)
    val state: StateFlow<FoodUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = FoodUiState.Loading
            val profile = profileRepository.tripRequest.first()?.profile

            val lat = route.lat.takeUnless { it.isNaN() }
            val lng = route.lng.takeUnless { it.isNaN() }

            // The reasons and cautions come back in the traveller's language.
            val language = languageRepository.language.first()

            _state.value = when (val result = backendRepository.food(route.placeName, route.city, lat, lng, profile, language)) {
                is ApiResult.Success -> FoodUiState.Loaded(result.value)
                is ApiResult.Failure -> FoodUiState.Error(result.error)
            }
        }
    }
}
