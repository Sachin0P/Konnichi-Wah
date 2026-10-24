package com.osakapatnam.konnichiwah.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.PhraseDto

// A Japanese phrase card. Tapping it opens EnlargedPhrase.
@Composable
fun PhraseCard(phrase: PhraseDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(phrase.japanese, style = MaterialTheme.typography.titleLarge)
            Text(phrase.romaji, style = MaterialTheme.typography.bodyMedium)
            Text(phrase.english, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.card_tap_to_show), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// Full screen, big Japanese text, for holding the phone up to staff.
@Composable
fun EnlargedPhrase(phrase: PhraseDto, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(phrase.japanese, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Text(phrase.romaji, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Text(
                    phrase.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onDismiss) { Text(stringResource(R.string.card_close)) }
            }
        }
    }
}
