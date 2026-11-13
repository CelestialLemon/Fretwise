package com.celestiallemon.tuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiallemon.tuner.Note
import com.celestiallemon.tuner.TunerTracker
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Degrees either side of centre that ±50 cents covers. */
private const val Sweep = 58f

/**
 * A dial reading ±50 cents. The pointer floats on the outer ring so the note name can sit inside the bowl,
 * and the scale's ticks light up as the pointer passes. Green band = in tune.
 */
@Composable
fun Meter(
    active: Boolean,
    cents: Double,
    inTune: Boolean,
    note: Note?,
    hint: String,
    hintColor: Color,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    val shown = if (active) cents.coerceIn(-50.0, 50.0).toFloat() else 0f
    val needle by animateFloatAsState(
        targetValue = shown,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = if (active) 220f else Spring.StiffnessVeryLow),
        label = "needle",
    )
    val stateColor by animateColorAsState(
        targetValue = when {
            !active -> Palette.Faint
            inTune -> Palette.InTune
            abs(cents) <= 15 -> Palette.Brass
            else -> Palette.Off
        },
        animationSpec = tween(180),
        label = "state",
    )
    val glow by animateFloatAsState(if (active && inTune) 1f else 0f, tween(250), label = "glow")
    val labels = rememberTextMeasurer()

    Column(modifier.widthIn(max = 460.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.95f)) {
        val radius: Dp = min(maxWidth.value * 0.44f, maxHeight.value * 0.9f).dp
        Canvas(Modifier.fillMaxSize()) {
            val r = radius.toPx()
            val center = Offset(size.width / 2, size.height - 4.dp.toPx())
            drawDial(center, r, needle, stateColor, glow, active)
            val style = TextStyle(color = Palette.Muted, fontSize = 18.sp, fontFamily = Display)
            for ((text, angle) in listOf("♭" to -Sweep - 9f, "♯" to Sweep + 9f)) {
                val p = polar(center, r * 0.9f, angle)
                val layout = labels.measure(text, style)
                drawText(layout, topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            NoteName(note, if (active) Palette.Bone else Palette.Faint.copy(alpha = 0.7f))
        }
    }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active && !inTune) {
                    Text(
                        text = formatCents(cents),
                        color = stateColor,
                        fontFamily = Display,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                    )
                }
                Text(hint, color = hintColor, fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 17.sp)
            }
            Text(
                text = detail ?: " ",
                color = Palette.Faint,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
    }
}

@Composable
private fun NoteName(note: Note?, color: Color) {
    Box(contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                // Nothing heard yet: keep the space so the layout doesn't jump on the first pluck.
                text = note?.letter ?: " ",
                color = color,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 84.sp,
                lineHeight = 84.sp,
            )
            if (note != null) {
                Text(
                    text = note.octave.toString(),
                    color = color.copy(alpha = 0.55f),
                    fontFamily = Display,
                    fontWeight = FontWeight.Medium,
                    fontSize = 26.sp,
                    modifier = Modifier.offset(y = (-12).dp),
                )
            }
        }
    }
}

private fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * sin(rad).toFloat(), center.y - radius * cos(rad).toFloat())
}

private fun DrawScope.drawDial(center: Offset, r: Float, needle: Float, color: Color, glow: Float, active: Boolean) {
    val needleAngle = needle / 50f * Sweep

    // In-tune zone: a short green arc just outside the scale, glowing while the string is in tune.
    val bandAngle = TunerTracker.InTuneCents.toFloat() / 50f * Sweep
    val bandRadius = r + 9.dp.toPx()
    fun band(color: Color, width: Float, extra: Float) = drawArc(
        color = color,
        startAngle = -90f - bandAngle - extra,
        sweepAngle = (bandAngle + extra) * 2,
        useCenter = false,
        topLeft = Offset(center.x - bandRadius, center.y - bandRadius),
        size = androidx.compose.ui.geometry.Size(bandRadius * 2, bandRadius * 2),
        style = Stroke(width = width, cap = StrokeCap.Round),
    )
    if (glow > 0f) band(Palette.InTune.copy(alpha = 0.22f * glow), 16.dp.toPx(), 2f)
    band(Palette.InTune.copy(alpha = 0.55f + 0.45f * glow), 4.dp.toPx(), 0f)

    // Ticks every 2 cents, longer every 10, lit up near the pointer.
    for (c in -50..50 step 2) {
        val angle = c / 50f * Sweep
        val major = c % 10 == 0
        val length = (if (c == 0) 22 else if (major) 14 else 7).dp.toPx()
        val distance = abs(angle - needleAngle)
        val proximity = if (active) (1f - distance / 14f).coerceIn(0f, 1f) else 0f
        val base = if (major) Palette.Muted else Palette.Faint
        val tickColor = lerp(base, color, proximity)
        drawLine(
            color = tickColor,
            start = polar(center, r - length, angle),
            end = polar(center, r, angle),
            strokeWidth = (if (major) 2.2f else 1.4f).dp.toPx() + proximity * 1.2f.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    // Pointer: a tapered spoke on the ring with a bead at its tip, plus a soft glow.
    val inner = polar(center, r - 34.dp.toPx(), needleAngle)
    val outer = polar(center, r + 9.dp.toPx(), needleAngle)
    drawLine(color.copy(alpha = 0.22f), inner, outer, strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, inner, outer, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(color, radius = 7.dp.toPx(), center = outer)
    drawCircle(Palette.Background, radius = 3.dp.toPx(), center = outer)
}

private fun lerp(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/** "+7", "−12", "0": with a true minus sign. */
fun formatCents(cents: Double): String {
    val rounded = cents.roundToInt()
    return when {
        rounded > 0 -> "+$rounded¢"
        rounded < 0 -> "−${-rounded}¢"
        else -> "0¢"
    }
}
