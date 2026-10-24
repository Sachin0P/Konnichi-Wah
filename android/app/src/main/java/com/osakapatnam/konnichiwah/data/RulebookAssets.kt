package com.osakapatnam.konnichiwah.data

import android.content.Context
import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import com.osakapatnam.konnichiwah.data.remote.PhraseCardDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// The Hindi version of one rule, from etiquette.hi.json.
@Serializable
data class HindiRuleDto(val title: String, val tip: String)

@Serializable
data class StationExitDto(
    val name: String,
    val name_ja: String? = null,
    val leads_to: String,
)

@Serializable
data class StationGuideDto(
    val id: String,
    val name: String,
    val name_ja: String? = null,
    val city: String,
    val lat: Double,
    val lng: Double,
    val lines: List<String> = emptyList(),
    val exits: List<StationExitDto> = emptyList(),
    val tips: List<String> = emptyList(),
)

// Reads the etiquette rulebook bundled in the APK. The build copies it from
// backend/rulebook/etiquette.json (see copyRulebook in app/build.gradle.kts),
// so tips are available with no network at all. The Hindi files sit beside
// the English ones, keyed by id; anything they don't cover stays in English.
@Singleton
class RulebookAssets @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val mutex = Mutex()
    private val cached = mutableMapOf<AppLanguage, Rulebook>()

    suspend fun load(language: AppLanguage = AppLanguage.ENGLISH): List<EtiquetteRuleDto> {
        val rules = read<List<EtiquetteRuleDto>>("etiquette.json")
        if (language != AppLanguage.HINDI) return rules

        val hindi = read<Map<String, HindiRuleDto>>("etiquette.hi.json")
        return rules.map { rule -> hindi[rule.id]?.let { rule.copy(title = it.title, tip = it.tip) } ?: rule }
    }

    // The indexed rulebook in the given language, parsed once and then kept
    // in memory.
    suspend fun rulebook(language: AppLanguage = AppLanguage.ENGLISH): Rulebook = mutex.withLock {
        cached[language] ?: Rulebook(load(language)).also { cached[language] = it }
    }

    // The hand-checked Japanese phrase cards (phrases.json), for offline use.
    // In Hindi, each card's meaning (the "english" line shown under the
    // Japanese) is in Hindi; the Japanese is the same.
    suspend fun phraseCards(language: AppLanguage = AppLanguage.ENGLISH): List<PhraseCardDto> {
        val cards = read<List<PhraseCardDto>>("phrases.json")
        if (language != AppLanguage.HINDI) return cards

        val hindi = read<Map<String, String>>("phrases.hi.json")
        return cards.map { card -> hindi[card.id]?.let { card.copy(english = it) } ?: card }
    }

    suspend fun stationGuides(language: AppLanguage = AppLanguage.ENGLISH): List<StationGuideDto> {
        val file = if (language == AppLanguage.HINDI) "stations.hi.json" else "stations.json"
        return runCatching { read<List<StationGuideDto>>(file) }.getOrElse { read<List<StationGuideDto>>("stations.json") }
    }

    private suspend inline fun <reified T> read(name: String): T = withContext(Dispatchers.IO) {
        val text = context.assets.open(name).bufferedReader().use { it.readText() }
        json.decodeFromString<T>(text)
    }
}
