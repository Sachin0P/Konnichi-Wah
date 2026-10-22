package com.osakapatnam.konnichiwah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.osakapatnam.konnichiwah.ui.KonnichiNavHost
import com.osakapatnam.konnichiwah.ui.theme.KonnichiTheme
import dagger.hilt.android.AndroidEntryPoint

// The app's only activity. Every screen is a Compose destination in
// KonnichiNavHost.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KonnichiTheme {
                KonnichiNavHost()
            }
        }
    }
}
