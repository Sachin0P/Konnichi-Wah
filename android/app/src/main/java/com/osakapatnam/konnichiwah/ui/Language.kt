package com.osakapatnam.konnichiwah.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.AppLanguage
import com.osakapatnam.konnichiwah.data.LanguageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

// Shows content in the given language. stringResource() reads its strings
// from LocalResources, so we swap in resources in that language, plus a
// matching context (for code that reads resources from it) and configuration
// (the date picker reads its locale from there). Switching language
// recomposes the screen in place, without restarting the activity.
//
// The context wraps the activity rather than replacing it, because things
// like hiltViewModel() look for the activity behind LocalContext.
@Composable
fun InLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val (localContext, localConfiguration) = remember(context, configuration, language) {
        val config = Configuration(configuration).apply { setLocale(language.locale) }
        LanguageContext(context, context.createConfigurationContext(config).resources) to config
    }

    CompositionLocalProvider(
        LocalContext provides localContext,
        LocalResources provides localContext.resources,
        LocalConfiguration provides localConfiguration,
        content = content,
    )
}

private class LanguageContext(base: Context, private val localResources: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localResources
}

// The locale the screen is showing, for formatting dates and months.
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val repository: LanguageRepository,
) : ViewModel() {

    fun toggle() {
        viewModelScope.launch {
            val now = repository.language.first()
            repository.set(if (now == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH)
        }
    }
}

// A top-bar button showing the other language in its own script: "हिन्दी"
// in English, "English" in Hindi.
@Composable
fun LanguageSwitch(viewModel: LanguageViewModel = hiltViewModel()) {
    TextButton(onClick = viewModel::toggle) { Text(stringResource(R.string.switch_language)) }
}
