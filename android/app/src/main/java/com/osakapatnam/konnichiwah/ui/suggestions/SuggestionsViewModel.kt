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
import com.osakapatnam.konnichiwah.data.remote.CityPlanDto
import com.osakapatnam.konnichiwah.data.remote.Pace
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

    // Curated subset is preselected according to pace and trip length so the
    // itinerary doesn't feel stuffed. The traveller can toggle any card.
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
                    selectedIds = selectCuratedSubset(result.value, request.profile.pace, request.tripDays),
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

    fun selectAll() = _state.update { state ->
        if (state !is SuggestionsUiState.Loaded || state.building) return@update state
        state.copy(selectedIds = state.plan.suggestions.map { it.id }.toSet())
    }

    fun resetCurated() = _state.update { state ->
        if (state !is SuggestionsUiState.Loaded || state.building) return@update state
        viewModelScope.launch {
            val request = profileRepository.tripRequest.first()
            val pace = request?.profile?.pace
            val days = request?.tripDays ?: state.plan.cities.sumOf { it.days }
            _state.update { s ->
                if (s !is SuggestionsUiState.Loaded) s
                else s.copy(selectedIds = selectCuratedSubset(s.plan, pace, days))
            }
        }
        state
    }

    companion object {
        private val dayTripBases = mapOf(
            "nara" to "kyoto",
            "uji" to "kyoto",
            "himeji" to "osaka",
            "kobe" to "osaka",
            "hakone" to "tokyo",
            "nikko" to "tokyo",
            "kamakura" to "tokyo",
            "yokohama" to "tokyo",
            "otaru" to "sapporo",
            "niseko" to "sapporo",
            "jozankei" to "sapporo",
        )

        private fun baseCity(suggestionCity: String, cities: List<CityPlanDto>): String {
            val norm = suggestionCity.trim().lowercase()
            val direct = cities.firstOrNull { it.name.trim().lowercase() == norm }
            if (direct != null) return direct.name.trim().lowercase()
            val base = dayTripBases[norm]
            if (base != null && cities.any { it.name.trim().lowercase() == base }) return base
            return cities.maxByOrNull { it.days }?.name?.trim()?.lowercase() ?: ""
        }

        // Pre-selects a curated subset matching the user's pace and trip duration so the
        // resulting days don't get stuffed with too many items.
        fun selectCuratedSubset(plan: PlanDto, pace: Pace?, tripDays: Int): Set<String> {
            val sightsPerDay = when (pace) {
                Pace.RELAXED -> 2
                Pace.PACKED -> 4
                else -> 3
            }

            val selected = mutableSetOf<String>()
            val byBaseCity = plan.suggestions.groupBy { baseCity(it.city, plan.cities) }

            for (cityPlan in plan.cities) {
                val cityKey = cityPlan.name.trim().lowercase()
                val items = byBaseCity[cityKey] ?: emptyList()

                val sights = items.filter { it.kind != "food" }
                val foods = items.filter { it.kind == "food" }

                val targetSights = cityPlan.days * sightsPerDay
                selected.addAll(sights.take(targetSights).map { it.id })

                // Pre-select at most 1 restaurant per city day (capped at 2 per city)
                val targetFood = minOf(cityPlan.days, 2)
                selected.addAll(foods.take(targetFood).map { it.id })
            }

            // Fallback if nothing was matched by city
            if (selected.isEmpty()) {
                val totalTarget = tripDays * sightsPerDay
                return plan.suggestions.take(totalTarget).map { it.id }.toSet()
            }

            return selected
        }
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
