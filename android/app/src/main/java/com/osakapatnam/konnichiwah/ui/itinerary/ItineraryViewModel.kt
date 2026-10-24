package com.osakapatnam.konnichiwah.ui.itinerary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.data.LocalItinerary
import com.osakapatnam.konnichiwah.data.Rulebook
import com.osakapatnam.konnichiwah.data.RulebookAssets
import com.osakapatnam.konnichiwah.data.remote.DayDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ItineraryUiState(
    val loading: Boolean = true,
    val itinerary: LocalItinerary? = null,
    val syncing: Boolean = false,
    // The bundled etiquette rulebook, for showing each stop's tips offline.
    val rulebook: Rulebook? = null,
)

@HiltViewModel
class ItineraryViewModel @Inject constructor(
    private val repository: ItineraryRepository,
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
            repository.latest.collect { itinerary ->
                _state.update { it.copy(loading = false, itinerary = itinerary) }

                // Retry any edits that didn't sync last time, once per open.
                if (!triedSync && itinerary?.pendingSync == true) {
                    triedSync = true
                    syncNow()
                }
            }
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
