package com.osakapatnam.konnichiwah.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.PhraseDto
import com.osakapatnam.konnichiwah.ui.theme.Palette

// A Japanese phrase card. Tapping it opens EnlargedPhrase.
@Composable
fun PhraseCard(phrase: PhraseDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KCard(modifier = modifier, onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(phrase.japanese, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, lineHeight = 30.sp)
            Text(phrase.romaji, style = MaterialTheme.typography.bodyMedium, color = Palette.Maroon)
            Text(phrase.english, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Pill(stringResource(R.string.card_tap_to_show), Tone.PETAL, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// Full screen, big Japanese text, for holding the phone up to staff.
@Composable
fun EnlargedPhrase(phrase: PhraseDto, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Palette.Card,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(phrase.japanese, fontSize = 34.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, textAlign = TextAlign.Center)
                Text(phrase.romaji, style = MaterialTheme.typography.bodyLarge, color = Palette.Maroon, textAlign = TextAlign.Center)
                Text(
                    phrase.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(stringResource(R.string.card_close), onClick = onDismiss)
            }
        }
    }
}
