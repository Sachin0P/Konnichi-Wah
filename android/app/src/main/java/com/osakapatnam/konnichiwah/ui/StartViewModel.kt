package com.osakapatnam.konnichiwah.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Whether a trip is saved on the phone, which decides where the Plan tab
// goes: the saved trip if there is one (it works offline), otherwise the
// questionnaire. It follows Room, so it turns true as soon as a trip is
// built. null means "still checking".
@HiltViewModel
class StartViewModel @Inject constructor(
    itineraryRepository: ItineraryRepository,
) : ViewModel() {

    val hasItinerary: StateFlow<Boolean?> = itineraryRepository.latest
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
