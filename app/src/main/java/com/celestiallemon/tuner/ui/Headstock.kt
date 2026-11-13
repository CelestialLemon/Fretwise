package com.celestiallemon.tuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiallemon.tuner.Note
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Where everything on the headstock goes, in dp, for a given box size and string count. */
private class HeadstockGeometry(width: Float, height: Float, strings: Int, leftPegs: Int) {
    val cx = width / 2
    val top = 6f
    val nutY = height * 0.82f
    val bottom = height
    val headW = min(width * 0.5f, 240f)
    val neckW = min(width * (0.17f + 0.02f * strings), headW * 0.78f)
    val left = min(leftPegs, strings)
    val right = strings - left
    private val rowTop = top + height * 0.13f
    private val rowBottom = nutY - height * 0.22f
    private val rows = max(left, right)

    /** Peg diameter: 54dp, smaller when many strings share a side. */
    val peg = if (rows <= 1) 54f else min(54f, (rowBottom - rowTop) / (rows - 1) * 0.86f)

    /** Peg centres in nut order. Outer strings get the posts nearest the nut, as on a real 3+3 headstock. */
    val pegs: List<Offset>
    val posts: List<Offset>
    val nut: List<Float>

    init {
        fun rowY(i: Int, count: Int) =
            if (count <= 1) (rowTop + rowBottom) / 2 else rowTop + i * (rowBottom - rowTop) / (count - 1)
        val pegOffset = headW / 2 + peg * 0.62f
        val postOffset = headW * 0.29f
        val pegList = MutableList(strings) { Offset.Zero }
        val postList = MutableList(strings) { Offset.Zero }
        for (s in 0 until left) {
            val y = rowY(left - 1 - s, left)
            pegList[s] = Offset(cx - pegOffset, y)
            postList[s] = Offset(cx - postOffset, y)
        }
        for (j in 0 until right) {
            val y = rowY(j, right)
            pegList[left + j] = Offset(cx + pegOffset, y)
            postList[left + j] = Offset(cx + postOffset, y)
        }
        pegs = pegList
        posts = postList
        val spread = neckW * 0.78f
        nut = List(strings) { if (strings == 1) cx else cx - spread / 2 + it * spread / (strings - 1) }
    }

    fun body(scale: Float): Path = Path().apply {
        val l = (cx - headW / 2) * scale
        val r = (cx + headW / 2) * scale
        val nl = (cx - neckW / 2) * scale
        val nr = (cx + neckW / 2) * scale
        val c = cx * scale
        val t = top * scale
        val n = nutY * scale
        val h = (nutY - top) * scale
        val w = headW * scale
        moveTo(nl, n + 1)
        cubicTo(nl, n - h * 0.1f, l, n - h * 0.12f, l, n - h * 0.26f)
        lineTo(l, t + h * 0.12f)
        cubicTo(l, t + h * 0.02f, c - w * 0.24f, t, c, t + h * 0.055f)
        cubicTo(c + w * 0.24f, t, r, t + h * 0.02f, r, t + h * 0.12f)
        lineTo(r, n - h * 0.26f)
        cubicTo(r, n - h * 0.12f, nr, n - h * 0.1f, nr, n + 1)
        close()
    }
}

/** True in screenshot tests, which need the UI to settle. */
val LocalStillFrames = staticCompositionLocalOf { false }

@Composable
fun Headstock(
    strings: List<Note>,
    leftPegs: Int,
    a4: Double,
    activeString: Int?,
    sounding: Boolean,
    inTune: Boolean,
    level: Float,
    tuned: Set<Int>,
    onPegClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The active string shimmers only while it's sounding, so the screen is still (and cheap) between plucks.
    var phase by remember { mutableFloatStateOf(0.25f) }
    if (sounding && !LocalStillFrames.current) {
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { phase = ((it / 140_000_000.0) % 1.0).toFloat() }
        }
    }
    val amplitude by animateFloatAsState(if (sounding) level else 0f, tween(120), label = "amplitude")
    val activeColor by animateColorAsState(if (inTune) Palette.InTune else Palette.Brass, tween(180), label = "active")

    // Thicker, bronze-wound strings for the low notes.
    val frequencies = strings.map { it.frequency(a4) }
    val lowest = frequencies.minOrNull() ?: 1.0
    val highest = frequencies.maxOrNull() ?: 1.0
    fun heaviness(i: Int) =
        if (highest <= lowest) 0.5f else (1 - ln(frequencies[i] / lowest) / ln(highest / lowest)).toFloat()

    BoxWithConstraints(modifier) {
        val geo = remember(maxWidth, maxHeight, strings.size, leftPegs) {
            HeadstockGeometry(maxWidth.value, maxHeight.value, strings.size, leftPegs)
        }
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            drawFretboard(geo, d)
            drawBody(geo, d)
            for (i in strings.indices) {
                // Tuner shaft running from the peg through the side of the headstock to the post.
                drawLine(Palette.Nickel.copy(alpha = 0.55f), geo.pegs[i] * d, geo.posts[i] * d, 5.dp.toPx())
            }
            for (i in strings.indices) {
                val heavy = heaviness(i)
                val wound = frequencies[i] < 180.0
                val base = if (wound) Palette.WoundString else Palette.PlainString
                val active = i == activeString
                drawString(
                    post = geo.posts[i] * d,
                    nutX = geo.nut[i] * d,
                    nutY = geo.nutY * d,
                    bottom = geo.bottom * d,
                    width = (1.2f + 2.2f * heavy).dp.toPx(),
                    color = if (active) lerpColor(base, activeColor, 0.55f) else base.copy(alpha = 0.8f),
                    glow = if (active) activeColor else null,
                    vibration = if (active) amplitude * 3.5f.dp.toPx() else 0f,
                    phase = phase,
                )
            }
            for (i in strings.indices) drawPost(geo.posts[i] * d, d)
            drawNut(geo, d)
        }
        for (i in strings.indices) {
            Peg(
                note = strings[i],
                active = i == activeString,
                activeColor = activeColor,
                tuned = i in tuned,
                onClick = { onPegClick(i) },
                modifier = Modifier.offset((geo.pegs[i].x - geo.peg / 2).dp, (geo.pegs[i].y - geo.peg / 2).dp),
                size = geo.peg.dp,
            )
        }
    }
}

@Composable
private fun Peg(
    note: Note,
    active: Boolean,
    activeColor: Color,
    tuned: Boolean,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val fill by animateColorAsState(if (active) activeColor else Palette.SurfaceHigh, tween(160), label = "fill")
    val text = when {
        active -> Palette.Background
        tuned -> Palette.InTune
        else -> Palette.Bone
    }
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(if (active) 14.dp else 4.dp, CircleShape, ambientColor = fill, spotColor = fill)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerpColor(fill, Color.White, 0.12f), fill)))
                .border(1.5.dp, if (tuned && !active) Palette.InTune else Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = "$note string" + if (tuned) ", tuned" else ""
                    selected = active
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(note.letter, color = text, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        }
        if (tuned) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Palette.InTune)
                    .border(2.dp, Palette.Background, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Palette.Background, modifier = Modifier.size(13.dp))
            }
        }
    }
}

private operator fun Offset.times(scale: Float) = Offset(x * scale, y * scale)

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

private fun DrawScope.drawFretboard(geo: HeadstockGeometry, d: Float) {
    val left = (geo.cx - geo.neckW / 2) * d
    val width = geo.neckW * d
    drawRect(Palette.Fretboard, Offset(left, geo.nutY * d), Size(width, (geo.bottom - geo.nutY) * d))
    // First fret.
    val fretY = (geo.nutY + (geo.bottom - geo.nutY) * 0.62f) * d
    drawLine(Palette.Nickel.copy(alpha = 0.6f), Offset(left, fretY), Offset(left + width, fretY), 2.5f.dp.toPx())
}

private fun DrawScope.drawBody(geo: HeadstockGeometry, d: Float) {
    val path = geo.body(d)
    drawPath(path, Brush.verticalGradient(listOf(Palette.WoodLight, Palette.WoodDark), 0f, geo.nutY * d))
    clipPath(path) {
        // A few long grain lines, faint.
        val grain = Color.Black.copy(alpha = 0.13f)
        for (k in 0 until 9) {
            val x0 = (geo.cx - geo.headW / 2 + geo.headW * (k + 0.5f) / 9) * d
            val p = Path().apply {
                moveTo(x0, 0f)
                cubicTo(x0 + 10 * d, geo.nutY * d * 0.35f, x0 - 12 * d, geo.nutY * d * 0.7f, x0 + 4 * d, geo.nutY * d)
            }
            drawPath(p, grain, style = Stroke(width = (0.8f + (k % 3) * 0.5f) * d))
        }
    }
    drawPath(path, Color.White.copy(alpha = 0.07f), style = Stroke(width = 1.5f * d))
}

private fun DrawScope.drawNut(geo: HeadstockGeometry, d: Float) {
    val left = (geo.cx - geo.neckW / 2 - 2) * d
    drawRoundRect(
        Palette.Nut,
        topLeft = Offset(left, (geo.nutY - 5) * d),
        size = Size((geo.neckW + 4) * d, 8 * d),
        cornerRadius = CornerRadius(2 * d),
    )
}

private fun DrawScope.drawPost(center: Offset, d: Float) {
    drawCircle(Color.Black.copy(alpha = 0.35f), 9.5f * d, center + Offset(0f, 1.5f * d))
    drawCircle(Brush.radialGradient(listOf(Color.White, Palette.Nickel, Color(0xFF8E9299)), center, 9 * d), 8.5f * d, center)
    drawCircle(Color(0xFF55585E), 3f * d, center)
}

private fun DrawScope.drawString(
    post: Offset,
    nutX: Float,
    nutY: Float,
    bottom: Float,
    width: Float,
    color: Color,
    glow: Color?,
    vibration: Float,
    phase: Float,
) {
    val nut = Offset(nutX, nutY)
    // Below the nut the string vibrates (the bridge is off-screen, so the swing grows towards the bottom).
    val vibrating = Path().apply {
        moveTo(nutX, nutY)
        val steps = 28
        val swing = vibration * sin(2 * PI * phase).toFloat()
        for (s in 1..steps) {
            val u = s.toFloat() / steps
            val x = nutX + swing * sin(PI * u / 2).toFloat()
            lineTo(x, nutY + (bottom - nutY) * u)
        }
    }
    if (glow != null) {
        drawLine(glow.copy(alpha = 0.28f), post, nut, width + 7.dp.toPx(), StrokeCap.Round)
        drawPath(vibrating, glow.copy(alpha = 0.28f), style = Stroke(width + 7.dp.toPx() + max(0f, vibration), cap = StrokeCap.Round))
    }
    drawLine(color, post, nut, width, StrokeCap.Round)
    drawPath(vibrating, color, style = Stroke(width, cap = StrokeCap.Round))
}
