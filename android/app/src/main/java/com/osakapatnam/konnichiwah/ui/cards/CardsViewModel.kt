package com.osakapatnam.konnichiwah.ui.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.RulebookAssets
import com.osakapatnam.konnichiwah.data.dietCardsFor
import com.osakapatnam.konnichiwah.data.remote.PhraseCardDto
import com.osakapatnam.konnichiwah.data.remote.PhraseDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardsUiState(
    // The traveller's own diet cards, picked from their saved profile.
    val yours: List<PhraseCardDto> = emptyList(),
    // Every other card, for showing to others in the group.
    val others: List<PhraseCardDto> = emptyList(),
    val enlarged: PhraseDto? = null,
)

// The phrase cards come from the APK's bundled phrases.json, so this screen
// works with no network at all: exactly when it's needed, at a restaurant.
@HiltViewModel
class CardsViewModel @Inject constructor(
    private val rulebookAssets: RulebookAssets,
    private val profileRepository: ProfileRepository,
    private val languageRepository: LanguageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardsUiState())
    val state: StateFlow<CardsUiState> = _state.asStateFlow()

    init {
        // Each card's meaning follows the traveller's language.
        viewModelScope.launch {
            languageRepository.language.collect { language ->
                val cards = rulebookAssets.phraseCards(language)
                val yours = dietCardsFor(profileRepository.tripRequest.first()?.profile, cards)
                _state.update { it.copy(yours = yours, others = cards - yours.toSet()) }
            }
        }
    }

    fun enlarge(card: PhraseCardDto) = _state.update { it.copy(enlarged = card.phrase()) }
    fun dismissEnlarged() = _state.update { it.copy(enlarged = null) }
}
