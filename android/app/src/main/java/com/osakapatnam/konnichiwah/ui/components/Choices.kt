package com.osakapatnam.konnichiwah.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.osakapatnam.konnichiwah.ui.theme.Palette

// A titled group of answers on a questionnaire step.
@Composable
fun Question(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

// Wrapping rows of pill chips: maroon when chosen, sakura pink when not. Used
// for both single choice (selected is "== the current value") and multiple
// choice (selected is "in the current set").
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    isSelected: (T) -> Boolean,
    onClick: (T) -> Unit,
    label: @Composable (T) -> String,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val selected = isSelected(option)
            Surface(
                shape = CircleShape,
                color = if (selected) Palette.Maroon else Palette.Petal,
                contentColor = if (selected) Palette.Tip else Palette.Maroon,
                // selectable() tells screen readers this is a choice, and
                // whether it's chosen.
                modifier = Modifier.selectable(selected = selected, role = Role.Checkbox, onClick = { onClick(option) }),
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// One choice out of a few short options, as a single rounded bar with the
// chosen one in bold red (the design's "Relaxed | Balanced | Packed").
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    onClick: (T) -> Unit,
    label: @Composable (T) -> String,
) {
    Surface(
        shape = CircleShape,
        color = Palette.Card,
        border = BorderStroke(1.dp, Palette.Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { option ->
                val on = option == selected
                Surface(
                    shape = CircleShape,
                    color = if (on) Palette.Petal else Palette.Card,
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onClick(option) }),
                ) {
                    Text(
                        label(option),
                        color = if (on) Palette.Sun else Palette.Ink,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
