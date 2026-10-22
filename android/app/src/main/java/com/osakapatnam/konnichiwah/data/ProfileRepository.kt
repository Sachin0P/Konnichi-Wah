package com.osakapatnam.konnichiwah.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.osakapatnam.konnichiwah.data.remote.TripRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// Stores the questionnaire answers on the phone, so they survive restarts and
// work offline. We keep the whole trip request as one JSON string: it's
// always read and written together, and it's exactly what the planner needs.
@Singleton
class ProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val tripRequestKey = stringPreferencesKey("trip_request")

    // Emits null until the questionnaire has been completed once. A value
    // that no longer parses (e.g. after an enum was renamed) is treated the
    // same way, so the user simply answers again rather than the app crashing.
    val tripRequest: Flow<TripRequestDto?> = dataStore.data.map { prefs ->
        prefs[tripRequestKey]?.let { runCatching { json.decodeFromString<TripRequestDto>(it) }.getOrNull() }
    }

    suspend fun save(request: TripRequestDto) {
        dataStore.edit { prefs -> prefs[tripRequestKey] = json.encodeToString(request) }
    }
}
