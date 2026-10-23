package com.osakapatnam.konnichiwah.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Decides the first screen: the saved trip if there is one (so the app opens
// straight to it, even offline), otherwise the questionnaire. null means
// "still checking".
@HiltViewModel
class StartViewModel @Inject constructor(
    itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val _hasItinerary = MutableStateFlow<Boolean?>(null)
    val hasItinerary: StateFlow<Boolean?> = _hasItinerary.asStateFlow()

    init {
        viewModelScope.launch { _hasItinerary.value = itineraryRepository.hasItinerary() }
    }
}
