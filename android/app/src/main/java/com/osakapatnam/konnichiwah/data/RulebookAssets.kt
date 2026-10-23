package com.osakapatnam.konnichiwah.data

import android.content.Context
import com.osakapatnam.konnichiwah.data.remote.EtiquetteRuleDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Reads the etiquette rulebook bundled in the APK. The build copies it from
// backend/rulebook/etiquette.json (see copyRulebook in app/build.gradle.kts),
// so tips are available with no network at all.
@Singleton
class RulebookAssets @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var cached: Rulebook? = null

    suspend fun load(): List<EtiquetteRuleDto> = withContext(Dispatchers.IO) {
        val text = context.assets.open("etiquette.json").bufferedReader().use { it.readText() }
        json.decodeFromString<List<EtiquetteRuleDto>>(text)
    }

    // The indexed rulebook, parsed once and then kept in memory.
    suspend fun rulebook(): Rulebook = mutex.withLock {
        cached ?: Rulebook(load()).also { cached = it }
    }
}
