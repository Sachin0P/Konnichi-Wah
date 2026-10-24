package com.osakapatnam.konnichiwah.ui.nearby

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.LocationProvider
import com.osakapatnam.konnichiwah.data.RouteEnd
import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.PlacesDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// The kinds of help, in the order the buttons show them. The values match the
// backend's (data.HelpKinds).
val HELP_KINDS = listOf("hospital", "clinic", "pharmacy", "police", "atm", "toilet", "convenience_store")

data class NearbyUiState(
    // The kind chosen, or null for a text search.
    val kind: String? = null,
    val query: String = "",
    val loading: Boolean = false,
    val result: PlacesDto? = null,
    val apiError: ApiError? = null,
    @StringRes val message: Int? = null,
)

// Finds help near where the traveller is: always from their location, since
// "a pharmacy near me" is the point.
@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val backend: BackendRepository,
    private val itineraries: ItineraryRepository,
    private val location: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    val hasLocationPermission: Boolean get() = location.hasPermission()

    fun setQuery(text: String) = _state.update { it.copy(query = text.take(100)) }

    fun locationDenied() = _state.update { it.copy(loading = false, message = R.string.dir_need_location) }

    // Search for a kind of help (kind) or anything else (the typed query).
    fun search(kind: String?) {
        val query = _state.value.query.trim()
        if (kind == null && query.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(kind = kind, loading = true, apiError = null, message = null) }

            val here = location.current()
            if (here == null) {
                _state.update { it.copy(loading = false, message = R.string.dir_no_fix) }
                return@launch
            }

            val city = itineraries.latest.first()?.days?.firstOrNull()?.city.orEmpty().ifBlank { "Tokyo" }
            val result = backend.places(kind, if (kind == null) query else null, RouteEnd.Point(here.latitude, here.longitude), city)
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(
                        loading = false,
                        result = result.value,
                        message = if (result.value.results.isEmpty()) R.string.near_none else null,
                    )
                    is ApiResult.Failure -> it.copy(loading = false, apiError = result.error)
                }
            }
        }
    }
}
