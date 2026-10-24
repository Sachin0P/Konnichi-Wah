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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osakapatnam.konnichiwah.data.hasJapanese
import com.osakapatnam.konnichiwah.data.isNoiseOrFragment
import com.osakapatnam.konnichiwah.data.isPriceOrNumber
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

// The overlay's colours are fixed rather than taken from the theme: they sit on
// a photo, which looks the same in light and dark mode.
private val labelBackground = Color(0xF5FFFFFF)
private val labelBorder = Color(0x33000000)
private val warningBackground = Color(0xFFFFECEB)
private val warningOutline = Color(0xFFBA1A1A)
private val labelText = Color(0xFF141414)
private val warningText = Color(0xFF8B0000)

// The photo of the menu with each line's translation drawn over the Japanese,
// like Google Lens. Lines with a warning for the traveller's diet get a distinct
// red outline and a "⚠". Pinch to zoom and drag to move; tap a line to open its
// full details.
//
// To prevent visual clutter, overlays are NOT drawn over lines that contain no
// Japanese text and no dietary warnings (such as prices "¥450", English titles
// "Meal", or icons), as they are already legible on the original menu.
@Composable
fun MenuPhotoView(
    photo: Bitmap,
    lines: List<MenuLine>,
    warningsOnly: Boolean = false,
    onTap: (MenuLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = remember(photo) { photo.asImageBitmap() }
    val measurer = rememberTextMeasurer()

    // What each line shows: its translation, or just its warnings.
    val labels = lines.map { line ->
        val warnings = line.flags.map { stringResource(it.concern.label) }.distinct()
        when {
            line.english != null && warnings.isNotEmpty() -> "⚠ " + line.english
            line.english != null -> line.english
            warnings.isNotEmpty() -> "⚠ " + warnings.joinToString()
            else -> null
        }
    }

    // Suppress duplicate identical stickers that overlap (e.g. furigana "しょくじ" right above "お食事" both translated to "Meals")
    val isDuplicateFurigana: (Int) -> Boolean = { i ->
        val boxI = lines[i].box
        val labelI = labels[i]
        if (boxI == null || labelI == null) {
            false
        } else {
            lines.indices.any { j ->
                if (j != i && labels[j]?.equals(labelI, ignoreCase = true) == true) {
                    val boxJ = lines[j].box
                    if (boxJ != null) {
                        val jIsLarger = (boxJ.width() * boxJ.height()) > (boxI.width() * boxI.height())
                        val xOverlap = boxI.left < boxJ.right && boxI.right > boxJ.left
                        val yNear = abs(boxI.centerY() - boxJ.centerY()) < (boxJ.height() * 2)
                        jIsLarger && xOverlap && yNear
                    } else false
                } else false
            }
        }
    }

    // Filter which lines are drawn to eliminate clutter.
    val shouldDrawLine: (Int) -> Boolean = { i ->
        val line = lines[i]
        val hasWarn = line.flags.isNotEmpty()
        val hasJp = hasJapanese(line.japanese)
        val label = labels[i]

        if (line.box == null) {
            false
        } else if (warningsOnly) {
            hasWarn && label != null
        } else if (hasWarn) {
            label != null
        } else if (isPriceOrNumber(line.japanese) || isPriceOrNumber(label.orEmpty())) {
            // Prices like "¥450" or "980 円" are already perfectly readable on the menu itself.
            false
        } else if (isNoiseOrFragment(line.japanese)) {
            // Noise or single kana particles like "の" or "ちの"
            false
        } else if (isDuplicateFurigana(i)) {
            // Duplicate furigana labels right above the kanji word
            false
        } else if (hasJp) {
            label != null && !label.equals(line.japanese, ignoreCase = true)
        } else {
            // Lines that contain no Japanese characters and no diet flags
            // (e.g. English titles like "Meal", "Beverages", numbers)
            false
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
                .pointerInput(photo, lines, warningsOnly) {
                    detectTapGestures { tap ->
                        val x = (tap.x - left) / scale
                        val y = (tap.y - top) / scale
                        val slop = 14.dp.toPx() / scale
                        lines.indices
                            .filter(shouldDrawLine)
                            .map { lines[it] }
                            .filter { line ->
                                val b = line.box ?: return@filter false
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

            // Lines with a warning are drawn last, so that where boxes overlap,
            // the dietary warning is always visible on top.
            val order = lines.indices.filter(shouldDrawLine).sortedBy { lines[it].flags.isNotEmpty() }
            for (i in order) {
                val line = lines[i]
                val box = line.box ?: continue
                val label = labels[i] ?: continue

                // Gentle padding so adjacent rows in dense menus don't fuse together
                val growX = 1.dp.toPx()
                val growY = 0.5f.dp.toPx()
                val x = left + box.left * scale - growX
                val y = top + box.top * scale - growY
                val w = box.width() * scale + growX * 2
                val h = box.height() * scale + growY * 2
                val warning = line.flags.isNotEmpty()
                val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                drawRoundRect(if (warning) warningBackground else labelBackground, Offset(x, y), Size(w, h), radius)
                drawRoundRect(
                    if (warning) warningOutline else labelBorder,
                    Offset(x, y),
                    Size(w, h),
                    radius,
                    style = Stroke(if (warning) 1.5.dp.toPx() else 0.75.dp.toPx()),
                )

                // Japanese is often written top to bottom. For a tall, narrow
                // line, turn the English to run down the line instead.
                val vertical = h > w * 1.5f
                val (across, deep) = if (vertical) h to w else w to h
                val layout = fitted.getOrPut(Triple(label, across.roundToInt(), deep.roundToInt())) {
                    fitText(measurer, label, across - 4.dp.toPx(), deep, if (warning) warningText else labelText)
                }
                val inset = (deep - layout.size.height) / 2

                if (vertical) {
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
private fun DrawScope.fitText(
    measurer: TextMeasurer,
    text: String,
    maxWidth: Float,
    maxHeight: Float,
    textColor: Color,
): TextLayoutResult {
    fun measure(px: Float) = measurer.measure(
        text,
        TextStyle(
            color = textColor,
            fontSize = (px / (density * fontScale)).sp,
            lineHeight = (px * 1.15f / (density * fontScale)).sp,
            fontWeight = FontWeight.SemiBold,
        ),
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
