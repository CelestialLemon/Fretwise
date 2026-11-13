package com.celestiallemon.tuner.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.celestiallemon.tuner.repertoire.Level
import com.celestiallemon.tuner.repertoire.RepertoireUiState
import com.celestiallemon.tuner.repertoire.RepertoireViewModel
import com.celestiallemon.tuner.repertoire.Song
import com.celestiallemon.tuner.repertoire.SongDraft
import com.celestiallemon.tuner.repertoire.inLevel
import com.celestiallemon.tuner.repertoire.practiceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.format.DateTimeFormatter

/** Everything the Repertoire screen can ask for. */
class RepertoireActions(
    val add: (SongDraft) -> Unit = {},
    val edit: (String, SongDraft) -> Unit = { _, _ -> },
    val delete: (String) -> Unit = {},
    val move: (String, Level) -> Unit = { _, _ -> },
    val toggleDone: (String) -> Unit = {},
    val swap: (Level, String) -> Unit = { _, _ -> },
    val export: (android.net.Uri) -> Unit = {},
    val readImport: (android.net.Uri) -> Unit = {},
    val confirmImport: () -> Unit = {},
    val cancelImport: () -> Unit = {},
    val openMetronome: (Song) -> Unit = {},
)

/** What a level is for, shown under its name. */
private val Level.blurb: String
    get() = when (this) {
        Level.L1 -> "Just picked up"
        Level.L2 -> "Getting the hang of it"
        Level.L3 -> "Polishing"
        Level.Memory -> "Know it by heart"
    }

@Composable
fun RepertoireScreen(
    state: RepertoireUiState,
    actions: RepertoireActions,
    modifier: Modifier = Modifier,
    messages: Flow<RepertoireViewModel.Message> = emptyFlow(),
    initiallyOpenSong: String? = null,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(messages) {
        messages.collect { message ->
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = if (message.undo != null) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) message.undo?.invoke()
        }
    }

    var detailId by rememberSaveable { mutableStateOf(initiallyOpenSong) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var swapLevel by rememberSaveable { mutableStateOf<Level?>(null) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<Level>()) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(actions.export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions.readImport)
    }

    val list = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { list.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.songs.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Add song") },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    onClick = { adding = true },
                    expanded = fabExpanded,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = list,
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item(key = "header") {
                ScreenHeader("Repertoire") {
                    BackupMenu(
                        onExport = { exportLauncher.launch("fretwise-backup-${state.today}.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) },
                    )
                }
            }
            if (state.loaded && state.songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = "Start your repertoire",
                        body = "Add a song you want to learn. It starts in L1, and you move it up as it sticks, " +
                            "until it lives in Memory. Each day you'll practice one song from every level.",
                        modifier = Modifier.animateItem(),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        ExtendedFloatingActionButton(
                            text = { Text("Add your first song") },
                            icon = { Icon(Icons.Rounded.Add, null) },
                            onClick = { adding = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                return@LazyColumn
            }
            if (state.todaySongs.isNotEmpty()) {
                item(key = "today") {
                    TodayCard(
                        state = state,
                        onOpen = { detailId = it },
                        onToggle = actions.toggleDone,
                        onSwap = { swapLevel = it },
                        modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            for (level in Level.entries) {
                val songs = state.songs.inLevel(level)
                val open = level !in collapsed
                item(key = "level-${level.name}") {
                    LevelHeader(
                        level = level,
                        count = songs.size,
                        expanded = open,
                        onToggle = { collapsed = if (open) collapsed + level else collapsed - level },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (open) {
                    if (songs.isEmpty()) {
                        item(key = "empty-${level.name}") {
                            Text(
                                if (level == Level.L1) "Nothing new on the go. Add a song to start learning it." else "Songs move here from ${level.previous?.label}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.animateItem().padding(start = 40.dp, end = 24.dp, bottom = 12.dp),
                            )
                        }
                    }
                    items(songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isToday = state.plan.picks[level] == song.id,
                            summary = practiceSummary(song, state.today),
                            onClick = { detailId = song.id },
                            modifier = Modifier
                                .animateItem(placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1)))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    state.song(detailId)?.let { song ->
        SongDetailSheet(
            song = song,
            today = state.today,
            onDismiss = { detailId = null },
            onMove = { level -> actions.move(song.id, level) },
            onEdit = { editId = song.id },
            onDelete = {
                detailId = null
                actions.delete(song.id)
            },
            onYouTube = { openUrl(context, com.celestiallemon.tuner.repertoire.youtubeUrl(song)) },
            onMetronome = {
                detailId = null
                actions.openMetronome(song)
            },
        )
    }
    if (adding || editId != null) {
        val editing = state.song(editId)
        SongFormSheet(
            initial = editing?.let { SongDraft(it.title, it.artist, it.bpm, it.notes, it.link) } ?: SongDraft(),
            isNew = editing == null,
            onDismiss = {
                adding = false
                editId = null
            },
            onSave = { draft ->
                if (editing == null) actions.add(draft) else actions.edit(editing.id, draft)
                adding = false
                editId = null
            },
        )
    }
    swapLevel?.let { level ->
        SwapSheet(
            level = level,
            songs = state.songs.inLevel(level),
            current = state.plan.picks[level],
            today = state.today,
            onPick = { id ->
                actions.swap(level, id)
                swapLevel = null
            },
            onDismiss = { swapLevel = null },
        )
    }
    state.pendingImport?.let { incoming ->
        AlertDialog(
            onDismissRequest = actions.cancelImport,
            icon = { Icon(Icons.Rounded.Download, null) },
            title = { Text("Replace your repertoire?") },
            text = {
                val n = state.songs.size
                Text(
                    "The backup has ${incoming.size} ${if (incoming.size == 1) "song" else "songs"}. " +
                        "It will replace the $n ${if (n == 1) "song" else "songs"} you have now, along with their practice history.",
                )
            },
            confirmButton = { TextButton(onClick = actions.confirmImport) { Text("Replace") } },
            dismissButton = { TextButton(onClick = actions.cancelImport) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun BackupMenu(onExport: () -> Unit, onImport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
        ) {
            DropdownMenuItem(
                text = { Text("Export backup") },
                leadingIcon = { Icon(Icons.Rounded.Upload, null) },
                onClick = {
                    open = false
                    onExport()
                },
            )
            DropdownMenuItem(
                text = { Text("Import backup") },
                leadingIcon = { Icon(Icons.Rounded.Download, null) },
                onClick = {
                    open = false
                    onImport()
                },
            )
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
private fun TodayCard(
    state: RepertoireUiState,
    onOpen: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSwap: (Level) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = state.todaySongs
    val done = state.doneToday
    val allDone = done == slots.size
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(top = 20.dp, bottom = 10.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Overline("Today · ${state.today.format(dayFormat)}")
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(
                        if (allDone) "All practiced. Nice work." else "$done of ${slots.size} practiced",
                        transitionSpec = {
                            (slideInVertically(Motion.slide) { it / 2 } + fadeIn(Motion.effects())) togetherWith
                                (slideOutVertically(Motion.slide) { -it / 2 } + fadeOut(Motion.effects(120)))
                        },
                        label = "todayTitle",
                    ) { text ->
                        Text(text, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                ProgressRing(done, slots.size)
            }
            Spacer(Modifier.height(12.dp))
            for ((level, song) in slots) {
                TodaySlot(
                    level = level,
                    song = song,
                    done = song.practicedOn(state.today),
                    canSwap = state.songs.count { it.level == level } > 1,
                    onOpen = { onOpen(song.id) },
                    onToggle = { onToggle(song.id) },
                    onSwap = { onSwap(level) },
                )
            }
        }
    }
}

/** A ring that fills as the day's songs are ticked off, and turns green with a check when complete. */
@Composable
private fun ProgressRing(done: Int, total: Int) {
    val progress by animateFloatAsState(if (total == 0) 0f else done.toFloat() / total, Motion.spatial(), label = "ring")
    val complete = total > 0 && done == total
    val color by animateColorAsState(if (complete) Palette.InTune else Palette.Brass, Motion.effects(400), label = "ringColor")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(52.dp).semantics { contentDescription = "$done of $total practiced" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        AnimatedContent(
            complete,
            transitionSpec = { scaleIn(Motion.pop(), 0.3f) + fadeIn() togetherWith scaleOut(targetScale = 0.3f) + fadeOut() },
            label = "ringCenter",
        ) { isComplete ->
            if (isComplete) {
                Icon(Icons.Rounded.Check, null, tint = Palette.InTune, modifier = Modifier.size(26.dp))
            } else {
                Text("$done", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun TodaySlot(
    level: Level,
    song: Song,
    done: Boolean,
    canSwap: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onSwap: () -> Unit,
) {
    val textAlpha by animateFloatAsState(if (done) 0.55f else 1f, Motion.effects(), label = "slotAlpha")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelBadge(level, Modifier.width(76.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).graphicsLayer { alpha = textAlpha }) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (song.artist.isNotBlank()) {
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (canSwap) {
            IconButton(onClick = onSwap) {
                Icon(Icons.Rounded.SwapVert, "Pick a different ${level.label} song", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DoneCheck(done, level, onToggle)
    }
}

/** A round check button: an outlined ring in the level's colour that fills and pops a tick when done. */
@Composable
private fun DoneCheck(done: Boolean, level: Level, onToggle: () -> Unit) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (done) level.color else level.color.copy(alpha = 0f), Motion.effects(), label = "checkFill")
    val ring by animateColorAsState(if (done) level.color else MaterialTheme.colorScheme.outline, Motion.effects(), label = "checkRing")
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 24.dp),
                role = Role.Checkbox,
                onClickLabel = if (done) "Mark as not practiced" else "Mark as practiced",
            ) {
                if (!done) {
                    view.performHapticFeedback(
                        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
                    )
                }
                onToggle()
            }
            .semantics { contentDescription = if (done) "Practiced" else "Not practiced" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .pressScale(interaction, 0.8f)
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                done,
                enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 700f), initialScale = 0.2f) + fadeIn(tween(90)),
                exit = scaleOut(tween(120), targetScale = 0.4f) + fadeOut(tween(120)),
            ) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LevelHeader(level: Level, count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 0f else -90f, Motion.spatial(), label = "chevron")
    val over = level.softCap?.let { count - it }?.takeIf { it > 0 }
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable(onClickLabel = if (expanded) "Collapse ${level.label}" else "Expand ${level.label}", onClick = onToggle)
            .padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(level.color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(level.label, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (count == 1) "1 song" else "$count songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Text(level.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(over != null, enter = scaleIn(Motion.pop()) + fadeIn(), exit = scaleOut() + fadeOut()) {
            CapWarning(over ?: 0, level.softCap ?: 0)
        }
        Icon(
            Icons.Rounded.ExpandMore,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).rotate(rotation),
        )
    }
}

/** Soft-cap warning: a level holds more songs than you can keep moving forward. Never blocks anything. */
@Composable
private fun CapWarning(over: Int, cap: Int) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Palette.Amber.copy(alpha = 0.14f))
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Palette.Amber, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text("$over over · aim for $cap", style = MaterialTheme.typography.labelMedium, color = Palette.Amber)
    }
}

@Composable
private fun SongRow(song: Song, isToday: Boolean, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.97f),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interaction,
    ) {
        Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isToday) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "TODAY",
                            style = MaterialTheme.typography.labelSmall,
                            color = song.level.color,
                            modifier = Modifier.clip(CircleShape).background(song.level.container).padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
                if (song.artist.isNotBlank()) {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(summary, style = MaterialTheme.typography.labelMedium, color = Palette.Faint)
            }
            song.bpm?.let { bpm ->
                Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$bpm", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text("BPM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "No app can open that link", android.widget.Toast.LENGTH_SHORT).show()
    }
}
