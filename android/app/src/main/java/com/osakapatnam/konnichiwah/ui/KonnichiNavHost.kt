package com.osakapatnam.konnichiwah.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.osakapatnam.konnichiwah.ui.cards.CardsScreen
import com.osakapatnam.konnichiwah.ui.chat.ChatScreen
import com.osakapatnam.konnichiwah.ui.devcheck.DevCheckScreen
import com.osakapatnam.konnichiwah.ui.food.FoodScreen
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
object ChatRoute

@Serializable
object CardsRoute

// The food search is for one stop, so its route carries the stop's name, city
// and coordinates (type-safe arguments; FoodViewModel reads them back with
// toRoute()). Nullable Doubles aren't supported as navigation arguments, so
// lat and lng are NaN when the stop has no coordinates.
@Serializable
data class FoodRoute(val placeName: String, val city: String, val lat: Double = Double.NaN, val lng: Double = Double.NaN)

@Serializable
object DevCheckRoute

// Every navigation callback is wrapped in dropUnlessResumed. A screen stops
// being "resumed" once we navigate away from it, so a second tap during the
// transition is ignored. Without this, double-tapping "Back" popped two
// screens, including the last one, leaving the NavHost blank (a white screen
// with no way out). Taps landing in the very same frame can still slip past
// dropUnlessResumed, so popping also goes through safePop(), which never
// removes the last screen. launchSingleTop on navigate() calls stops a double
// tap from opening the same screen twice.
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
            QuestionnaireScreen(
                onSubmitted = dropUnlessResumed { navController.navigate(SuggestionsRoute) { launchSingleTop = true } },
                onOpenDevCheck = dropUnlessResumed { navController.navigate(DevCheckRoute) { launchSingleTop = true } },
            )
        }
        composable<SuggestionsRoute> {
            SuggestionsScreen(
                onEditAnswers = dropUnlessResumed { navController.safePop() },
                // Once the trip is built, the planning screens are done with:
                // clear them so Back from the itinerary leaves the app.
                onItineraryBuilt = dropUnlessResumed {
                    navController.navigate(ItineraryRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable<ItineraryRoute> {
            // dropUnlessResumed only wraps no-argument callbacks, so the
            // food search (which takes the stop) checks the lifecycle itself.
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            ItineraryScreen(
                onNewTrip = dropUnlessResumed { navController.navigate(QuestionnaireRoute) { launchSingleTop = true } },
                onAsk = dropUnlessResumed { navController.navigate(ChatRoute) { launchSingleTop = true } },
                onCards = dropUnlessResumed { navController.navigate(CardsRoute) { launchSingleTop = true } },
                onFindFood = { stop ->
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        val route = FoodRoute(stop.placeName, stop.city, stop.lat ?: Double.NaN, stop.lng ?: Double.NaN)
                        navController.navigate(route) { launchSingleTop = true }
                    }
                },
            )
        }
        composable<ChatRoute> {
            ChatScreen(onBack = dropUnlessResumed { navController.safePop() })
        }
        composable<CardsRoute> {
            CardsScreen(onBack = dropUnlessResumed { navController.safePop() })
        }
        composable<FoodRoute> {
            FoodScreen(
                onBack = dropUnlessResumed { navController.safePop() },
                onDietCards = dropUnlessResumed { navController.navigate(CardsRoute) { launchSingleTop = true } },
            )
        }
        composable<DevCheckRoute> { DevCheckScreen() }
    }
}

// safePop goes back one screen, but never pops the last one: an empty back
// stack leaves the NavHost showing nothing at all.
private fun NavController.safePop() {
    if (previousBackStackEntry != null) {
        popBackStack()
    }
}
