package com.osakapatnam.konnichiwah.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.data.LocalItinerary
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.Rulebook
import com.osakapatnam.konnichiwah.data.RulebookAssets
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.DayDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ItineraryUiState(
    val loading: Boolean = true,
    val itinerary: LocalItinerary? = null,
    val syncing: Boolean = false,
    val loadingHotels: Boolean = false,
    // The bundled etiquette rulebook, for showing each stop's tips offline.
    val rulebook: Rulebook? = null,
)

@HiltViewModel
class ItineraryViewModel @Inject constructor(
    private val repository: ItineraryRepository,
    private val backendRepository: BackendRepository,
    private val profileRepository: ProfileRepository,
    private val rulebookAssets: RulebookAssets,
    private val languageRepository: LanguageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ItineraryUiState())
    val state: StateFlow<ItineraryUiState> = _state.asStateFlow()

    init {
        // The tips follow the traveller's language, and switch with it.
        viewModelScope.launch {
            languageRepository.language.collect { language ->
                val rulebook = rulebookAssets.rulebook(language)
                _state.update { it.copy(rulebook = rulebook) }
            }
        }

        // The screen always shows Room's copy, so it works offline and updates
        // itself when a sync brings back the server's version.
        viewModelScope.launch {
            var triedSync = false
            var triedHotels = false
            repository.latest.collect { itinerary ->
                _state.update { it.copy(loading = false, itinerary = itinerary) }

                // Retry any edits that didn't sync last time, once per open.
                if (!triedSync && itinerary?.pendingSync == true) {
                    triedSync = true
                    syncNow()
                }

                // If any day is missing hotel recommendations, fetch them automatically!
                if (!triedHotels && itinerary != null && itinerary.days.any { it.hotelName.isNullOrBlank() }) {
                    triedHotels = true
                    recommendHotels()
                }
            }
        }
    }

    fun recommendHotels() {
        val itinerary = _state.value.itinerary ?: return
        val cities = itinerary.days.map { it.city }.filter { it.isNotBlank() }.distinct()
        if (cities.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(loadingHotels = true) }
            val profile = profileRepository.tripRequest.first()?.profile
            val result = backendRepository.recommendHotels(
                cities = cities,
                budgetBand = profile?.budgetBand,
                groupType = profile?.groupType,
                notes = profile?.notes,
                itineraryId = itinerary.serverId,
            )
            if (result is ApiResult.Success && result.value.isNotEmpty()) {
                val hotelMap = result.value.associateBy { it.city.trim().lowercase() }
                val updatedDays = itinerary.days.map { day ->
                    val h = hotelMap[day.city.trim().lowercase()]
                    if (h != null) {
                        day.copy(
                            hotelName = h.hotelName,
                            hotelPrice = h.hotelPrice,
                            hotelReason = h.hotelReason,
                        )
                    } else day
                }
                repository.updateDays(itinerary.localId, updatedDays)
            }
            _state.update { it.copy(loadingHotels = false) }
        }
    }

    fun moveUp(dayIndex: Int, stopIndex: Int) = edit { moveStopUp(it, dayIndex, stopIndex) }
    fun moveDown(dayIndex: Int, stopIndex: Int) = edit { moveStopDown(it, dayIndex, stopIndex) }
    fun delete(dayIndex: Int, stopIndex: Int) = edit { deleteStop(it, dayIndex, stopIndex) }

    fun add(dayIndex: Int, placeName: String, placeType: String, timeSlot: String) {
        if (placeName.isBlank()) return
        edit { addStop(it, dayIndex, placeName, placeType, timeSlot) }
    }

    // Setting the dates also schedules the day-before reminders (see
    // KonnichiApp).
    fun setStartDate(date: LocalDate) {
        val itinerary = _state.value.itinerary ?: return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            repository.setStartDate(itinerary.localId, date)
            _state.update { it.copy(syncing = false) }
        }
    }

    fun syncNow() {
        val itinerary = _state.value.itinerary ?: return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            repository.sync(itinerary.localId)
            _state.update { it.copy(syncing = false) }
        }
    }

    private fun edit(transform: (List<DayDto>) -> List<DayDto>) {
        val itinerary = _state.value.itinerary ?: return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            repository.updateDays(itinerary.localId, transform(itinerary.days))
            _state.update { it.copy(syncing = false) }
        }
    }
}
