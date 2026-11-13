package com.celestiallemon.tuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.celestiallemon.tuner.metronome.MaxBpm
import com.celestiallemon.tuner.metronome.MinBpm
import com.celestiallemon.tuner.repertoire.Level
import com.celestiallemon.tuner.repertoire.Song
import com.celestiallemon.tuner.repertoire.SongDraft
import com.celestiallemon.tuner.repertoire.practiceSummary
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FretwiseSheet(onDismiss: () -> Unit, content: @Composable (dismiss: (then: () -> Unit) -> Unit) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Slide the sheet away first, then act, so it never vanishes abruptly.
    val dismiss: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            then()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0), bottomEnd = androidx.compose.foundation.shape.CornerSize(0)),
    ) {
        Column(Modifier.navigationBarsPadding()) { content(dismiss) }
    }
}

@Composable
fun SongDetailSheet(
    song: Song,
    today: LocalDate,
    onDismiss: () -> Unit,
    onMove: (Level) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onYouTube: () -> Unit,
    onMetronome: () -> Unit,
) {
    FretwiseSheet(onDismiss) { dismiss ->
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(song.level)
                Spacer(Modifier.width(10.dp))
                Text(practiceSummary(song, today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(song.title, style = MaterialTheme.typography.headlineMedium)
            if (song.artist.isNotBlank()) {
                Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onYouTube,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.PlayCircle, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (song.link.isNotBlank()) "Open link" else "YouTube")
                }
                if (song.bpm != null) {
                    FilledTonalButton(onClick = onMetronome, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.Metronome, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${song.bpm} BPM")
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Overline("Level", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LevelTrack(song.level)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val previous = song.level.previous
                val next = song.level.next
                if (previous != null) {
                    OutlinedButton(onClick = { onMove(previous) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Back to ${previous.label}", maxLines = 1)
                    }
                }
                if (next != null) {
                    Button(
                        onClick = { onMove(next) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = next.color, contentColor = MaterialTheme.colorScheme.background),
                    ) {
                        Text("Move to ${next.label}", maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                    }
                }
            }

            if (song.notes.isNotBlank()) {
                Spacer(Modifier.height(28.dp))
                Overline("Notes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    song.notes,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(16.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { dismiss(onDelete) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete")
                }
                TextButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Edit")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Four connected stops, L1 → Memory, with a marker that slides to the song's level. */
@Composable
private fun LevelTrack(current: Level) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
        val stops = Level.entries.size
        val step = (maxWidth - 28.dp) / (stops - 1)
        val markerX by animateDpAsState(step * current.ordinal, Motion.spatial(), label = "levelMarker")
        val markerColor by animateColorAsState(current.color, Motion.effects(300), label = "levelColor")
        // Rail, coloured up to the current level.
        Box(Modifier.padding(start = 14.dp, top = 13.dp).width(step * (stops - 1)).height(2.dp).background(MaterialTheme.colorScheme.outline))
        Box(Modifier.padding(start = 14.dp, top = 13.dp).width(markerX).height(2.dp).background(markerColor))
        for (level in Level.entries) {
            val reached = level.ordinal <= current.ordinal
            Column(
                Modifier.offset(x = step * level.ordinal).width(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (reached) level.color else MaterialTheme.colorScheme.outline),
                )
            }
        }
        Box(
            Modifier
                .offset(x = markerX + 2.dp, y = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(markerColor.copy(alpha = 0.3f)),
        )
        // Labels under the stops.
        for (level in Level.entries) {
            Text(
                level.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (level == current) level.color else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .offset(x = step * level.ordinal + 14.dp - if (level == Level.Memory) 36.dp else 8.dp, y = 26.dp),
            )
        }
    }
}

@Composable
fun SongFormSheet(initial: SongDraft, isNew: Boolean, onDismiss: () -> Unit, onSave: (SongDraft) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var artist by rememberSaveable { mutableStateOf(initial.artist) }
    var bpm by rememberSaveable { mutableStateOf(initial.bpm?.toString().orEmpty()) }
    var link by rememberSaveable { mutableStateOf(initial.link) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var triedSave by remember { mutableStateOf(false) }

    val bpmValue = bpm.toIntOrNull()
    val bpmError = bpm.isNotBlank() && (bpmValue == null || bpmValue !in MinBpm..MaxBpm)
    val titleError = triedSave && title.isBlank()
    val valid = title.isNotBlank() && !bpmError
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (isNew) focus.requestFocus() }

    FretwiseSheet(onDismiss) { dismiss ->
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isNew) "New song" else "Edit song", style = MaterialTheme.typography.headlineSmall)
                    if (isNew) {
                        Text("It starts in L1", style = MaterialTheme.typography.bodySmall, color = Level.L1.color)
                    }
                }
                Button(
                    onClick = {
                        triedSave = true
                        if (valid) dismiss { onSave(SongDraft(title, artist, bpmValue, notes, link)) }
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isNew) "Add" else "Save")
                }
            }
            Spacer(Modifier.height(20.dp))
            val fieldColors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            )
            OutlinedTextField(
                title, { title = it },
                label = { Text("Title") },
                isError = titleError,
                supportingText = if (titleError) ({ Text("Give the song a title") }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                colors = fieldColors,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    artist, { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    colors = fieldColors,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    bpm, { value -> bpm = value.filter(Char::isDigit).take(3) },
                    label = { Text("BPM") },
                    isError = bpmError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    colors = fieldColors,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.width(104.dp),
                )
            }
            if (bpmError) {
                Text(
                    "Tempo must be $MinBpm–$MaxBpm BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                link, { link = it },
                label = { Text("Link (YouTube, tab…)") },
                placeholder = { Text("Leave empty to search YouTube") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                colors = fieldColors,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                notes, { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Capo, tuning, tricky bars…") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions.Default,
                colors = fieldColors,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SwapSheet(level: Level, songs: List<Song>, current: String?, today: LocalDate, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    FretwiseSheet(onDismiss) { dismiss ->
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(level)
                Spacer(Modifier.width(10.dp))
                Text("Today's ${level.label} song", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Least recently practiced first",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            for (song in songs.sortedBy { it.practiced.lastOrNull() ?: "" }) {
                val selected = song.id == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.RadioButton) { dismiss { onPick(song.id) } }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = level.color), modifier = Modifier.padding(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(practiceSummary(song, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
