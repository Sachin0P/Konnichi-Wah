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
import com.osakapatnam.konnichiwah.data.RouteEnd
import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.DirectionsDto
import com.osakapatnam.konnichiwah.ui.DirectionsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

    private val route: DirectionsRoute = savedStateHandle.toRoute()

    private val _state = MutableStateFlow(DirectionsUiState(toText = route.toName))
    val state: StateFlow<DirectionsUiState> = _state.asStateFlow()

    // The stop's coordinates, used until the traveller edits the destination.
    private var toPoint: RouteEnd.Point? =
        if (route.toLat.isNaN() || route.toLng.isNaN()) null else RouteEnd.Point(route.toLat, route.toLng, route.toName)

    val hasLocationPermission: Boolean get() = location.hasPermission()

    fun setFromHere(here: Boolean) = _state.update { it.copy(fromHere = here, message = null) }
    fun setFrom(text: String) = _state.update { it.copy(fromText = text.take(100)) }
    fun setTo(text: String) {
        toPoint = null
        _state.update { it.copy(toText = text.take(100)) }
    }
    fun select(index: Int) = _state.update { it.copy(selected = index) }

    // Called when the location permission is refused: fall back to typing.
    fun locationDenied() = _state.update { it.copy(fromHere = false, message = R.string.dir_need_location) }

    fun search() {
        val current = _state.value
        if (!current.canSearch) return

        viewModelScope.launch {
            _state.update { it.copy(loading = true, apiError = null, message = null) }

            val from: RouteEnd = if (current.fromHere) {
                val here = location.current()
                if (here == null) {
                    _state.update { it.copy(loading = false, message = R.string.dir_no_fix) }
                    return@launch
                }
                RouteEnd.Point(here.latitude, here.longitude)
            } else {
                RouteEnd.Named(current.fromText.trim())
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
