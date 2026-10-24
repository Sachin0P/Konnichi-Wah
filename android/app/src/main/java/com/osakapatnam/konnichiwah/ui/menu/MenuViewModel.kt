package com.osakapatnam.konnichiwah.ui.menu

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.BackendRepository
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.data.MenuConcern
import com.osakapatnam.konnichiwah.data.MenuFlag
import com.osakapatnam.konnichiwah.data.MenuReader
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.menuConcernsFor
import com.osakapatnam.konnichiwah.data.menuFlags
import com.osakapatnam.konnichiwah.data.remote.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// The most lines we send for AI translation at once. Matches the backend's
// MaxMenuLines; any beyond it are translated on the phone.
private const val MAX_AI_LINES = 60

// One line of the menu: the Japanese, its translation (null until it's
// translated, or if no translator is available), a note on what the dish is
// (from the AI translator only), the warning words in it that matter to this
// traveller, and where it is in the photo.
data class MenuLine(
    val japanese: String,
    val english: String?,
    val flags: List<MenuFlag>,
    val box: Rect? = null,
    val note: String? = null,
)

enum class TranslationModel { CHECKING, MISSING, DOWNLOADING, READY, FAILED }

// Who translated the menu: the AI on our backend (better, but needs the
// internet) or ML Kit on the phone (works offline).
enum class TranslatedBy { AI, PHONE }

data class MenuUiState(
    val model: TranslationModel = TranslationModel.CHECKING,
    val reading: Boolean = false,
    val translating: Boolean = false,
    val translatedBy: TranslatedBy? = null,
    // null until something has been scanned.
    val lines: List<MenuLine>? = null,
    // The photo the lines were read from, upright, for drawing them over.
    val photo: Bitmap? = null,
    // A string resource, so it follows the language on screen.
    @StringRes val error: Int? = null,
) {
    // Everything flagged on the whole menu, for the summary at the top.
    val found: List<MenuConcern> get() = lines.orEmpty().flatMap { line -> line.flags.map { it.concern } }.distinct()
}

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val reader: MenuReader,
    private val backend: BackendRepository,
    private val profileRepository: ProfileRepository,
    private val languageRepository: LanguageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    // Goes up with every scan, so a translation still running for an old
    // scan knows to stop.
    private var scan = 0

    // What this traveller needs warning about, worked out once per scan.
    private var concerns: Set<MenuConcern> = emptySet()

    init {
        viewModelScope.launch {
            val ready = runCatching { reader.canTranslate() }.getOrDefault(false)
            _state.update { it.copy(model = if (ready) TranslationModel.READY else TranslationModel.MISSING) }
        }
    }

    fun read(photo: Bitmap) {
        val thisScan = ++scan
        viewModelScope.launch {
            _state.update { it.copy(reading = true, error = null, translatedBy = null) }

            val text = runCatching { reader.read(photo) }.getOrElse {
                _state.update { it.copy(reading = false, error = R.string.menu_read_failed) }
                return@launch
            }

            // Only flag what this traveller cares about: a non-vegetarian who
            // eats everything doesn't need dashi warnings.
            concerns = menuConcernsFor(profileRepository.tripRequest.first()?.profile)
            val lines = text.map { line -> MenuLine(line.text, null, flagsFor(line.text), line.box) }

            _state.update { it.copy(reading = false, lines = lines, photo = photo) }
            translate(thisScan)
        }
    }

    fun downloadTranslation() {
        viewModelScope.launch {
            _state.update { it.copy(model = TranslationModel.DOWNLOADING) }
            val ok = runCatching { reader.downloadTranslation() }.isSuccess
            _state.update { it.copy(model = if (ok) TranslationModel.READY else TranslationModel.FAILED) }
            // Fill in anything the AI didn't translate (or everything, if it
            // couldn't be reached).
            if (ok) translateOnPhone(scan)
        }
    }

    fun scanAgain() {
        scan++
        _state.update { it.copy(lines = null, photo = null, error = null, translatedBy = null, translating = false) }
    }

    fun cameraError(@StringRes message: Int) = _state.update { it.copy(error = message) }

    private fun flagsFor(text: String): List<MenuFlag> = menuFlags(text).filter { it.concern in concerns }

    // Tries the AI translator first. If it can't be reached (no internet, or
    // it's busy), falls back to translating on the phone.
    private suspend fun translate(thisScan: Int) {
        val lines = _state.value.lines ?: return
        if (lines.isEmpty()) return

        _state.update { it.copy(translating = true) }

        val sent = lines.take(MAX_AI_LINES)
        val language = languageRepository.language.first()
        val result = backend.translateMenu(sent.map { it.japanese }, language)
        if (scan != thisScan) return

        if (result is ApiResult.Success) {
            _state.update { state ->
                val updated = state.lines.orEmpty().mapIndexed { i, line ->
                    val ai = result.value.getOrNull(i) ?: return@mapIndexed line
                    // The AI's reading fixes text recognition mistakes, so
                    // check it for warning words too. Warnings are only ever
                    // added this way, never removed.
                    val extra = flagsFor(ai.japanese).filter { flag -> line.flags.none { it.concern == flag.concern } }
                    line.copy(english = ai.text, note = ai.note.ifBlank { null }, flags = line.flags + extra)
                }
                state.copy(lines = updated, translatedBy = TranslatedBy.AI)
            }
        }

        // Lines the AI didn't cover: all of them if it failed, or any past
        // MAX_AI_LINES.
        translateOnPhone(thisScan)
    }

    // Translates the lines still without a translation one at a time with
    // ML Kit, showing each as it's done. A line that fails stays untranslated.
    private suspend fun translateOnPhone(thisScan: Int) {
        val lines = _state.value.lines
        if (lines == null || _state.value.model != TranslationModel.READY) {
            _state.update { it.copy(translating = false) }
            return
        }

        for ((i, line) in lines.withIndex()) {
            if (line.english != null) continue
            val english = runCatching { reader.translate(line.japanese) }.getOrNull() ?: continue
            if (scan != thisScan) return

            _state.update { state ->
                state.copy(
                    lines = state.lines?.toMutableList()?.also { it[i] = it[i].copy(english = english) },
                    translatedBy = state.translatedBy ?: TranslatedBy.PHONE,
                )
            }
        }

        _state.update { it.copy(translating = false) }
    }
}
