package com.osakapatnam.konnichiwah.ui.devcheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.BuildConfig
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.RulebookAssets
import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.BudgetBand
import com.osakapatnam.konnichiwah.data.remote.Diet
import com.osakapatnam.konnichiwah.data.remote.GroupType
import com.osakapatnam.konnichiwah.data.remote.Interest
import com.osakapatnam.konnichiwah.data.remote.JapaneseLevel
import com.osakapatnam.konnichiwah.data.remote.Pace
import com.osakapatnam.konnichiwah.data.remote.SpiceLevel
import com.osakapatnam.konnichiwah.data.remote.TripRequestDto
import com.osakapatnam.konnichiwah.data.remote.UserProfileDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevCheckUiState(
    val baseUrl: String = BuildConfig.API_BASE_URL,
    val offlineRules: String = "loading…",
    val health: String? = null,
    val templeRules: String? = null,
    val plan: String? = null,
    val loading: Boolean = false,
)

// A temporary developer screen that exercises every integration point: the
// bundled rulebook, the healthcheck, the etiquette endpoint and the planner.
// It will be replaced by the questionnaire as the start screen.
@HiltViewModel
class DevCheckViewModel @Inject constructor(
    private val repository: BackendRepository,
    private val rulebook: RulebookAssets,
) : ViewModel() {

    private val _state = MutableStateFlow(DevCheckUiState())
    val state: StateFlow<DevCheckUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val rules = rulebook.load()
            _state.update { it.copy(offlineRules = "${rules.size} rules bundled in the APK") }
        }
    }

    fun checkBackend() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }

            val health = describe(repository.healthcheck()) {
                "${it.status} (version ${it.systemInfo["version"]}, ${it.systemInfo["environment"]})"
            }
            val temple = describe(repository.etiquette("temple")) { rules ->
                "${rules.size} temple rules; first: \"${rules.firstOrNull()?.title}\""
            }

            _state.update { it.copy(health = health, templeRules = temple, loading = false) }
        }
    }

    fun tryPlanner() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, plan = "asking the planner (can take 10-20s)…") }

            val plan = describe(repository.suggest(sampleRequest)) { plan ->
                val cities = plan.cities.joinToString { "${it.name} ${it.days}d" }
                "source=${plan.source}; $cities; ${plan.suggestions.size} suggestions, e.g. " +
                    plan.suggestions.take(3).joinToString { it.name }
            }

            _state.update { it.copy(plan = plan, loading = false) }
        }
    }

    private val sampleRequest = TripRequestDto(
        profile = UserProfileDto(
            interests = listOf(Interest.TEMPLES_SHRINES, Interest.FOOD),
            budgetBand = BudgetBand.LAKH_2_TO_3,
            pace = Pace.BALANCED,
            groupType = GroupType.FAMILY_ELDERS,
            diet = Diet.VEG,
            spiceLevel = SpiceLevel.MEDIUM,
            japaneseLevel = JapaneseLevel.NONE,
            notes = "Parents are 65+, avoid long walks",
        ),
        tripDays = 7,
        travelMonth = 4,
    )
}

private fun <T> describe(result: ApiResult<T>, onSuccess: (T) -> String): String =
    when (result) {
        is ApiResult.Success -> onSuccess(result.value)
        is ApiResult.Failure -> when (val error = result.error) {
            is ApiError.Network -> "can't reach the backend: ${error.cause.message}"
            is ApiError.Http -> "HTTP ${error.status}: ${error.message} ${error.fieldErrors}".trimEnd()
            is ApiError.BadResponse -> "unexpected response: ${error.cause.message}"
        }
    }
