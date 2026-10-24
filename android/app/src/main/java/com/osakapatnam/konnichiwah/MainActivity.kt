package com.osakapatnam.konnichiwah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.data.LanguageRepository
import com.osakapatnam.konnichiwah.ui.InLanguage
import com.osakapatnam.konnichiwah.ui.KonnichiNavHost
import com.osakapatnam.konnichiwah.ui.theme.KonnichiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// The app's only activity. Every screen is a Compose destination in
// KonnichiNavHost, shown in the traveller's chosen language.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var languageRepository: LanguageRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Wait for the (very quick) DataStore read, so the first screen
            // doesn't flash up in the wrong language.
            val language by languageRepository.language.collectAsStateWithLifecycle(initialValue = null)
            val current = language ?: return@setContent

            InLanguage(current) {
                KonnichiTheme {
                    KonnichiNavHost()
                }
            }
        }
    }
}
