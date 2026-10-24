package com.osakapatnam.konnichiwah.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.ui.components.BackLink
import com.osakapatnam.konnichiwah.ui.components.EnlargedPhrase
import com.osakapatnam.konnichiwah.ui.components.PhraseCard
import com.osakapatnam.konnichiwah.ui.components.ScreenHeader
import com.osakapatnam.konnichiwah.ui.components.StatusDot

@Composable
fun CardsScreen(
    onBack: () -> Unit,
    viewModel: CardsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        BackLink(stringResource(R.string.back), onClick = onBack, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader(stringResource(R.string.cards_title), subtitle = stringResource(R.string.cards_hint)) }
            item { StatusDot(stringResource(R.string.menu_offline)) }

            if (state.yours.isNotEmpty()) {
                item { Text(stringResource(R.string.cards_yours), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
                items(state.yours, key = { it.id }) { card ->
                    PhraseCard(card.phrase(), onClick = { viewModel.enlarge(card) }, modifier = Modifier.fillMaxWidth())
                }
            }

            item { Text(stringResource(R.string.cards_all), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            items(state.others, key = { it.id }) { card ->
                PhraseCard(card.phrase(), onClick = { viewModel.enlarge(card) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    state.enlarged?.let { EnlargedPhrase(it, onDismiss = viewModel::dismissEnlarged) }
}
