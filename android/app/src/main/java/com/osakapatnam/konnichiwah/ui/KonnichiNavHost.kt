package com.osakapatnam.konnichiwah.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.osakapatnam.konnichiwah.ui.devcheck.DevCheckScreen
import com.osakapatnam.konnichiwah.ui.itinerary.ItineraryScreen
import com.osakapatnam.konnichiwah.ui.questionnaire.QuestionnaireScreen
import com.osakapatnam.konnichiwah.ui.suggestions.SuggestionsScreen
import kotlinx.serialization.Serializable

// Type-safe routes. Screens never talk to each other directly: the
// questionnaire saves its answers to ProfileRepository, the suggestions screen
// reads them back, and the itinerary screen reads the trip from Room, so no
// arguments are passed between routes.
@Serializable
object QuestionnaireRoute

@Serializable
object SuggestionsRoute

@Serializable
object ItineraryRoute

@Serializable
object DevCheckRoute

@Composable
fun KonnichiNavHost(startViewModel: StartViewModel = hiltViewModel()) {
    val hasItinerary by startViewModel.hasItinerary.collectAsStateWithLifecycle()

    // Wait for the (very quick) Room check before choosing the first screen.
    val startAtItinerary = hasItinerary ?: return

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (startAtItinerary) ItineraryRoute else QuestionnaireRoute,
    ) {
        composable<QuestionnaireRoute> {
            // launchSingleTop stops a double tap from opening the same screen
            // twice on top of itself.
            QuestionnaireScreen(
                onSubmitted = { navController.navigate(SuggestionsRoute) { launchSingleTop = true } },
                onOpenDevCheck = { navController.navigate(DevCheckRoute) { launchSingleTop = true } },
            )
        }
        composable<SuggestionsRoute> {
            SuggestionsScreen(
                onEditAnswers = { navController.popBackStack() },
                // Once the trip is built, the planning screens are done with:
                // clear them so Back from the itinerary leaves the app.
                onItineraryBuilt = {
                    navController.navigate(ItineraryRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable<ItineraryRoute> {
            ItineraryScreen(
                onNewTrip = { navController.navigate(QuestionnaireRoute) { launchSingleTop = true } },
            )
        }
        composable<DevCheckRoute> { DevCheckScreen() }
    }
}
