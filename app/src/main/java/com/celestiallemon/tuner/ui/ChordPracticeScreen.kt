package com.celestiallemon.tuner.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celestiallemon.tuner.metronome.MaxBpm
import com.celestiallemon.tuner.metronome.MetronomeEngine
import com.celestiallemon.tuner.metronome.MinBpm
import com.celestiallemon.tuner.metronome.TimeSignature
import com.celestiallemon.tuner.playalong.ChordLibrary
import com.celestiallemon.tuner.playalong.ProgressionStep
import com.celestiallemon.tuner.playalong.SongChart
import com.celestiallemon.tuner.repertoire.Song
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class ChordDrillMode(val label: String) {
    Progression("Progression"),
    Pair("Pair drill"),
    OneMinute("1-Min"),
}

/**
 * Song-aware (or free) chord warm-up screen:
 * - Fretboard diagrams for the song's chords (or any selected chords)
 * - Metronome-paced progression & 2-chord transition drills with beat-synced strumming arrows
 * - 1-minute chord-change timer
 * - One-tap "Play song" handoff directly into the play-along video
 */
@Composable
fun ChordPracticeScreen(
    songs: List<Song>,
    charts: Map<String, SongChart>,
    initialSongId: String?,
    engine: MetronomeEngine?,
    metronomePlaying: Boolean,
    onStartMetronome: (bpm: Int, signature: TimeSignature) -> Unit,
    onStopMetronome: () -> Unit,
    onBack: () -> Unit,
    onPlaySong: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val songsWithChords = remember(songs, charts) {
        songs.filter { s -> s.chords.isNotEmpty() || charts[s.chartId]?.uniqueChords()?.isNotEmpty() == true }
    }
    var selectedSongId by rememberSaveable(initialSongId) {
        mutableStateOf(initialSongId ?: songsWithChords.firstOrNull()?.id)
    }
    val selectedSong = songs.firstOrNull { it.id == selectedSongId }
    val selectedChart = selectedSong?.chartId?.let { charts[it] }

    var freeChords by rememberSaveable { mutableStateOf(listOf("C", "G", "D", "Em")) }
    val activeChords = remember(selectedSong, selectedChart, freeChords) {
        val fromChart = selectedChart?.uniqueChords().orEmpty()
        val fromSong = selectedSong?.chords.orEmpty()
        (fromChart.ifEmpty { fromSong }).ifEmpty { freeChords }
    }
    val songProgression = remember(selectedChart, activeChords) {
        selectedChart?.practiceProgression()?.takeIf { it.isNotEmpty() }
            ?: activeChords.map { ProgressionStep(it, 4) }
    }

    val songBpm = selectedSong?.bpm ?: selectedChart?.roundedBpm ?: 100
    val signature = selectedChart?.timeSignature() ?: TimeSignature.FourFour
    val strumPattern = selectedChart?.defaultStrumPattern() ?: ChordLibrary.patternFor("fourBeat")

    var drillMode by rememberSaveable { mutableStateOf(ChordDrillMode.Progression) }
    var pairFirst by remember(selectedSongId, activeChords) { mutableStateOf(activeChords.getOrNull(0) ?: "C") }
    var pairSecond by remember(selectedSongId, activeChords) { mutableStateOf(activeChords.getOrNull(1) ?: "G") }
    var drillBpm by remember(selectedSongId) { mutableIntStateOf((songBpm * 0.7f).roundToInt().coerceIn(MinBpm, MaxBpm)) }
    var beatsPerChord by remember(selectedSongId) {
        mutableIntStateOf(if (signature == TimeSignature.SixEight) 6 else 4)
    }

    // Stop drill audio cleanly when leaving the screen.
    DisposableEffect(Unit) {
        onDispose { onStopMetronome() }
    }

    // Sync tempo changes live while the drill is running.
    LaunchedEffect(drillBpm, signature, metronomePlaying) {
        if (metronomePlaying && engine != null) {
            engine.bpm = drillBpm
            if (engine.signature != signature) engine.signature = signature
        }
    }

    // Track current beat and fractional progress from the audio playhead so offbeat ("&") strums highlight accurately.
    var playhead by remember { mutableStateOf<MetronomeEngine.PlayheadPosition?>(null) }
    LaunchedEffect(metronomePlaying, engine) {
        playhead = null
        if (!metronomePlaying || engine == null) return@LaunchedEffect
        while (true) withFrameNanos { playhead = engine.playheadPosition() }
    }

    // Compute which chord and strum slot are active right now.
    val steps: List<ProgressionStep> = remember(drillMode, songProgression, pairFirst, pairSecond, beatsPerChord) {
        when (drillMode) {
            ChordDrillMode.Progression -> songProgression
            ChordDrillMode.Pair, ChordDrillMode.OneMinute -> listOf(
                ProgressionStep(pairFirst, beatsPerChord),
                ProgressionStep(pairSecond, beatsPerChord),
            )
        }
    }
    val totalLoopBeats = remember(steps) { steps.sumOf { it.beats }.coerceAtLeast(1) }
    val activeStepIndex: Int?
    val nextStepIndex: Int?
    val activeStrumSlot: Int?
    val currentPlayhead = playhead
    if (metronomePlaying && currentPlayhead != null && drillMode != ChordDrillMode.OneMinute) {
        val loopBeat = (currentPlayhead.beat.serial % totalLoopBeats).toInt()
        var acc = 0
        var found = 0
        var localBeat = 0
        for (i in steps.indices) {
            if (loopBeat < acc + steps[i].beats) {
                found = i
                localBeat = loopBeat - acc
                break
            }
            acc += steps[i].beats
        }
        activeStepIndex = found
        nextStepIndex = (found + 1) % steps.size
        activeStrumSlot = strumSlotIndex(
            localBeat = localBeat,
            beatFraction = currentPlayhead.fraction,
            cycleBeats = strumPattern.cycleBeats,
            slotCount = strumPattern.slots.size,
        )
    } else {
        activeStepIndex = null
        nextStepIndex = null
        activeStrumSlot = null
    }
    val activeChordName = activeStepIndex?.let { steps.getOrNull(it)?.chord }

    // 1-Minute chord-change timer state.
    var timerRunning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var changeCount by remember { mutableIntStateOf(0) }
    var bestChanges by remember(pairFirst, pairSecond) { mutableIntStateOf(0) }
    LaunchedEffect(timerRunning) {
        if (!timerRunning) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        timerRunning = false
        if (changeCount > bestChanges) bestChanges = changeCount
    }

    val view = LocalView.current

    Column(modifier.fillMaxSize()) {
        // Header with Back + Title + Play Song handoff.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                onStopMetronome()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    selectedSong?.title ?: "Chord Practice",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    selectedSong?.capoLabel?.takeIf { it.isNotBlank() },
                    selectedSong?.keyLabel?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Brass,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selectedSong != null && selectedSong.hasPlayAlong) {
                Button(
                    onClick = {
                        onStopMetronome()
                        onPlaySong(selectedSong)
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.PlayCircle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play song", maxLines = 1)
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // Song / Free practice selector strip.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (song in songsWithChords) {
                    val selected = song.id == selectedSongId
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onStopMetronome()
                            selectedSongId = song.id
                        },
                        label = { Text(song.title, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
                FilterChip(
                    selected = selectedSongId == null,
                    onClick = {
                        onStopMetronome()
                        selectedSongId = null
                    },
                    label = { Text("Free practice") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }

            // Free practice chord picker.
            if (selectedSongId == null) {
                Overline(
                    "Pick chords to drill",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (name in ChordLibrary.allNames) {
                        val picked = name in freeChords
                        FilterChip(
                            selected = picked,
                            onClick = {
                                freeChords = if (picked && freeChords.size > 2) freeChords - name
                                else if (!picked) freeChords + name
                                else freeChords
                            },
                            label = { Text(name) },
                        )
                    }
                }
            }

            // Chord fretboard cards.
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline(
                    if (drillMode == ChordDrillMode.Progression) "Chords in this song" else "Tap two chords to pair",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${activeChords.size} shapes",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.Faint,
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(activeChords, key = { it }) { chordName ->
                    val shape = ChordLibrary.find(chordName)
                    val isLive = activeChordName == chordName
                    val isPairMember = drillMode != ChordDrillMode.Progression && (chordName == pairFirst || chordName == pairSecond)
                    val borderColor by animateColorAsState(
                        when {
                            isLive -> Palette.Brass
                            isPairMember -> Palette.Brass.copy(alpha = 0.65f)
                            else -> Palette.Outline.copy(alpha = 0.5f)
                        },
                        Motion.effects(160),
                        label = "chordCardBorder",
                    )
                    Surface(
                        modifier = Modifier
                            .width(126.dp)
                            .border(if (isLive || isPairMember) 1.5.dp else 1.dp, borderColor, MaterialTheme.shapes.large)
                            .clip(MaterialTheme.shapes.large)
                            .clickable(enabled = drillMode != ChordDrillMode.Progression, role = Role.Button) {
                                if (chordName != pairFirst && chordName != pairSecond) {
                                    pairFirst = pairSecond
                                    pairSecond = chordName
                                }
                            },
                        shape = MaterialTheme.shapes.large,
                        color = if (isLive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Column(
                            Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                chordName,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (isLive || isPairMember) Palette.Brass else Palette.Bone,
                            )
                            Spacer(Modifier.height(6.dp))
                            if (shape != null) {
                                ChordDiagram(
                                    shape = shape,
                                    highlighted = isLive,
                                    modifier = Modifier.size(width = 100.dp, height = 118.dp),
                                )
                            } else {
                                Box(Modifier.size(width = 100.dp, height = 118.dp), contentAlignment = Alignment.Center) {
                                    Text("No diagram", style = MaterialTheme.typography.bodySmall, color = Palette.Faint)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Drill mode selector.
            SegmentedPicker(
                options = ChordDrillMode.entries,
                selected = drillMode,
                label = { it.label },
                onSelect = { next ->
                    if (next == ChordDrillMode.OneMinute) onStopMetronome()
                    if (next != ChordDrillMode.OneMinute) timerRunning = false
                    drillMode = next
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                height = 44.dp,
            )

            Spacer(Modifier.height(14.dp))

            if (drillMode == ChordDrillMode.OneMinute) {
                OneMinuteCard(
                    first = pairFirst,
                    second = pairSecond,
                    secondsLeft = secondsLeft,
                    count = changeCount,
                    best = bestChanges,
                    running = timerRunning,
                    onStartPause = {
                        if (secondsLeft == 0) {
                            secondsLeft = 60
                            changeCount = 0
                        }
                        timerRunning = !timerRunning
                    },
                    onReset = {
                        timerRunning = false
                        secondsLeft = 60
                        changeCount = 0
                    },
                    onTapChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        if (!timerRunning && secondsLeft > 0) timerRunning = true
                        if (secondsLeft > 0) changeCount++
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                // Metronome-paced Progression / Pair drill card.
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Progression chips with live highlight & "Next" lookahead.
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Overline(
                                if (drillMode == ChordDrillMode.Progression) "Chord loop" else "2-Chord switch",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            val nextChord = nextStepIndex?.let { steps.getOrNull(it)?.chord }
                            if (nextChord != null) {
                                Text(
                                    "Next: $nextChord",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Palette.Brass,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            steps.forEachIndexed { idx, step ->
                                val isNow = activeStepIndex == idx
                                val isNext = nextStepIndex == idx
                                Box(
                                    Modifier
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(
                                            when {
                                                isNow -> Palette.Brass
                                                isNext -> MaterialTheme.colorScheme.primaryContainer
                                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                        )
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "${step.chord} · ${step.beats}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = when {
                                            isNow -> Palette.Background
                                            isNext -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> Palette.Bone
                                        },
                                    )
                                }
                                if (idx < steps.lastIndex) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        null,
                                        tint = Palette.Faint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // Beat-synced strumming pattern.
                        Overline(
                            "${strumPattern.name} · ${signature.label}",
                            color = Palette.Faint,
                        )
                        Spacer(Modifier.height(10.dp))
                        StrummingRow(
                            pattern = strumPattern,
                            activeSlot = activeStrumSlot,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(18.dp))

                        // Tempo control + speed presets relative to song BPM.
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            IconButton(
                                onClick = { drillBpm = (drillBpm - 5).coerceAtLeast(MinBpm) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Rounded.Remove, "Slower")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                                Text("$drillBpm", style = MaterialTheme.typography.headlineSmall)
                                Text("BPM", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                            }
                            IconButton(
                                onClick = { drillBpm = (drillBpm + 5).coerceAtMost(MaxBpm) },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(Icons.Rounded.Add, "Faster")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            for (pct in listOf(50, 70, 85, 100)) {
                                val target = (songBpm * pct / 100f).roundToInt().coerceIn(MinBpm, MaxBpm)
                                val selected = drillBpm == target
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(if (selected) Palette.Brass else MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable { drillBpm = target }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$pct%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) Palette.Background else Palette.Muted,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        if (drillMode == ChordDrillMode.Pair) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Beats per chord", style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (b in listOf(2, 4, 6, 8)) {
                                        val selected = beatsPerChord == b
                                        Box(
                                            Modifier
                                                .clip(CircleShape)
                                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                                .clickable { beatsPerChord = b }
                                                .padding(horizontal = 12.dp, vertical = 5.dp),
                                        ) {
                                            Text(
                                                "$b",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Palette.Muted,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                if (metronomePlaying) onStopMetronome()
                                else onStartMetronome(drillBpm, signature)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (metronomePlaying) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.background,
                            ),
                        ) {
                            Icon(if (metronomePlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (metronomePlaying) "Stop drill" else "Start drill")
                        }
                    }
                }
            }
        }
    }
}

/** Maps a beat index plus fractional progress [0f, 1f) to its active slot in a strumming pattern. */
internal fun strumSlotIndex(localBeat: Int, beatFraction: Float, cycleBeats: Int, slotCount: Int): Int {
    if (slotCount <= 0) return 0
    val cycle = cycleBeats.coerceAtLeast(1)
    val continuousBeat = (localBeat % cycle) + beatFraction.coerceIn(0f, 0.999f)
    return ((continuousBeat / cycle) * slotCount).toInt().coerceIn(0, slotCount - 1)
}

@Composable
private fun OneMinuteCard(
    first: String,
    second: String,
    secondsLeft: Int,
    count: Int,
    best: Int,
    running: Boolean,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onTapChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Overline("1-Minute Chord Changes", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text("$first ↔ $second", style = MaterialTheme.typography.headlineMedium, color = Palette.Brass)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(
                        secondsLeft,
                        transitionSpec = { fadeIn(Motion.effects(120)) togetherWith fadeOut(Motion.effects(90)) },
                        label = "timerSec",
                    ) { s ->
                        Text("${s}s", style = MaterialTheme.typography.displaySmall)
                    }
                    Text("Remaining", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", style = MaterialTheme.typography.displaySmall, color = Palette.Brass)
                    Text(
                        if (best > 0) "Changes · best $best" else "Changes",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Muted,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onTapChange,
                enabled = secondsLeft > 0,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(
                    if (secondsLeft == 0) "Time's up — $count changes!" else "Tap on each clean switch (+1)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onStartPause, modifier = Modifier.weight(1f).height(46.dp)) {
                    Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "Pause" else if (secondsLeft == 0) "Go again" else "Start timer")
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.height(46.dp)) {
                    Icon(Icons.Rounded.Refresh, "Reset", Modifier.size(18.dp))
                }
            }
        }
    }
}
