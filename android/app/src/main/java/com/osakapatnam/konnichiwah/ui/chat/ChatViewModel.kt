package com.osakapatnam.konnichiwah.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.ItineraryRepository
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.data.LocationProvider
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.remote.ApiError
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import com.osakapatnam.konnichiwah.data.remote.ChatContextDto
import com.osakapatnam.konnichiwah.data.remote.ChatMessageDto
import com.osakapatnam.konnichiwah.data.remote.ChatRequestDto
import com.osakapatnam.konnichiwah.data.remote.ChatStopDto
import com.osakapatnam.konnichiwah.data.remote.DayDto
import com.osakapatnam.konnichiwah.data.remote.GeoPointDto
import com.osakapatnam.konnichiwah.data.remote.PhraseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// The longest message a traveller can type. Well under the backend's limit,
// and long enough for any real question.
const val MAX_CHAT_INPUT = 500

// How many earlier messages we send for context. The backend accepts 20;
// fewer keeps requests small, which matters on the LLM's free tier.
private const val HISTORY_SIZE = 10

data class ChatEntry(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val phrases: List<PhraseDto> = emptyList(),
    val toolsUsed: List<String> = emptyList(),
    // A failed reply is shown in the chat as an error message (in the language
    // on screen) but never sent back as history.
    val error: ApiError? = null,
)

data class ChatUiState(
    val days: List<DayDto> = emptyList(),
    val selectedDay: Int = 0,
    val entries: List<ChatEntry> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    // A phrase card opened full screen to show staff.
    val enlarged: PhraseDto? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val itineraryRepository: ItineraryRepository,
    private val profileRepository: ProfileRepository,
    private val backendRepository: BackendRepository,
    private val languageRepository: LanguageRepository,
    private val location: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var nextId = 0L

    init {
        // We don't know the trip's dates yet, so start on the first day that
        // has stops. The traveller can pick another day.
        viewModelScope.launch {
            val days = itineraryRepository.latest.first()?.days.orEmpty()
            val firstWithStops = days.indexOfFirst { it.stops.isNotEmpty() }.coerceAtLeast(0)
            _state.update { it.copy(days = days, selectedDay = firstWithStops) }
        }
    }

    fun setInput(text: String) = _state.update { it.copy(input = text.take(MAX_CHAT_INPUT)) }
    fun selectDay(index: Int) = _state.update { it.copy(selectedDay = index) }
    fun enlarge(phrase: PhraseDto) = _state.update { it.copy(enlarged = phrase) }
    fun dismissEnlarged() = _state.update { it.copy(enlarged = null) }

    fun send(text: String = _state.value.input) {
        val question = text.trim().take(MAX_CHAT_INPUT)
        val current = _state.value
        if (question.isEmpty() || current.sending) return

        val entries = current.entries + ChatEntry(id = nextId++, fromUser = true, text = question)
        _state.update { it.copy(entries = entries, input = "", sending = true) }

        viewModelScope.launch {
            val request = ChatRequestDto(
                messages = entries
                    .filter { it.error == null }
                    .takeLast(HISTORY_SIZE)
                    .map { ChatMessageDto(role = if (it.fromUser) "user" else "assistant", text = it.text) },
                context = context(current),
                // The companion answers in the traveller's language.
                language = languageRepository.language.first().tag,
            )

            val reply = when (val result = backendRepository.chat(request)) {
                is ApiResult.Success -> ChatEntry(
                    id = nextId++,
                    fromUser = false,
                    text = result.value.text,
                    phrases = result.value.phrases,
                    toolsUsed = result.value.toolsUsed,
                )
                is ApiResult.Failure -> ChatEntry(
                    id = nextId++,
                    fromUser = false,
                    text = "",
                    error = result.error,
                )
            }

            _state.update { it.copy(entries = it.entries + reply, sending = false) }
        }
    }

    // Tell the companion about the chosen day and the traveller's taste
    // profile, so "how do I get to my next stop?" and "what can I eat?" work.
    private suspend fun context(state: ChatUiState): ChatContextDto {
        val day = state.days.getOrNull(state.selectedDay)
        val profile = profileRepository.tripRequest.first()?.profile

        return ChatContextDto(
            city = day?.city.orEmpty(),
            dayIndex = day?.index ?: 0,
            stops = day?.stops.orEmpty().take(20).map {
                ChatStopDto(placeName = it.placeName.take(100), placeType = it.placeType, timeSlot = it.timeSlot)
            },
            profile = profile,
            // Only if they've already allowed location and are in Japan:
            // the chat doesn't ask for it itself, and locations outside Japan
            // are ignored.
            location = location.recent()
                ?.takeIf { it.latitude in 24.0..46.0 && it.longitude in 122.0..146.0 }
                ?.let { GeoPointDto(it.latitude, it.longitude) },
        )
    }
}
