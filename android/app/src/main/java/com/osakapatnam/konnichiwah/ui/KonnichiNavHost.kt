package com.osakapatnam.konnichiwah.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.ui.cards.CardsScreen
import com.osakapatnam.konnichiwah.ui.chat.ChatScreen
import com.osakapatnam.konnichiwah.ui.components.TabIcons
import com.osakapatnam.konnichiwah.ui.devcheck.DevCheckScreen
import com.osakapatnam.konnichiwah.ui.directions.DirectionsScreen
import com.osakapatnam.konnichiwah.ui.food.FoodScreen
import com.osakapatnam.konnichiwah.ui.home.HomeScreen
import com.osakapatnam.konnichiwah.ui.itinerary.ItineraryScreen
import com.osakapatnam.konnichiwah.ui.menu.MenuScreen
import com.osakapatnam.konnichiwah.ui.nearby.NearbyScreen
import com.osakapatnam.konnichiwah.ui.questionnaire.QuestionnaireScreen
import com.osakapatnam.konnichiwah.ui.suggestions.SuggestionsScreen
import com.osakapatnam.konnichiwah.ui.theme.Palette
import kotlinx.serialization.Serializable

// Type-safe routes. Screens never talk to each other directly: the
// questionnaire saves its answers to ProfileRepository, the suggestions screen
// reads them back, and the itinerary screen reads the trip from Room, so no
// arguments are passed between routes.
@Serializable
object HomeRoute

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

@Serializable
object MenuRoute

// The food search is for one stop, so its route carries the stop's name, city
// and coordinates (type-safe arguments; FoodViewModel reads them back with
// toRoute()). Nullable Doubles aren't supported as navigation arguments, so
// lat and lng are NaN when the stop has no coordinates.
@Serializable
data class FoodRoute(val placeName: String, val city: String, val lat: Double = Double.NaN, val lng: Double = Double.NaN)

@Serializable
object DevCheckRoute

// Directions can open with a destination (a stop, with its coordinates when it
// has them) or empty, from Home. Like FoodRoute, missing coordinates are NaN.
@Serializable
data class DirectionsRoute(val toName: String = "", val toLat: Double = Double.NaN, val toLng: Double = Double.NaN, val city: String = "")

@Serializable
object NearbyRoute

// The four bottom tabs. Plan covers the whole planning flow (questionnaire,
// suggestions, itinerary and a stop's food search).
private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, TabIcons.Home),
    PLAN(R.string.tab_plan, TabIcons.Plan),
    MENU(R.string.it_menu, TabIcons.Camera),
    ASK(R.string.it_ask, TabIcons.Chat),
}

private fun NavDestination.tab(): Tab? = when {
    hasRoute<HomeRoute>() -> Tab.HOME
    hasRoute<QuestionnaireRoute>() || hasRoute<SuggestionsRoute>() || hasRoute<ItineraryRoute>() || hasRoute<FoodRoute>() -> Tab.PLAN
    hasRoute<MenuRoute>() -> Tab.MENU
    hasRoute<ChatRoute>() -> Tab.ASK
    else -> null
}

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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // Tabs sit on top of Home: switching tab replaces whatever was above
    // Home, so Back from any tab returns Home, and Back from Home leaves.
    // It reads navController.currentDestination rather than `destination`:
    // Compose remembers this function across recompositions, so a captured
    // `destination` would be stale (stuck on Home, making Home a no-op).
    fun openTab(tab: Tab) {
        if (navController.currentDestination?.tab() == tab) return
        when (tab) {
            Tab.HOME -> navController.popBackStack(HomeRoute, inclusive = false)
            Tab.PLAN -> navController.navigateTab(if (hasItinerary == true) ItineraryRoute else QuestionnaireRoute)
            Tab.MENU -> navController.navigateTab(MenuRoute)
            Tab.ASK -> navController.navigateTab(ChatRoute)
        }
    }

    Scaffold(
        bottomBar = {
            // The developer check screen is a tool, not part of the app.
            if (destination != null && !destination.hasRoute<DevCheckRoute>()) {
                BottomBar(selected = destination.tab(), onSelect = ::openTab)
            }
        },
    ) { padding ->
        // consumeWindowInsets tells the screens inside that the system bars
        // (and the tab bar) are already padded for, so their own
        // navigationBarsPadding() and imePadding() don't add them again.
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    onPlan = dropUnlessResumed { openTab(Tab.PLAN) },
                    onAsk = dropUnlessResumed { openTab(Tab.ASK) },
                    onMenu = dropUnlessResumed { openTab(Tab.MENU) },
                    onCards = dropUnlessResumed { navController.navigate(CardsRoute) { launchSingleTop = true } },
                    onDirections = dropUnlessResumed { navController.navigate(DirectionsRoute()) { launchSingleTop = true } },
                    onHelp = dropUnlessResumed { navController.navigate(NearbyRoute) { launchSingleTop = true } },
                )
            }
            composable<QuestionnaireRoute> {
                QuestionnaireScreen(
                    onSubmitted = dropUnlessResumed { navController.navigate(SuggestionsRoute) { launchSingleTop = true } },
                    onOpenDevCheck = dropUnlessResumed { navController.navigate(DevCheckRoute) { launchSingleTop = true } },
                )
            }
            composable<SuggestionsRoute> {
                SuggestionsScreen(
                    onEditAnswers = dropUnlessResumed { navController.safePop() },
                    // Once the trip is built, the planning screens are done
                    // with: clear them so Back from the itinerary goes Home.
                    onItineraryBuilt = dropUnlessResumed { navController.navigateTab(ItineraryRoute) },
                )
            }
            composable<ItineraryRoute> {
                // dropUnlessResumed only wraps no-argument callbacks, so the
                // food search (which takes the stop) checks the lifecycle itself.
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                ItineraryScreen(
                    onNewTrip = dropUnlessResumed { navController.navigate(QuestionnaireRoute) { launchSingleTop = true } },
                    onCards = dropUnlessResumed { navController.navigate(CardsRoute) { launchSingleTop = true } },
                    onFindFood = { stop ->
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            val route = FoodRoute(stop.placeName, stop.city, stop.lat ?: Double.NaN, stop.lng ?: Double.NaN)
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    },
                    onDirections = { stop ->
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            val route = DirectionsRoute(stop.placeName, stop.lat ?: Double.NaN, stop.lng ?: Double.NaN, stop.city)
                            navController.navigate(route) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable<ChatRoute> { ChatScreen() }
            composable<CardsRoute> {
                CardsScreen(onBack = dropUnlessResumed { navController.safePop() })
            }
            composable<FoodRoute> {
                FoodScreen(
                    onBack = dropUnlessResumed { navController.safePop() },
                    onDietCards = dropUnlessResumed { navController.navigate(CardsRoute) { launchSingleTop = true } },
                )
            }
            composable<MenuRoute> {
                MenuScreen(onDietCards = dropUnlessResumed { navController.navigate(CardsRoute) { launchSingleTop = true } })
            }
            composable<DirectionsRoute> {
                DirectionsScreen(onBack = dropUnlessResumed { navController.safePop() })
            }
            composable<NearbyRoute> {
                // Like the stop's food search, the place is an argument, so
                // the lifecycle check is done by hand.
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                NearbyScreen(
                    onBack = dropUnlessResumed { navController.safePop() },
                    onDirections = { place ->
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.navigate(DirectionsRoute(place.name, place.lat, place.lng)) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable<DevCheckRoute> { DevCheckScreen() }
        }
    }
}

// Opens a tab's screen on top of Home, clearing anything in between.
private fun <T : Any> NavController.navigateTab(route: T) {
    navigate(route) {
        popUpTo(HomeRoute)
        launchSingleTop = true
    }
}

// safePop goes back one screen, but never pops the last one: an empty back
// stack leaves the NavHost showing nothing at all.
private fun NavController.safePop() {
    if (previousBackStackEntry != null) {
        popBackStack()
    }
}

// The design's tab bar: the chosen tab's icon sits in a filled red tile.
@Composable
private fun BottomBar(selected: Tab?, onSelect: (Tab) -> Unit) {
    Surface(color = Palette.Card, border = BorderStroke(1.dp, Palette.Line)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 32.dp)
                            .background(if (on) Palette.Sun else Color.Transparent, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = if (on) Color.White else Palette.Mute, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) Palette.Sun else Palette.Mute,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}
