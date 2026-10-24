package com.osakapatnam.konnichiwah.ui.directions

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.LocationProvider
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.data.RouteEnd
import com.osakapatnam.konnichiwah.data.RulebookAssets
import com.osakapatnam.konnichiwah.data.StationGuideDto
import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.DirectionsDto
import com.osakapatnam.konnichiwah.data.remote.PlaceSuggestionDto
import com.osakapatnam.konnichiwah.ui.DirectionsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectionsUiState(
    // Start from where the traveller is (the default), or from fromText.
    val fromHere: Boolean = true,
    val fromText: String = "",
    val toText: String = "",
    val loading: Boolean = false,
    val result: DirectionsDto? = null,
    val selected: Int = 0,
    // Either a failed request, or a problem we explain ourselves (a string
    // resource): no location, no route.
    val apiError: ApiError? = null,
    @StringRes val message: Int? = null,
    // The dropdowns under the From and To boxes while typing.
    val fromSuggestions: List<PlaceSuggestionDto> = emptyList(),
    val toSuggestions: List<PlaceSuggestionDto> = emptyList(),
    val stationGuides: List<StationGuideDto> = emptyList(),
    val viewingGuide: StationGuideDto? = null,
) {
    val canSearch: Boolean get() = toText.isNotBlank() && (fromHere || fromText.isNotBlank()) && !loading
}

// Finds journeys for the directions screen. The destination can arrive with
// the navigation route (a stop on the trip, with its coordinates); the
// traveller can also type both ends.
@HiltViewModel
class DirectionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val backend: BackendRepository,
    private val itineraries: ItineraryRepository,
    private val location: LocationProvider,
    private val rulebookAssets: RulebookAssets,
    private val languageRepo: LanguageRepository,
) : ViewModel() {

    private val route: DirectionsRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(DirectionsUiState(toText = route.toName))
    val state: StateFlow<DirectionsUiState> = _state.asStateFlow()

    // Exact coordinates for either end: the stop's, or a picked suggestion's.
    // Used until the traveller edits that box again.
    private var fromPoint: RouteEnd.Point? = null
    private var toPoint: RouteEnd.Point? =
        if (route.toLat.isNaN() || route.toLng.isNaN()) null else RouteEnd.Point(route.toLat, route.toLng, route.toName)

    private var suggestJob: Job? = null

    init {
        val recent = location.recent()
        if (recent != null && (recent.latitude !in 24.0..46.0 || recent.longitude !in 122.0..146.0)) {
            _state.update { it.copy(fromHere = false, fromText = "Tokyo Station") }
        }
        viewModelScope.launch {
            val lang = languageRepo.language.first()
            val guides = rulebookAssets.stationGuides(lang)
            _state.update { it.copy(stationGuides = guides) }
        }
    }

    val hasLocationPermission: Boolean get() = location.hasPermission()

    fun viewGuide(guide: StationGuideDto?) = _state.update { it.copy(viewingGuide = guide) }

    fun setFromHere(here: Boolean) = _state.update { it.copy(fromHere = here, message = null, fromSuggestions = emptyList()) }
    fun setFrom(text: String) {
        fromPoint = null
        _state.update { it.copy(fromText = text.take(100)) }
        suggest(text, from = true)
    }
    fun setTo(text: String) {
        toPoint = null
        _state.update { it.copy(toText = text.take(100)) }
        suggest(text, from = false)
    }

    // A suggestion was tapped: show its name in the box and route to its
    // exact point. label is what the screen shows ("Shinjuku Station").
    fun pick(suggestion: PlaceSuggestionDto, label: String, from: Boolean) {
        suggestJob?.cancel()
        val point = RouteEnd.Point(suggestion.lat, suggestion.lng, label)
        if (from) {
            fromPoint = point
            _state.update { it.copy(fromText = label, fromSuggestions = emptyList()) }
        } else {
            toPoint = point
            _state.update { it.copy(toText = label, toSuggestions = emptyList()) }
        }
    }

    fun dismissSuggestions() {
        suggestJob?.cancel()
        _state.update { it.copy(fromSuggestions = emptyList(), toSuggestions = emptyList()) }
    }

    // Looks the text up once typing pauses for 300 ms, so a quick typist
    // sends one request rather than one per letter. The trip's own stops come
    // first, and work offline; if the backend can't be reached, they're all
    // that's shown.
    private fun suggest(text: String, from: Boolean) {
        suggestJob?.cancel()
        val query = text.trim()
        if (query.length < 2) {
            setSuggestions(emptyList(), from)
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            val trip = tripStops(query)
            setSuggestions(trip, from)

            val here = location.recent()
            val found = backend.suggestPlaces(query, city(), here?.latitude, here?.longitude)
            if (found is ApiResult.Success) {
                setSuggestions((trip + found.value).distinctBy { it.name.lowercase() }.take(8), from)
            }
        }
    }

    private fun setSuggestions(list: List<PlaceSuggestionDto>, from: Boolean) = _state.update {
        if (from) it.copy(fromSuggestions = list) else it.copy(toSuggestions = list)
    }

    // Stops on the saved trip whose name contains the text, with their
    // coordinates: "fus" offers the Fushimi Inari stop the traveller planned.
    private suspend fun tripStops(query: String): List<PlaceSuggestionDto> =
        itineraries.latest.first()?.days.orEmpty()
            .flatMap { day -> day.stops }
            .filter { it.lat != null && it.lng != null && it.placeName.contains(query, ignoreCase = true) }
            .distinctBy { it.placeName }
            .take(3)
            .map { PlaceSuggestionDto(name = it.placeName, kind = TRIP_STOP, detail = it.city, lat = it.lat!!, lng = it.lng!!) }
    fun select(index: Int) = _state.update { it.copy(selected = index) }

    // Called when the location permission is refused: fall back to typing.
    fun locationDenied() = _state.update { it.copy(fromHere = false, message = R.string.dir_need_location) }

    fun search() {
        val current = _state.value
        if (!current.canSearch) return

        viewModelScope.launch {
            suggestJob?.cancel()
            _state.update { it.copy(loading = true, apiError = null, message = null, fromSuggestions = emptyList(), toSuggestions = emptyList()) }

            val from: RouteEnd = if (current.fromHere) {
                val here = location.current()
                if (here == null) {
                    _state.update { it.copy(loading = false, message = R.string.dir_no_fix) }
                    return@launch
                }
                if (here.latitude !in 24.0..46.0 || here.longitude !in 122.0..146.0) {
                    _state.update { it.copy(loading = false, message = R.string.dir_outside_japan) }
                    return@launch
                }
                RouteEnd.Point(here.latitude, here.longitude)
            } else {
                fromPoint ?: RouteEnd.Named(current.fromText.trim())
            }
            val to: RouteEnd = toPoint ?: RouteEnd.Named(current.toText.trim())

            val result = backend.directions(from, to, city())
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(
                        loading = false,
                        result = result.value,
                        selected = 0,
                        message = if (result.value.journeys.isEmpty()) R.string.dir_no_routes else null,
                    )
                    is ApiResult.Failure -> it.copy(loading = false, apiError = result.error)
                }
            }
        }
    }

    // The city to look place names up near: the stop's, else the trip's
    // first city, else Tokyo.
    private suspend fun city(): String =
        route.city.ifBlank { itineraries.latest.first()?.days?.firstOrNull()?.city.orEmpty() }.ifBlank { "Tokyo" }
}

// The kind of suggestion that comes from the traveller's own trip.
const val TRIP_STOP = "trip_stop"
