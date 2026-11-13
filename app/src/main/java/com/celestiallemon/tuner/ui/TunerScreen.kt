package com.celestiallemon.tuner.ui

import android.os.Build
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiallemon.tuner.Instrument
import com.celestiallemon.tuner.Tuning
import com.celestiallemon.tuner.TunerUiState
import kotlin.math.roundToInt

@Composable
fun TunerScreen(
    state: TunerUiState,
    onSelectTuning: (Instrument, Tuning) -> Unit,
    onA4Change: (Double) -> Unit,
    onLockString: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = state.reading
    val strings = state.tuning.strings
    val chromatic = strings.isEmpty()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    // A short confirm buzz each time a string settles in tune.
    val view = LocalView.current
    val lastEvent = remember { mutableStateOf(reading.tunedEvent) }
    LaunchedEffect(reading.tunedEvent) {
        if (reading.tunedEvent > lastEvent.value) {
            val type = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            view.performHapticFeedback(type)
        }
        lastEvent.value = reading.tunedEvent
    }

    val allTuned = !chromatic && reading.tuned.size == strings.size
    val lockedNote = if (!state.auto) reading.stringIndex?.let { strings.getOrNull(it) } else null
    val hint = when {
        reading.active && reading.inTune -> "In tune"
        reading.active && reading.cents < 0 -> "Tune up"
        reading.active -> "Tune down"
        allTuned -> "All strings in tune"
        lockedNote != null -> "Play the ${lockedNote.letter} string"
        chromatic -> "Play a note"
        else -> "Play a string"
    }
    val hintColor = when {
        reading.active && reading.inTune -> Palette.InTune
        reading.active -> Palette.Bone
        allTuned -> Palette.InTune
        else -> Palette.Muted
    }
    val detail = reading.frequency?.let { f ->
        val target = reading.target?.frequency(state.a4)
        if (target != null) "%.1f Hz  ·  target %.1f Hz".format(f, target) else "%.1f Hz".format(f)
    }

    Column(modifier.fillMaxSize()) {
        TopBar(state, onClick = { showSheet = true })
        Spacer(Modifier.weight(if (chromatic) 1f else 0.14f))
        Meter(
            active = reading.active,
            cents = reading.cents,
            inTune = reading.inTune,
            note = reading.target,
            hint = hint,
            hintColor = hintColor,
            detail = detail,
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (chromatic) {
            Text(
                text = "Chromatic mode names whatever note it hears.\nPick an instrument to tune string by string.",
                color = Palette.Faint,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
            )
            Spacer(Modifier.weight(1f))
        } else {
            Headstock(
                strings = strings,
                leftPegs = state.instrument.leftPegs,
                a4 = state.a4,
                activeString = reading.stringIndex,
                sounding = reading.active,
                inTune = reading.inTune,
                level = reading.level,
                tuned = reading.tuned,
                onPegClick = { index -> onLockString(if (!state.auto && reading.stringIndex == index) null else index) },
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp),
            )
            ModeBar(auto = state.auto, tunedCount = reading.tuned.size, total = strings.size, onAuto = { onLockString(null) })
        }
        state.error?.let {
            Text(
                it,
                color = Palette.Off,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }

    if (showSheet) {
        TuningSheet(
            instrument = state.instrument,
            tuning = state.tuning,
            a4 = state.a4,
            onSelect = { instrument, tuning ->
                onSelectTuning(instrument, tuning)
                showSheet = false
            },
            onA4Change = onA4Change,
            onDismiss = { showSheet = false },
        )
    }
}

@Composable
private fun TopBar(state: TunerUiState, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClickLabel = "Change tuning", onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    state.instrument.name.uppercase(),
                    color = Palette.Brass,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.6.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.tuning.name,
                        color = Palette.Bone,
                        fontFamily = Display,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp,
                    )
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Palette.Muted)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "A4 = ${formatHz(state.a4)} Hz",
            color = if (state.a4 == 440.0) Palette.Muted else Palette.Brass,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(CircleShape)
                .border(1.dp, Palette.Outline, CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Change reference pitch", onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ModeBar(auto: Boolean, tunedCount: Int, total: Int, onAuto: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (auto) Palette.Brass.copy(alpha = 0.16f) else Color.Transparent)
                .border(1.dp, if (auto) Palette.Brass.copy(alpha = 0.6f) else Palette.Outline, CircleShape)
                .clickable(role = Role.Button, enabled = !auto, onClickLabel = "Detect strings automatically", onClick = onAuto)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = if (auto) Palette.Brass else Palette.Muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (auto) "Auto" else "Manual · tap for auto",
                color = if (auto) Palette.Brass else Palette.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "$tunedCount of $total tuned",
            color = if (tunedCount == total) Palette.InTune else Palette.Faint,
            fontSize = 13.sp,
        )
    }
    Spacer(Modifier.height(4.dp))
}

fun formatHz(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else "%.1f".format(value)

@Composable
fun PermissionScreen(permanentlyDenied: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Palette.Brass, modifier = Modifier.size(48.dp))
            Text(
                "Let the tuner hear you",
                color = Palette.Bone,
                fontFamily = Display,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                if (permanentlyDenied) {
                    "Microphone access is turned off. Turn it on in the app's settings to tune."
                } else {
                    "The tuner listens to your strings through the microphone to measure their pitch. Nothing is recorded or sent anywhere."
                },
                color = Palette.Muted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (permanentlyDenied) "Open settings" else "Allow microphone",
                color = Palette.Background,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Palette.Brass)
                    .clickable(role = Role.Button, onClick = if (permanentlyDenied) onOpenSettings else onAllow)
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}
