package com.osakapatnam.konnichiwah.ui.menu

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.MenuConcern
import com.osakapatnam.konnichiwah.data.MenuFlag
import com.osakapatnam.konnichiwah.data.MenuReader
import com.osakapatnam.konnichiwah.data.ProfileRepository
import com.osakapatnam.konnichiwah.data.menuConcernsFor
import com.osakapatnam.konnichiwah.data.menuFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// One line of the menu: the Japanese, its translation (null until it's
// translated, or if the model isn't downloaded), the warning words in it that
// matter to this traveller, and where it is in the photo.
data class MenuLine(val japanese: String, val english: String?, val flags: List<MenuFlag>, val box: Rect? = null)

enum class TranslationModel { CHECKING, MISSING, DOWNLOADING, READY, FAILED }

data class MenuUiState(
    val model: TranslationModel = TranslationModel.CHECKING,
    val reading: Boolean = false,
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
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MenuUiState())
    val state: StateFlow<MenuUiState> = _state.asStateFlow()

    // Goes up with every scan, so a translation still running for an old
    // scan knows to stop.
    private var scan = 0

    init {
        viewModelScope.launch {
            val ready = runCatching { reader.canTranslate() }.getOrDefault(false)
            _state.update { it.copy(model = if (ready) TranslationModel.READY else TranslationModel.MISSING) }
        }
    }

    fun read(photo: Bitmap) {
        val thisScan = ++scan
        viewModelScope.launch {
            _state.update { it.copy(reading = true, error = null) }

            val text = runCatching { reader.read(photo) }.getOrElse {
                _state.update { it.copy(reading = false, error = R.string.menu_read_failed) }
                return@launch
            }

            // Only flag what this traveller cares about: a non-vegetarian who
            // eats everything doesn't need dashi warnings.
            val concerns = menuConcernsFor(profileRepository.tripRequest.first()?.profile)
            val lines = text.map { line -> MenuLine(line.text, null, menuFlags(line.text).filter { it.concern in concerns }, line.box) }

            _state.update { it.copy(reading = false, lines = lines, photo = photo) }
            translateLines(thisScan)
        }
    }

    fun downloadTranslation() {
        viewModelScope.launch {
            _state.update { it.copy(model = TranslationModel.DOWNLOADING) }
            val ok = runCatching { reader.downloadTranslation() }.isSuccess
            _state.update { it.copy(model = if (ok) TranslationModel.READY else TranslationModel.FAILED) }
            if (ok) translateLines(scan)
        }
    }

    fun scanAgain() {
        scan++
        _state.update { it.copy(lines = null, photo = null, error = null) }
    }

    fun cameraError(@StringRes message: Int) = _state.update { it.copy(error = message) }

    // Translates the lines one at a time, showing each as it's done. A line
    // that fails to translate just stays untranslated.
    private suspend fun translateLines(thisScan: Int) {
        if (_state.value.model != TranslationModel.READY) return
        val lines = _state.value.lines ?: return

        for ((i, line) in lines.withIndex()) {
            if (line.english != null) continue
            val english = runCatching { reader.translate(line.japanese) }.getOrNull() ?: continue
            if (scan != thisScan) return

            _state.update { state ->
                state.copy(lines = state.lines?.toMutableList()?.also { it[i] = it[i].copy(english = english) })
            }
        }
    }
}
