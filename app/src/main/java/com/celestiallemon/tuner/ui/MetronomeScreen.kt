package com.celestiallemon.tuner.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiallemon.tuner.metronome.Accent
import com.celestiallemon.tuner.metronome.MaxBpm
import com.celestiallemon.tuner.metronome.MetronomeEngine
import com.celestiallemon.tuner.metronome.MetronomeUiState
import com.celestiallemon.tuner.metronome.MinBpm
import com.celestiallemon.tuner.metronome.TimeSignature
import com.celestiallemon.tuner.metronome.tempoName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The dial's scale starts at 135° (bottom left) and sweeps 270° clockwise. */
private const val DialStart = 135f
private const val DialSweep = 270f

/** Degrees of rotation per BPM step when dragging the dial. */
private const val DegreesPerBpm = 3.2f

@Composable
fun MetronomeScreen(
    state: MetronomeUiState,
    engine: MetronomeEngine?,
    onToggle: () -> Unit,
    onBpm: (Int) -> Unit,
    onNudge: (Int) -> Unit,
    onTap: (Long) -> Unit,
    onSignature: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Follow the audio playhead every frame so the lights land on the clicks, not on when they were scheduled.
    var beat by remember { mutableStateOf<MetronomeEngine.Beat?>(null) }
    LaunchedEffect(state.playing, engine) {
        beat = null
        if (!state.playing || engine == null) return@LaunchedEffect
        while (true) withFrameNanos { beat = engine.playheadBeat() }
    }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(beat?.serial) {
        val b = beat ?: return@LaunchedEffect pulse.snapTo(0f)
        pulse.snapTo(if (b.accent == Accent.Strong) 1f else 0.6f)
        pulse.animateTo(0f, tween((60_000 / state.bpm * 0.85).toInt(), easing = LinearOutSlowInEasing))
    }

    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        ScreenHeader("Metronome")
        AnimatedVisibility(
            visible = state.songTitle != null,
            enter = fadeIn(Motion.effects()) + expandVertically(Motion.spatial()),
            exit = fadeOut(Motion.effects()) + shrinkVertically(Motion.spatial()),
        ) {
            SongTempoChip(state.songTitle.orEmpty())
        }

        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 32.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            TempoDial(
                bpm = state.bpm,
                pulse = { pulse.value },
                accentBeat = beat?.accent == Accent.Strong,
                onBpm = onBpm,
                modifier = Modifier.widthIn(max = 340.dp).aspectRatio(1f, matchHeightConstraintsFirst = true),
            )
        }

        BeatLights(state.signature, active = beat?.index, playing = state.playing)
        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RepeatButton(Icons.Rounded.Remove, "Slower", enabled = state.bpm > MinBpm) { onNudge(-1) }
            TapButton(onTap, Modifier.weight(1f))
            RepeatButton(Icons.Rounded.Add, "Faster", enabled = state.bpm < MaxBpm) { onNudge(1) }
        }
        Spacer(Modifier.height(16.dp))
        SegmentedPicker(
            options = TimeSignature.entries,
            selected = state.signature,
            label = { it.label },
            onSelect = onSignature,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(20.dp))
        PlayButton(state.playing, onToggle)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SongTempoChip(title: String) {
    Row(
        Modifier
            .padding(bottom = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Tempo for $title",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }
}

/**
 * A rotary tempo control. Drag around the ring to change the tempo, with a haptic tick for each BPM, like a
 * detented knob. The ring glows on every beat.
 */
@Composable
private fun TempoDial(
    bpm: Int,
    pulse: () -> Float,
    accentBeat: Boolean,
    onBpm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val currentBpm by rememberUpdatedState(bpm)
    val fraction by animateFloatAsState(
        (bpm - MinBpm).toFloat() / (MaxBpm - MinBpm),
        Motion.spatialFast(),
        label = "dialFraction",
    )
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(if (dragging) 1.35f else 1f, Motion.pop(), label = "thumb")
    val colors = MaterialTheme.colorScheme
    val glowColor by animateColorAsState(if (accentBeat) Palette.Brass else Palette.Bone, Motion.effects(80), label = "glow")

    BoxWithConstraints(
        modifier
            .semantics {
                contentDescription = "Tempo dial"
                stateDescription = "$bpm beats per minute"
            }
            .pointerInput(Unit) {
                var accumulated = 0f
                var lastAngle = 0f
                detectDragGestures(
                    onDragStart = { start ->
                        dragging = true
                        accumulated = 0f
                        lastAngle = angleOf(start, size.width / 2f, size.height / 2f)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    val angle = angleOf(change.position, size.width / 2f, size.height / 2f)
                    var delta = angle - lastAngle
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    lastAngle = angle
                    accumulated += delta
                    val steps = (accumulated / DegreesPerBpm).toInt()
                    if (steps != 0) {
                        accumulated -= steps * DegreesPerBpm
                        val next = (currentBpm + steps).coerceIn(MinBpm, MaxBpm)
                        if (next != currentBpm) {
                            onBpm(next)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val side = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val stroke = side * 0.045f
            val radius = side / 2f - stroke * 1.4f
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)

            // Beat glow: a soft disc that blooms from the centre and fades.
            val p = pulse()
            if (p > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(glowColor.copy(alpha = 0.22f * p), glowColor.copy(alpha = 0f)),
                        center = center,
                        radius = radius * (0.75f + 0.3f * p),
                    ),
                    radius = radius * (0.75f + 0.3f * p),
                )
            }
            // Fine scale ticks around the ring, every 10 BPM.
            for (tick in MinBpm..MaxBpm step 10) {
                val t = (tick - MinBpm).toFloat() / (MaxBpm - MinBpm)
                val a = Math.toRadians((DialStart + DialSweep * t).toDouble())
                val inner = radius - stroke * 1.6f
                val outer = inner - (if (tick % 50 == 0) stroke * 0.9f else stroke * 0.45f)
                drawLine(
                    if (t <= fraction) colors.primary.copy(alpha = 0.7f) else colors.outline,
                    Offset(center.x + inner * cos(a).toFloat(), center.y + inner * sin(a).toFloat()),
                    Offset(center.x + outer * cos(a).toFloat(), center.y + outer * sin(a).toFloat()),
                    strokeWidth = stroke * 0.18f,
                    cap = StrokeCap.Round,
                )
            }
            drawArc(colors.surfaceContainerHigh, DialStart, DialSweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(Palette.Ember, Palette.Brass, Palette.Brass, Palette.Ember), center),
                DialStart,
                DialSweep * fraction,
                false,
                topLeft,
                arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            // Ring-wide flash on each beat.
            if (p > 0f) {
                drawArc(
                    glowColor.copy(alpha = 0.35f * p),
                    0f, 360f, false,
                    Offset(center.x - radius - stroke, center.y - radius - stroke),
                    Size((radius + stroke) * 2, (radius + stroke) * 2),
                    style = Stroke(stroke * 0.25f),
                )
            }
            val thumbAngle = Math.toRadians((DialStart + DialSweep * fraction).toDouble())
            val thumb = Offset(center.x + radius * cos(thumbAngle).toFloat(), center.y + radius * sin(thumbAngle).toFloat())
            drawCircle(colors.primary.copy(alpha = 0.25f), radius = stroke * 1.3f * thumbScale, center = thumb)
            drawCircle(colors.onSurface, radius = stroke * 0.78f * thumbScale, center = thumb)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Overline("Tempo", color = colors.onSurfaceVariant)
            RollingNumber(bpm)
            AnimatedContent(
                tempoName(bpm),
                transitionSpec = { fadeIn(Motion.effects()) togetherWith fadeOut(Motion.effects(120)) },
                label = "tempoName",
            ) { name ->
                Text(name, style = MaterialTheme.typography.titleMedium, color = colors.primary, fontFamily = Display)
            }
        }
    }
}

private fun angleOf(point: Offset, cx: Float, cy: Float): Float =
    (atan2(point.y - cy, point.x - cx) * 180f / PI.toFloat())

/** The BPM, with digits that roll up when the tempo rises and down when it falls. */
@Composable
private fun RollingNumber(value: Int) {
    Row {
        val digits = value.toString()
        for ((i, ch) in digits.withIndex()) {
            // Key by place value so "99 → 100" doesn't roll every digit.
            val place = digits.length - i
            androidx.compose.runtime.key(place) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(Motion.slide) { if (up) it else -it } + fadeIn(Motion.effects(150))) togetherWith
                            (slideOutVertically(Motion.slide) { if (up) -it else it } + fadeOut(Motion.effects(100)))
                    },
                    label = "digit",
                ) { digit ->
                    Text(digit.toString(), style = MaterialTheme.typography.displayLarge)
                }
            }
        }
    }
}

/** One light per beat; the current beat pops, the downbeat is larger and brass. */
@Composable
private fun BeatLights(signature: TimeSignature, active: Int?, playing: Boolean) {
    Row(
        Modifier.height(28.dp).semantics { contentDescription = "${signature.beats} beats per bar" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(signature.beats) { index ->
            if (signature == TimeSignature.SixEight && index == 3) Spacer(Modifier.width(6.dp))
            val accent = signature.accent(index)
            val on = playing && active == index
            val base = when (accent) {
                Accent.Strong -> 18.dp
                Accent.Medium -> 15.dp
                Accent.Normal -> 12.dp
            }
            val size by animateDpAsState(if (on) base + 8.dp else base, Motion.pop(), label = "beatSize")
            val color by animateColorAsState(
                when {
                    on && accent == Accent.Strong -> Palette.Brass
                    on -> Palette.Bone
                    accent == Accent.Strong -> Palette.Brass.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.outline
                },
                Motion.effects(if (on) 40 else 260),
                label = "beatColor",
            )
            Box(Modifier.size(size).clip(CircleShape).background(color))
        }
    }
}

/** Round ± button; hold it to keep changing, faster the longer it's held. */
@Composable
private fun RepeatButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val interaction = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, Motion.effects(), label = "repeatAlpha")
    Box(
        Modifier
            .size(64.dp)
            .pressScale(interaction, 0.88f)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { step(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val press = PressInteraction.Press(down.position)
                    scope.launch { interaction.emit(press) }
                    step()
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val repeat = scope.launch {
                        delay(420)
                        var interval = 110L
                        while (true) {
                            step()
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            delay(interval)
                            interval = (interval * 0.9).toLong().coerceAtLeast(35)
                        }
                    }
                    try {
                        waitForUpOrCancellation()
                    } finally {
                        // Also runs when the gesture is cancelled, e.g. the button disables itself at the BPM limit.
                        repeat.cancel()
                        scope.launch { interaction.emit(PressInteraction.Release(press)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
    }
}

/** Tap tempo. Registers on touch-down, not release, so the timing matches the player's hand. */
@Composable
private fun TapButton(onTap: (Long) -> Unit, modifier: Modifier = Modifier) {
    val tap by rememberUpdatedState(onTap)
    val flash = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var taps by remember { mutableIntStateOf(0) }
    val press = remember { mutableFloatStateOf(1f) }
    val scale by animateFloatAsState(press.floatValue, Motion.spatialFast(), label = "tapScale")
    fun fire() {
        tap(SystemClock.uptimeMillis())
        taps++
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(380, easing = LinearOutSlowInEasing))
        }
    }
    Box(
        modifier
            .height(64.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f + 0.25f * flash.value))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f + 0.55f * flash.value), CircleShape)
            .semantics {
                role = Role.Button
                contentDescription = "Tap tempo"
                onClick { fire(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    press.floatValue = 0.94f
                    fire()
                    waitForUpOrCancellation()
                    press.floatValue = 1f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "TAP",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The big start/stop control. It morphs from a circle (stopped) to a rounded square (playing). */
@Composable
private fun PlayButton(playing: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (playing) 30.dp else 46.dp, Motion.spatial(), label = "playCorner")
    val width by animateDpAsState(if (playing) 124.dp else 92.dp, Motion.spatial(), label = "playWidth")
    val container by animateColorAsState(
        if (playing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        Motion.effects(),
        label = "playColor",
    )
    val view = LocalView.current
    Box(
        Modifier
            .size(width = width, height = 92.dp)
            .pressScale(interaction, 0.9f)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = if (playing) "Stop" else "Start") {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            playing,
            transitionSpec = {
                (scaleIn(Motion.pop(), initialScale = 0.4f) + fadeIn(Motion.effects(120))) togetherWith
                    (scaleOut(Motion.effects(120), targetScale = 0.4f) + fadeOut(Motion.effects(90)))
            },
            label = "playIcon",
        ) { isPlaying ->
            Icon(
                if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Start",
                tint = MaterialTheme.colorScheme.background,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

