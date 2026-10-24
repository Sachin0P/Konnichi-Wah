package com.osakapatnam.konnichiwah.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// The languages the app can be used in. tag is what the backend expects
// ("language" in chat and food requests) and the Android locale to use.
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    HINDI("hi");

    val locale: Locale get() = Locale.forLanguageTag(tag)
}

// Remembers the traveller's choice of language on the phone. Until they
// choose, we follow the phone: Hindi if it's set to Hindi, English otherwise.
//
// The app switches language itself (see InLanguage), rather than through
// Android's per-app language setting, so the switch is one tap and instant,
// which matters in a demo, and works the same on every Android version.
@Singleton
class LanguageRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @param:ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("language")

    val language: Flow<AppLanguage> = dataStore.data.map { prefs ->
        AppLanguage.entries.firstOrNull { it.tag == prefs[key] } ?: phoneLanguage()
    }

    suspend fun set(language: AppLanguage) {
        dataStore.edit { it[key] = language.tag }
    }

    // The app's strings in the chosen language, for text made outside the
    // screens: notifications, and the trip's title.
    suspend fun resources(): Resources = context.inLanguage(language.first()).resources

    private fun phoneLanguage() =
        if (Locale.getDefault().language == AppLanguage.HINDI.tag) AppLanguage.HINDI else AppLanguage.ENGLISH
}

// A context whose resources are in the given language.
fun Context.inLanguage(language: AppLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(language.locale)
    return createConfigurationContext(config)
}
