package com.osakapatnam.konnichiwah.ui.directions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.toColorInt
import com.osakapatnam.konnichiwah.R
import com.osakapatnam.konnichiwah.data.remote.JourneyLegDto
import com.osakapatnam.konnichiwah.ui.components.PrimaryButton
import com.osakapatnam.konnichiwah.ui.theme.DisplayFont
import com.osakapatnam.konnichiwah.ui.theme.Palette

private object Jp {
    val Mute = Palette.Mute
    val Card = Palette.Card
    val Line = Palette.Line
    val Sun = Palette.Sun
    val Maroon = Palette.Maroon
    val Display = DisplayFont
    val Green = Palette.Green
    val Ink = Palette.Ink
}

// ---------- Model ----------
sealed interface GuideVisual {
    data class SignArrow(val letter: String, val number: String, val color: Color) : GuideVisual
    data class TrainFront(val destEn: String, val destJp: String, val color: Color) : GuideVisual
    data class Countdown(val stopsLeft: Int, val total: Int, val from: String, val to: String, val color: Color) : GuideVisual
    data class Arrived(val letter: String, val number: String, val color: Color, val destEn: String, val destJp: String) : GuideVisual
}

data class GuideStep(
    val title: String,
    val titleHi: String,
    val text: String,
    val textHi: String,
    val visual: GuideVisual,
)

/** Turn one train leg (from backend / routing API) into 4 simple visual steps. */
fun buildGuide(
    letter: String,
    startNo: String,
    endNo: String,
    color: Color,
    destEn: String,
    destJp: String,
    from: String,
    stops: Int,
    nextStop: String,
    exit: String = "1",
): List<GuideStep> = listOf(
    GuideStep(
        title = "Find this sign",
        titleHi = "यह साइन ढूँढें",
        text = "Look for it on walls and on the floor. Just follow it.",
        textHi = "इसे दीवारों और फ़र्श पर देखें। बस इसके पीछे चलते जाएँ।",
        visual = GuideVisual.SignArrow(letter, startNo, color),
    ),
    GuideStep(
        title = "Board this train",
        titleHi = "इस ट्रेन में चढ़ें",
        text = "Wait for a train that says $destEn on the front.",
        textHi = "ऐसी ट्रेन का इंतज़ार करें जिसके आगे $destEn लिखा हो।",
        visual = GuideVisual.TrainFront(destEn, destJp, color),
    ),
    GuideStep(
        title = "Ride ${maxOf(1, stops - 1)} more stops",
        titleHi = "${maxOf(1, stops - 1)} स्टॉप और",
        text = "Relax. Next stop is $nextStop. We buzz you at 1 stop left.",
        textHi = "आराम करें। अगला स्टॉप $nextStop है। एक स्टॉप बचने पर हम आपको बज़र देंगे।",
        visual = GuideVisual.Countdown(maxOf(1, stops - 1), maxOf(1, stops), from, destEn, color),
    ),
    GuideStep(
        title = "This is your stop",
        titleHi = "यह आपका स्टॉप है",
        text = "Get off here. Follow the arrows to Exit $exit.",
        textHi = "यहाँ उतरें। एक्ज़िट $exit तक तीरों का पीछा करें।",
        visual = GuideVisual.Arrived(letter, endNo, color, destEn, destJp),
    ),
)

val sampleGuide = buildGuide("G", "01", "19", Color(0xFFF39700), "Asakusa", "浅草", "Shibuya", 18, "Omotesando", exit = "1")

fun buildGuideFromLeg(leg: JourneyLegDto): List<GuideStep> {
    val color = leg.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: Color(0xFFF39700)

    val fromCode = leg.from.code?.trim().orEmpty()
    val toCode = leg.to.code?.trim().orEmpty()
    val lineCode = leg.lineCode?.trim().orEmpty()

    val letter = when {
        fromCode.any { it.isLetter() } -> fromCode.filter { it.isLetter() }
        lineCode.isNotEmpty() -> lineCode
        leg.line != null -> leg.line.take(2).uppercase()
        else -> "M"
    }

    val startNo = fromCode.filter { it.isDigit() }.ifEmpty { "01" }
    val endNo = toCode.filter { it.isDigit() }.ifEmpty {
        val totalStops = maxOf(1, leg.stops)
        String.format("%02d", totalStops)
    }

    val destEn = leg.towards?.takeIf { it.isNotBlank() } ?: leg.to.name
    val destJp = leg.to.nameJa ?: (leg.lineJa ?: "")
    val from = leg.from.name
    val stops = maxOf(1, leg.stops)
    val nextStop = leg.via.firstOrNull() ?: leg.to.name
    val exit = leg.exit?.replace(Regex("[^0-9A-Za-z]"), "")?.ifEmpty { "1" } ?: "1"

    return buildGuide(
        letter = letter,
        startNo = startNo,
        endNo = endNo,
        color = color,
        destEn = destEn,
        destJp = destJp,
        from = from,
        stops = stops,
        nextStop = nextStop,
        exit = exit,
    )
}

// ---------- Full-Screen Modal Dialog ----------
@Composable
fun RouteGuideDialog(
    steps: List<GuideStep>,
    destinationName: String = "",
    destinationNameJa: String = "",
    onDismiss: () -> Unit,
) {
    var showStaffCard by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        RouteGuideScreen(
            steps = steps,
            onLost = { showStaffCard = true },
            onDone = onDismiss,
        )

        if (showStaffCard) {
            val destTitle = listOfNotNull(destinationName.takeIf { it.isNotBlank() }, destinationNameJa.takeIf { it.isNotBlank() }).joinToString(" ")
            AlertDialog(
                onDismissRequest = { showStaffCard = false },
                confirmButton = {
                    TextButton(onClick = { showStaffCard = false }) {
                        Text(stringResource(R.string.card_close), color = Palette.Sun, fontWeight = FontWeight.Bold)
                    }
                },
                title = {
                    Text(
                        stringResource(R.string.dir_staff_card_title),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        color = Palette.Maroon,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Palette.Cream,
                            border = BorderStroke(1.dp, Palette.Line),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "すみません、道に迷いました。",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Palette.Maroon,
                                )
                                Text(
                                    "この駅に行きたいです：",
                                    fontSize = 15.sp,
                                    color = Palette.Ink,
                                )
                                Text(
                                    destTitle.ifEmpty { "駅" },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Palette.Sun,
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.dir_staff_card_hint),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = Palette.Mute,
                        )
                    }
                },
                containerColor = Palette.Card,
            )
        }
    }
}

// ---------- Screen ----------
@Composable
fun RouteGuideScreen(
    steps: List<GuideStep>,
    onLost: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var i by rememberSaveable { mutableIntStateOf(0) }
    var hindi by rememberSaveable { mutableStateOf(false) }
    val s = steps.getOrElse(i) { steps.first() }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Palette.Cream,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (hindi) "चरण ${i + 1} / ${steps.size}" else "Step ${i + 1} of ${steps.size}",
                    fontSize = 13.sp,
                    color = Jp.Mute,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Jp.Card)
                        .border(1.dp, Jp.Line, CircleShape)
                        .clickable { hindi = !hindi }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("EN", fontSize = 12.sp, fontWeight = if (!hindi) FontWeight.Bold else FontWeight.Normal, color = if (!hindi) Jp.Maroon else Jp.Mute)
                    Text(" | ", fontSize = 12.sp, color = Jp.Line)
                    Text("हिन्दी", fontSize = 12.sp, fontWeight = if (hindi) FontWeight.Bold else FontWeight.Normal, color = if (hindi) Jp.Maroon else Jp.Mute)
                }
            }

            // Step capsules
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                steps.indices.forEach { k ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (k <= i) Jp.Sun else Jp.Line)
                    )
                }
            }

            Text(
                if (hindi) s.titleHi else s.title,
                fontFamily = Jp.Display,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Jp.Maroon,
                lineHeight = 34.sp,
            )

            Surface(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                color = Jp.Card,
                border = BorderStroke(1.dp, Jp.Line),
            ) {
                GuideVisualView(s.visual, hindi)
            }

            Text(
                if (hindi) s.textHi else s.text,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                color = Jp.Ink,
            )

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = onLost,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Jp.Sun),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Jp.Sun),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LifesaverIcon(color = Jp.Sun)
                    Text(
                        if (hindi) "मैं खो गया हूँ। मदद करें" else "I'm lost. Help me",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { if (i > 0) i-- else onDone() },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Jp.Line),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Jp.Ink),
                ) {
                    Text(
                        if (i == 0) (if (hindi) "बंद करें" else "Close") else (if (hindi) "पीछे" else "Back"),
                        color = Jp.Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                PrimaryButton(
                    text = if (i == steps.lastIndex) (if (hindi) "पूरा हुआ" else "Done") else (if (hindi) "अगला चरण" else "Next step"),
                    onClick = {
                        if (i == steps.lastIndex) onDone() else i++
                    },
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}

// ---------- Visuals ----------
@Composable
fun GuideVisualView(v: GuideVisual, hindi: Boolean) = when (v) {
    is GuideVisual.SignArrow -> Box(Modifier.fillMaxSize().background(Color(0xFFEFE7D6))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFFD8D0C0), Offset(0f, size.height * .75f), Size(size.width, size.height * .25f))
            val lineY = size.height * .87f
            drawLine(v.color, Offset(size.width * .07f, lineY), Offset(size.width * .93f, lineY), 28f, StrokeCap.Round)

            // Double chevrons on the track line like in PDF screenshot page 1
            val cx1 = size.width * .72f
            val cx2 = size.width * .80f
            val arm = 13f
            val p1 = Path().apply {
                moveTo(cx1 - arm * 0.7f, lineY - arm)
                lineTo(cx1 + arm * 0.5f, lineY)
                lineTo(cx1 - arm * 0.7f, lineY + arm)
            }
            drawPath(p1, Color.White, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val p2 = Path().apply {
                moveTo(cx2 - arm * 0.7f, lineY - arm)
                lineTo(cx2 + arm * 0.5f, lineY)
                lineTo(cx2 - arm * 0.7f, lineY + arm)
            }
            drawPath(p2, Color.White, style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Row(Modifier.align(Alignment.Center).padding(bottom = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            SignBadge(v.letter, v.number, v.color, 84.dp)
            Spacer(Modifier.width(18.dp))
            BigArrow()
        }
    }
    is GuideVisual.TrainFront -> Box(Modifier.fillMaxSize().background(Color(0xFFEFE7D6)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(176.dp, 108.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(Color(0xFFF4ECDC), size = size, cornerRadius = CornerRadius(40f))
                drawRect(v.color, Offset(0f, size.height * .58f), Size(size.width, size.height * .12f))
                val win = Color(0xFF9FC5CF)
                drawRoundRect(win, Offset(size.width * .08f, size.height * .72f), Size(size.width * .34f, size.height * .20f), CornerRadius(14f))
                drawRoundRect(win, Offset(size.width * .58f, size.height * .72f), Size(size.width * .34f, size.height * .20f), CornerRadius(14f))

                // Front headlights
                drawCircle(Color.White, radius = 7f, center = Offset(size.width * .25f, size.height * .94f))
                drawCircle(Color(0xFFC0B8A8), radius = 7f, center = Offset(size.width * .25f, size.height * .94f), style = Stroke(2f))
                drawCircle(Color.White, radius = 7f, center = Offset(size.width * .75f, size.height * .94f))
                drawCircle(Color(0xFFC0B8A8), radius = 7f, center = Offset(size.width * .75f, size.height * .94f), style = Stroke(2f))
            }
            val destText = listOfNotNull(v.destJp.takeIf { it.isNotBlank() }, v.destEn.takeIf { it.isNotBlank() }).joinToString(" ")
            Text(
                destText,
                color = Color(0xFFFFB84D),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF1D1D1D))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    is GuideVisual.Countdown -> Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFEFE7D6))
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${v.stopsLeft}",
            fontFamily = Jp.Display,
            fontWeight = FontWeight.Bold,
            fontSize = 64.sp,
            color = v.color,
            lineHeight = 64.sp,
        )
        Text(if (hindi) "स्टॉप बाकी" else "stops to go", fontSize = 14.sp, color = Jp.Mute, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        val progress = if (v.total > 0) 1f - (v.stopsLeft.toFloat() / v.total.toFloat()).coerceIn(0f, 1f) else 1f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape),
            color = v.color,
            trackColor = Color(0xFFF3E3C3),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(v.from, fontSize = 12.sp, color = Jp.Mute, maxLines = 1)
            Text(v.to, fontSize = 12.sp, color = Jp.Mute, maxLines = 1)
        }
    }
    is GuideVisual.Arrived -> Row(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFEFE7D6))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SignBadge(v.letter, v.number, v.color, 84.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(v.destEn, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Jp.Ink)
            if (v.destJp.isNotBlank()) {
                Text(v.destJp, fontSize = 16.sp, color = Jp.Mute)
            }
        }
        Canvas(Modifier.size(44.dp)) {
            drawCircle(Jp.Green)
            val p = Path().apply {
                moveTo(size.width * .27f, size.height * .52f)
                lineTo(size.width * .43f, size.height * .68f)
                lineTo(size.width * .74f, size.height * .34f)
            }
            drawPath(p, Color.White, style = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Replica of a Tokyo Metro style line sign: coloured ring, letter, station number. */
@Composable
fun SignBadge(letter: String, number: String, color: Color, size: Dp) = Box(Modifier.size(size), Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(color)
        drawCircle(Color.White, radius = this.size.minDimension * .40f)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(letter, color = color, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
        Text(number, color = Jp.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun BigArrow() = Canvas(Modifier.size(80.dp, 50.dp)) {
    val w = size.width
    val h = size.height
    val sw = 9.dp.toPx()
    drawLine(Jp.Maroon, Offset(0f, h / 2), Offset(w - 4f, h / 2), sw, StrokeCap.Round)
    drawLine(Jp.Maroon, Offset(w * .62f, h * .1f), Offset(w - 4f, h / 2), sw, StrokeCap.Round)
    drawLine(Jp.Maroon, Offset(w * .62f, h * .9f), Offset(w - 4f, h / 2), sw, StrokeCap.Round)
}

@Composable
fun LifesaverIcon(modifier: Modifier = Modifier, color: Color = Jp.Sun) = Canvas(modifier.size(20.dp)) {
    val r = size.minDimension / 2
    val c = center
    drawCircle(color, radius = r, style = Stroke(width = 3.dp.toPx()))
    drawCircle(color, radius = r * 0.45f, style = Stroke(width = 2.dp.toPx()))
    val w = 2.5.dp.toPx()
    drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y - r * 0.45f), strokeWidth = w)
    drawLine(color, Offset(c.x, c.y + r * 0.45f), Offset(c.x, c.y + r), strokeWidth = w)
    drawLine(color, Offset(c.x - r, c.y), Offset(c.x - r * 0.45f, c.y), strokeWidth = w)
    drawLine(color, Offset(c.x + r * 0.45f, c.y), Offset(c.x + r, c.y), strokeWidth = w)
}
