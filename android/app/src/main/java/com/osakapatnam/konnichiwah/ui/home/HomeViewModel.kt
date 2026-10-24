package com.osakapatnam.konnichiwah.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.remote.Diet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// What the home card says about the trip, e.g. "7 days · Tokyo, Kyoto ·
// Vegetarian". Both come from the phone (Room and DataStore), so the home
// screen works offline.
data class HomeUiState(
    val hasTrip: Boolean = false,
    val days: Int = 0,
    val cities: List<String> = emptyList(),
    val diet: Diet? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    itineraryRepository: ItineraryRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(itineraryRepository.latest, profileRepository.tripRequest) { trip, request ->
        HomeUiState(
            hasTrip = trip != null,
            days = trip?.days?.size ?: 0,
            cities = trip?.days?.map { it.city }?.distinct().orEmpty(),
            diet = request?.profile?.diet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
