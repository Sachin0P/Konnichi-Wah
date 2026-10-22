package com.osakapatnam.konnichiwah.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.osakapatnam.konnichiwah.ui.devcheck.DevCheckScreen
import kotlinx.serialization.Serializable

// Type-safe routes. The questionnaire, suggestions and itinerary screens will
// be added here as new destinations.
@Serializable
object DevCheckRoute

@Composable
fun KonnichiNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = DevCheckRoute) {
        composable<DevCheckRoute> { DevCheckScreen() }
    }
}
