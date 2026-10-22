package com.osakapatnam.konnichiwah.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.osakapatnam.konnichiwah.ui.devcheck.DevCheckScreen
import com.osakapatnam.konnichiwah.ui.questionnaire.QuestionnaireScreen
import com.osakapatnam.konnichiwah.ui.suggestions.SuggestionsScreen
import kotlinx.serialization.Serializable

// Type-safe routes. Screens never talk to each other directly: the
// questionnaire saves its answers to ProfileRepository and the suggestions
// screen reads them back, so no arguments are passed between routes.
@Serializable
object QuestionnaireRoute

@Serializable
object SuggestionsRoute

@Serializable
object DevCheckRoute

@Composable
fun KonnichiNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = QuestionnaireRoute) {
        composable<QuestionnaireRoute> {
            // launchSingleTop stops a double tap from opening the same screen
            // twice on top of itself.
            QuestionnaireScreen(
                onSubmitted = { navController.navigate(SuggestionsRoute) { launchSingleTop = true } },
                onOpenDevCheck = { navController.navigate(DevCheckRoute) { launchSingleTop = true } },
            )
        }
        composable<SuggestionsRoute> {
            SuggestionsScreen(onEditAnswers = { navController.popBackStack() })
        }
        composable<DevCheckRoute> { DevCheckScreen() }
    }
}
