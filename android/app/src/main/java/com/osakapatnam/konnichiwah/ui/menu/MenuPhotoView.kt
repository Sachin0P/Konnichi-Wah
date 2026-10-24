package com.osakapatnam.konnichiwah.ui.menu

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

// The overlay's colours are fixed rather than taken from the theme: they sit on
// a photo, which looks the same in light and dark mode.
private val labelBackground = Color.White
private val warningBackground = Color(0xFFFFE3E0)
private val warningOutline = Color(0xFFC62828)
private val labelText = Color(0xFF1B1B1B)
private val readOutline = Color.White.copy(alpha = 0.7f)

// The photo of the menu with each line's translation drawn over the Japanese,
// like Google Lens. Lines with a warning for the traveller's diet get a red
// outline and a "⚠". Pinch to zoom (menus are dense) and drag to move; tap a
// line to open it in full.
//
// The photo is drawn fitted inside the view. Zooming scales the whole view
// (photo and labels together) with a graphics layer, so the labels stay on
// their lines and the text stays sharp.
@Composable
fun MenuPhotoView(photo: Bitmap, lines: List<MenuLine>, onTap: (MenuLine) -> Unit, modifier: Modifier = Modifier) {
    val image = remember(photo) { photo.asImageBitmap() }
    val measurer = rememberTextMeasurer()

    // What each line shows: its translation, or (before it's translated, or
    // without the translation model) just its warnings.
    val labels = lines.map { line ->
        val warnings = line.flags.map { stringResource(it.concern.label) }.distinct()
        when {
            line.english != null && warnings.isNotEmpty() -> "⚠ " + line.english
            line.english != null -> line.english
            warnings.isNotEmpty() -> "⚠ " + warnings.joinToString()
            else -> null
        }
    }

    // Text sizes are worked out once per line and size, not on every frame.
    val fitted = remember(photo) { mutableMapOf<Triple<String, Int, Int>, TextLayoutResult>() }

    var zoom by remember(photo) { mutableFloatStateOf(1f) }
    var offset by remember(photo) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // Where the photo sits in the view, fitted and centred.
        val scale = min(width / photo.width, height / photo.height)
        val left = (width - photo.width * scale) / 2
        val top = (height - photo.height * scale) / 2

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Pinch and drag are handled outside the graphics layer, so the
                // gesture's numbers are in screen pixels.
                .pointerInput(photo) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                        // Keep the point between the fingers where it is.
                        val moved = centroid - (centroid - offset) * (newZoom / zoom) + pan
                        zoom = newZoom
                        offset = Offset(
                            moved.x.coerceIn(width - width * zoom, 0f),
                            moved.y.coerceIn(height - height * zoom, 0f),
                        )
                    }
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                }
                // Inside the layer, taps arrive in unzoomed coordinates, so we
                // only have to undo the fitting to find the line.
                .pointerInput(photo, lines) {
                    detectTapGestures { tap ->
                        val x = (tap.x - left) / scale
                        val y = (tap.y - top) / scale
                        val slop = 12.dp.toPx() / scale
                        lines.filter { it.box != null }
                            .filter { line ->
                                val b = line.box!!
                                x >= b.left - slop && x <= b.right + slop && y >= b.top - slop && y <= b.bottom + slop
                            }
                            .minByOrNull { it.box!!.width() * it.box!!.height() }
                            ?.let(onTap)
                    }
                },
        ) {
            drawImage(
                image,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize((photo.width * scale).roundToInt(), (photo.height * scale).roundToInt()),
                filterQuality = FilterQuality.Medium,
            )

            // Lines with a warning are drawn last, so that where ML Kit's boxes
            // overlap (it sometimes reads a row twice), the warning is on top.
            val order = lines.indices.sortedBy { lines[it].flags.isNotEmpty() }
            for (i in order) {
                val line = lines[i]
                val box = line.box ?: continue
                // ML Kit's boxes hug the text, so grow them a little to cover
                // the Japanese completely.
                val grow = 2.dp.toPx()
                val x = left + box.left * scale - grow
                val y = top + box.top * scale - grow
                val w = box.width() * scale + grow * 2
                val h = box.height() * scale + grow * 2
                val warning = line.flags.isNotEmpty()
                val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                val label = labels[i]
                if (label == null) {
                    // Not translated yet: just show we read something there.
                    drawRoundRect(readOutline, Offset(x, y), Size(w, h), radius, style = Stroke(1.dp.toPx()))
                    continue
                }

                drawRoundRect(if (warning) warningBackground else labelBackground, Offset(x, y), Size(w, h), radius)
                if (warning) drawRoundRect(warningOutline, Offset(x, y), Size(w, h), radius, style = Stroke(2.dp.toPx()))

                // Japanese is often written top to bottom. For a tall, narrow
                // line, turn the English to run down the line instead.
                val vertical = h > w * 1.5f
                val (across, deep) = if (vertical) h to w else w to h
                val layout = fitted.getOrPut(Triple(label, across.roundToInt(), deep.roundToInt())) {
                    fitText(measurer, label, across - 4.dp.toPx(), deep)
                }
                val inset = (deep - layout.size.height) / 2

                if (vertical) {
                    // Rotating a quarter turn about the box's top-left corner
                    // swings text drawn above the box into it, reading downwards.
                    rotate(90f, pivot = Offset(x, y)) {
                        drawText(layout, topLeft = Offset(x + 2.dp.toPx(), y - w + inset))
                    }
                } else {
                    drawText(layout, topLeft = Offset(x + 2.dp.toPx(), y + inset))
                }
            }
        }
    }
}

// The largest text that fits the box, wrapping onto more lines if it must.
// Long translations in small boxes can still end up tiny; zooming in, or
// tapping the line, shows them properly.
private fun DrawScope.fitText(measurer: TextMeasurer, text: String, maxWidth: Float, maxHeight: Float): TextLayoutResult {
    fun measure(px: Float) = measurer.measure(
        text,
        TextStyle(color = labelText, fontSize = (px / (density * fontScale)).sp, lineHeight = (px * 1.1f / (density * fontScale)).sp),
        constraints = Constraints(maxWidth = maxWidth.roundToInt().coerceAtLeast(1)),
    )

    var px = maxHeight * 0.72f
    var layout = measure(px)
    repeat(10) {
        if (layout.size.height <= maxHeight && !layout.didOverflowWidth) return layout
        px *= 0.85f
        layout = measure(px)
    }
    return layout
}
