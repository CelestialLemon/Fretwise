package com.celestiallemon.tuner.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.celestiallemon.tuner.playalong.ChordShape
import com.celestiallemon.tuner.playalong.StrumDir
import com.celestiallemon.tuner.playalong.StrumPattern
import kotlin.math.min

/**
 * A crisp guitar chord fretboard diagram drawn in Fretwise's warm bone/brass palette.
 */
@Composable
fun ChordDiagram(
    shape: ChordShape,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    accent: Color = Palette.Brass,
) {
    val dotColor by animateColorAsState(
        if (highlighted) accent else Palette.Bone,
        Motion.effects(160),
        label = "chordDot",
    )
    Canvas(
        modifier.semantics { contentDescription = "${shape.name} chord diagram" },
    ) {
        val nFrets = 4
        val w = size.width
        val h = size.height
        val markH = h * 0.17f
        val leftMargin = if (shape.baseFret > 1) w * 0.22f else w * 0.13f
        val rightMargin = w * 0.11f
        val top = markH
        val bottom = h - 4.dp.toPx()
        val sx = (w - leftMargin - rightMargin) / 5f
        val fy = (bottom - top) / nFrets
        val r = min(sx, fy) * 0.44f
        val lineW = (w * 0.015f).coerceAtLeast(1.4.dp.toPx())
        val nutW = if (shape.baseFret == 1) lineW * 2.8f else lineW * 1.2f

        fun xOf(stringIdx: Int) = leftMargin + stringIdx * sx

        // Subtle fretboard background inside the grid.
        drawRoundRect(
            color = Palette.Fretboard.copy(alpha = 0.7f),
            topLeft = Offset(xOf(0), top),
            size = Size(xOf(5) - xOf(0), bottom - top),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )

        // Frets.
        for (f in 1..nFrets) {
            val y = top + f * fy
            drawLine(
                color = Palette.Bone.copy(alpha = 0.35f),
                start = Offset(xOf(0), y),
                end = Offset(xOf(5), y),
                strokeWidth = lineW * 0.85f,
                cap = StrokeCap.Round,
            )
        }

        // 6 strings (thicker on the bass side, matching the headstock).
        for (i in 0..5) {
            val sw = lineW * (1.35f - i * 0.08f)
            val strColor = if (i < 4) Palette.WoundString.copy(alpha = 0.75f) else Palette.PlainString.copy(alpha = 0.75f)
            drawLine(
                color = strColor,
                start = Offset(xOf(i), top),
                end = Offset(xOf(i), bottom),
                strokeWidth = sw,
            )
        }

        // Nut / top fret bar.
        drawLine(
            color = if (shape.baseFret == 1) Palette.Nut else Palette.Bone.copy(alpha = 0.7f),
            start = Offset(xOf(0) - lineW * 0.7f, top),
            end = Offset(xOf(5) + lineW * 0.7f, top),
            strokeWidth = nutW,
            cap = StrokeCap.Round,
        )

        // Base fret indicator (e.g. "4fr").
        if (shape.baseFret > 1) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Palette.Muted.toArgb()
                textSize = (fy * 0.44f).coerceIn(10.dp.toPx(), 13.dp.toPx())
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val fm = paint.fontMetrics
            val textY = top + fy * 0.5f - (fm.ascent + fm.descent) / 2f
            drawContext.canvas.nativeCanvas.drawText("${shape.baseFret}fr", xOf(0) - 6.dp.toPx(), textY, paint)
        }

        // Open (O) and muted (X) string markers above the nut.
        val my = markH * 0.48f
        val mr = min(r * 0.58f, markH * 0.28f)
        shape.frets.forEachIndexed { i, fret ->
            val x = xOf(i)
            when {
                fret < 0 -> {
                    val a = mr * 0.82f
                    drawLine(Palette.Muted, Offset(x - a, my - a), Offset(x + a, my + a), lineW, StrokeCap.Round)
                    drawLine(Palette.Muted, Offset(x + a, my - a), Offset(x - a, my + a), lineW, StrokeCap.Round)
                }
                fret == 0 -> {
                    drawCircle(
                        color = Palette.Bone.copy(alpha = 0.85f),
                        radius = mr,
                        center = Offset(x, my),
                        style = Stroke(width = lineW),
                    )
                }
            }
        }

        // Optional barre pill.
        val barre = shape.barre
        if (barre != null) {
            val relF = barre.fret - shape.baseFret + 1
            if (relF in 1..nFrets) {
                val y = top + (relF - 0.5f) * fy
                val xStart = xOf(barre.fromString) - r
                val xEnd = xOf(barre.toString) + r
                drawRoundRect(
                    color = dotColor.copy(alpha = 0.94f),
                    topLeft = Offset(xStart, y - r * 0.78f),
                    size = Size(xEnd - xStart, r * 1.56f),
                    cornerRadius = CornerRadius(r * 0.78f),
                )
            }
        }

        // Finger dots + numbers.
        val fingerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Palette.Background.toArgb()
            textSize = r * 1.22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val fm = fingerPaint.fontMetrics
        shape.frets.forEachIndexed { i, fret ->
            if (fret > 0) {
                val relF = fret - shape.baseFret + 1
                if (relF in 1..nFrets) {
                    val x = xOf(i)
                    val y = top + (relF - 0.5f) * fy
                    val insideBarre = barre != null && fret == barre.fret && i in barre.fromString..barre.toString
                    if (!insideBarre) {
                        drawCircle(color = dotColor, radius = r, center = Offset(x, y))
                    }
                    val finger = shape.fingers.getOrNull(i).orEmpty()
                    if (finger.isNotEmpty()) {
                        val baseline = y - (fm.ascent + fm.descent) / 2f
                        drawContext.canvas.nativeCanvas.drawText(finger, x, baseline, fingerPaint)
                    }
                }
            }
        }
    }
}

/**
 * Beat-synced strumming pattern row (down/up arrows and rest dots), matching the play-along cue style.
 */
@Composable
fun StrummingRow(
    pattern: StrumPattern,
    activeSlot: Int?,
    modifier: Modifier = Modifier,
    arrowHeight: Dp = 28.dp,
) {
    val compact = pattern.slots.size >= 7
    val gap = if (compact) 8.dp else 14.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pattern.slots.forEachIndexed { idx, slot ->
            val isOn = activeSlot == idx
            val isPast = activeSlot != null && idx < activeSlot
            val tint by animateColorAsState(
                when {
                    isOn -> Palette.Brass
                    isPast -> Palette.Bone.copy(alpha = 0.35f)
                    else -> Palette.Bone.copy(alpha = 0.82f)
                },
                Motion.effects(90),
                label = "strumTint",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(if (compact) 22.dp else 28.dp),
            ) {
                Box(Modifier.height(arrowHeight), contentAlignment = Alignment.Center) {
                    when (slot.dir) {
                        StrumDir.Rest -> Box(
                            Modifier
                                .size(if (isOn) 9.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (isOn) Palette.Brass else Palette.Muted.copy(alpha = if (isPast) 0.3f else 0.65f)),
                        )
                        StrumDir.Down, StrumDir.Up -> StrumArrow(
                            down = slot.dir == StrumDir.Down,
                            color = tint,
                            modifier = Modifier.size(width = if (compact) 16.dp else 20.dp, height = arrowHeight),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    slot.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                    color = if (isOn) Palette.Brass else Palette.Muted.copy(alpha = if (isPast) 0.45f else 0.85f),
                )
            }
        }
    }
}

@Composable
private fun StrumArrow(down: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sw = 2.4.dp.toPx()
        val cx = size.width / 2f
        val pad = sw
        val head = size.width * 0.36f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(cx, pad), Offset(cx, size.height - pad), strokeWidth = sw, cap = StrokeCap.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            if (down) {
                moveTo(cx - head, size.height - pad - head)
                lineTo(cx, size.height - pad)
                lineTo(cx + head, size.height - pad - head)
            } else {
                moveTo(cx - head, pad + head)
                lineTo(cx, pad)
                lineTo(cx + head, pad + head)
            }
        }
        drawPath(path, color = color, style = stroke)
    }
}
