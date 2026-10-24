package com.osakapatnam.konnichiwah.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osakapatnam.konnichiwah.ui.theme.DisplayFont
import com.osakapatnam.konnichiwah.ui.theme.Palette

// Small building blocks shared by every screen, so they all follow the same
// design: rounded cream cards, pill-shaped tags and buttons, serif titles.

// The colour pairs used for tags. Green means "suits you" or "offline",
// orange is for travel and "check this", petal is everything else.
enum class Tone(val background: Color, val content: Color) {
    PETAL(Palette.Petal, Palette.Maroon),
    GREEN(Palette.GreenBg, Palette.GreenInk),
    ORANGE(Palette.Orange, Palette.OrangeInk),
    MAROON(Palette.Maroon, Palette.Tip),
}

// A small, non-interactive tag, e.g. "JR Nara Line · 5 min".
@Composable
fun Pill(text: String, tone: Tone, modifier: Modifier = Modifier) {
    Text(
        text,
        color = tone.content,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(tone.background, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// A tappable pill, for secondary actions like "Add stop" or "Diet cards".
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, tone: Tone = Tone.PETAL, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = tone.background, contentColor = tone.content, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

// The main card style: cream-white with a thin warm border.
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: BorderStroke = BorderStroke(1.dp, Palette.Line),
    color: Color = Palette.Card,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

// The big serif title at the top of a screen, with an optional grey line under it.
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    brandKicker: String? = "Konnichi-Wah!",
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!brandKicker.isNullOrBlank()) {
            Text(
                brandKicker,
                fontFamily = DisplayFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Palette.Maroon,
            )
        }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// The red, fully rounded call-to-action button.
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.Sun,
            contentColor = Color.White,
            disabledContainerColor = Palette.Petal,
            disabledContentColor = Palette.Maroon.copy(alpha = 0.6f),
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// "← Back" in the top-left corner of screens that aren't tabs.
@Composable
fun BackLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp)) {
        Text("← $text", color = Palette.Maroon, style = MaterialTheme.typography.labelLarge)
    }
}

// "● Saved offline", in green.
@Composable
fun StatusDot(text: String, modifier: Modifier = Modifier, color: Color = Palette.Green) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

// A culture tip, set off with a red bar on the left like a quotation.
@Composable
fun TipQuote(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Tip, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
            // The bar is drawn rather than laid out, so it always matches the
            // tip's height.
            .drawBehind { drawRect(Palette.Sun, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 17.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

// The little flat town along the bottom of the home card: houses, cherry
// trees, Mount Fuji and a torii, over a river. Drawn on a 270 × 72 grid and
// scaled to fit, so it needs no image file.
@Composable
fun TownScene(modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(76.dp),
    ) {
        scale(size.width / 270f, size.height / 72f, pivot = Offset.Zero) {
            fun rect(c: Color, x: Float, y: Float, w: Float, h: Float) = drawRect(c, Offset(x, y), Size(w, h))
            val sky = Color(0xFFBFDAD8)
            val pink = Palette.Petal
            drawPath(
                Path().apply { moveTo(0f, 14f); cubicTo(60f, 0f, 210f, 0f, 270f, 14f); lineTo(270f, 72f); lineTo(0f, 72f); close() },
                Palette.Cream,
            )
            // Fuji with its snow cap.
            drawPath(Path().apply { moveTo(78f, 58f); lineTo(124f, 22f); lineTo(170f, 58f); close() }, sky)
            drawPath(
                Path().apply { moveTo(112f, 31f); lineTo(124f, 22f); lineTo(136f, 31f); lineTo(130f, 29f); lineTo(124f, 33f); lineTo(118f, 29f); close() },
                Color.White,
            )
            // Torii.
            rect(Palette.Sun, 126f, 34f, 3f, 24f); rect(Palette.Sun, 143f, 34f, 3f, 24f)
            rect(Palette.Sun, 122f, 31f, 28f, 4f); rect(Palette.Sun, 125f, 39f, 22f, 2.5f)
            // Two houses.
            rect(Color(0xFFEBC3B4), 14f, 38f, 36f, 20f); rect(Palette.Sun, 14f, 36f, 36f, 5f)
            rect(sky, 20f, 45f, 10f, 13f); rect(sky, 34f, 45f, 10f, 8f)
            rect(Color(0xFFDCCBB3), 184f, 40f, 40f, 18f); rect(Palette.Maroon, 184f, 37f, 40f, 4f)
            rect(sky, 190f, 46f, 12f, 12f); rect(sky, 208f, 46f, 10f, 7f)
            // Cherry trees.
            rect(Palette.Maroon, 66f, 48f, 2.5f, 10f); drawCircle(pink, 9f, Offset(67f, 43f))
            rect(Palette.Maroon, 168f, 46f, 2.5f, 12f); drawCircle(pink, 9f, Offset(169f, 41f))
            drawCircle(pink, 8f, Offset(256f, 48f))
            // Path and river.
            rect(Color(0xFFF3E3C3), 0f, 58f, 270f, 14f)
            drawPath(
                Path().apply {
                    moveTo(0f, 66f); cubicTo(40f, 58f, 80f, 58f, 120f, 64f); cubicTo(170f, 68f, 220f, 58f, 270f, 62f)
                    lineTo(270f, 72f); lineTo(0f, 72f); close()
                },
                sky,
            )
        }
    }
}
