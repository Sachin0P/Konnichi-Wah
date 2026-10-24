package com.osakapatnam.konnichiwah.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.ui.LanguageSwitch
import com.osakapatnam.konnichiwah.ui.components.KCard
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.components.StatusDot
import com.osakapatnam.konnichiwah.ui.components.TownScene
import com.osakapatnam.konnichiwah.ui.labelRes
import com.osakapatnam.konnichiwah.ui.theme.DisplayFont
import com.osakapatnam.konnichiwah.ui.theme.Palette

// The landing screen: the trip at a glance, and one tap to each thing a
// traveller needs on the ground. Every shortcut opens an existing screen.
@Composable
fun HomeScreen(
    onPlan: () -> Unit,
    onAsk: () -> Unit,
    onMenu: () -> Unit,
    onCards: () -> Unit,
    onDirections: () -> Unit,
    onHelp: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Konnichi-Wah! 🇯🇵🇮🇳",
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Palette.Maroon,
                )
                Text(
                    stringResource(R.string.home_greeting),
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Palette.Mute,
                )
            }
            LanguageSwitch()
        }

        Hero(state, onPlan)

        Text(stringResource(R.string.home_right_now), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Shortcut("🚆", stringResource(R.string.home_train), stringResource(R.string.home_train_sub), onDirections, Modifier.weight(1f))
            Shortcut("🥗", stringResource(R.string.cards_title), stringResource(R.string.home_cards_sub), onCards, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Shortcut("📷", stringResource(R.string.home_menu), stringResource(R.string.home_menu_sub), onMenu, Modifier.weight(1f))
            Shortcut("🙇", stringResource(R.string.home_etiquette), stringResource(R.string.home_etiquette_sub), onPlan, Modifier.weight(1f))
        }
        Shortcut("🏥", stringResource(R.string.home_help), stringResource(R.string.home_help_sub), onHelp, Modifier.fillMaxWidth())

        KCard(modifier = Modifier.fillMaxWidth()) {
            StatusDot(stringResource(R.string.home_offline_title))
            Text(
                stringResource(if (state.hasTrip) R.string.home_offline_trip else R.string.home_offline_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

// The pink card with the treats, the slogan and the main button, over the
// little town.
@Composable
private fun Hero(state: HomeUiState, onPlan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.HeroPink),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp),
        ) {
            // The treats sit on the right, behind the text column.
            Image(
                painterResource(R.drawable.taiyaki),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 10.dp)
                    .width(118.dp)
                    .rotate(-6f),
            )
            Image(
                painterResource(R.drawable.matcha_drink),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-86).dp, y = 118.dp)
                    .width(50.dp)
                    .rotate(-8f),
            )
            Image(
                painterResource(R.drawable.matcha_bowl),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-12).dp, y = 128.dp)
                    .width(74.dp),
            )

            Column(
                modifier = Modifier
                    .padding(start = 22.dp, top = 22.dp, end = 8.dp)
                    .widthIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.home_hero_title),
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                )
                Text(heroLine(state), style = MaterialTheme.typography.bodyLarge, color = HeroInk)
                PrimaryButton(
                    stringResource(if (state.hasTrip) R.string.home_open_trip else R.string.q_plan_my_trip),
                    onClick = onPlan,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        TownScene()
    }
}

private val HeroInk = Palette.Maroon.copy(alpha = 0.75f)

// "7 days · Tokyo, Kyoto · Vegetarian" once there's a trip, otherwise the
// promise of the app.
@Composable
private fun heroLine(state: HomeUiState): String {
    if (!state.hasTrip) return stringResource(R.string.home_hero_new)
    return listOfNotNull(
        pluralStringResource(R.plurals.days, state.days, state.days),
        state.cities.take(3).joinToString().ifBlank { null },
        state.diet?.let { stringResource(it.labelRes) },
    ).joinToString(" · ")
}

@Composable
private fun Shortcut(emoji: String, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KCard(modifier = modifier, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Palette.Petal, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 24.sp)
        }
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
