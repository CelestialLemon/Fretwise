package com.celestiallemon.tuner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.celestiallemon.tuner.playalong.ChartRepository
import com.celestiallemon.tuner.playalong.SongChart
import com.celestiallemon.tuner.playalong.SongSection
import com.celestiallemon.tuner.playalong.VideoLibrary
import com.celestiallemon.tuner.repertoire.Song
import com.celestiallemon.tuner.repertoire.youtubeUrl
import com.celestiallemon.tuner.ui.FretwiseTheme
import com.celestiallemon.tuner.ui.Motion
import com.celestiallemon.tuner.ui.Overline
import com.celestiallemon.tuner.ui.Palette
import com.celestiallemon.tuner.ui.openUrl
import kotlinx.coroutines.delay

/**
 * Full-screen landscape play-along video player with speed control (0.5x–1.0x), section jumping,
 * section looping, capo reminder, and direct "Practiced today" marking.
 */
class PlayAlongActivity : ComponentActivity() {
    private var practicedToday = false
    private var linkedVideoUri: String? = null
    private var openChordsOnExit = false
    private var songId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        songId = intent.getStringExtra(EXTRA_SONG_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty()
        val chartId = intent.getStringExtra(EXTRA_CHART_ID)
        val capoLabel = intent.getStringExtra(EXTRA_CAPO_LABEL).orEmpty()
        val keyLabel = intent.getStringExtra(EXTRA_KEY_LABEL).orEmpty()
        val link = intent.getStringExtra(EXTRA_LINK).orEmpty()
        val initialVideoUri = intent.getStringExtra(EXTRA_VIDEO_URI).orEmpty()
        practicedToday = intent.getBooleanExtra(EXTRA_PRACTICED_TODAY, false)

        val chart = ChartRepository.loadBundled(this).firstOrNull { it.id == chartId }
        val videoLibrary = VideoLibrary(this)
        val placeholderSong = Song(
            id = songId,
            title = title,
            artist = artist,
            link = link,
            levelSince = 0L,
            addedAt = 0L,
            chartId = chartId,
            capoLabel = capoLabel,
            keyLabel = keyLabel,
            videoUri = initialVideoUri,
        )

        setContent {
            FretwiseTheme {
                var resolvedUri by remember { mutableStateOf(videoLibrary.resolveVideo(placeholderSong, chart)) }
                var practiced by remember { mutableStateOf(practicedToday) }

                val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        videoLibrary.folderUri = uri
                        resolvedUri = videoLibrary.resolveVideo(placeholderSong, chart)
                    }
                }
                val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        linkedVideoUri = uri.toString()
                        resolvedUri = uri
                    }
                }

                PlayAlongScreen(
                    title = title,
                    artist = artist,
                    capoLabel = capoLabel.ifBlank { chart?.capoLabel.orEmpty() },
                    keyLabel = keyLabel.ifBlank { chart?.keyLabel.orEmpty() },
                    chart = chart,
                    videoUri = resolvedUri,
                    practicedToday = practiced,
                    onTogglePracticed = {
                        practiced = !practiced
                        practicedToday = practiced
                    },
                    onPickFolder = { folderPicker.launch(null) },
                    onPickFile = { filePicker.launch(arrayOf("video/*")) },
                    onOpenYouTube = { openUrl(this, youtubeUrl(placeholderSong)) },
                    onPracticeChords = {
                        openChordsOnExit = true
                        finishWithResult()
                    },
                    onBack = { finishWithResult() },
                )
            }
        }
    }

    override fun finish() {
        publishResult()
        super.finish()
    }

    private fun finishWithResult() {
        publishResult()
        super.finish()
    }

    private fun publishResult() {
        setResult(
            Activity.RESULT_OK,
            Intent().apply {
                putExtra(RESULT_SONG_ID, songId)
                putExtra(RESULT_PRACTICED_TODAY, practicedToday)
                linkedVideoUri?.let { putExtra(RESULT_VIDEO_URI, it) }
                putExtra(RESULT_OPEN_CHORDS, openChordsOnExit)
            },
        )
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        private const val EXTRA_SONG_ID = "song_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_CHART_ID = "chart_id"
        private const val EXTRA_CAPO_LABEL = "capo_label"
        private const val EXTRA_KEY_LABEL = "key_label"
        private const val EXTRA_LINK = "link"
        private const val EXTRA_VIDEO_URI = "video_uri"
        private const val EXTRA_PRACTICED_TODAY = "practiced_today"

        const val RESULT_SONG_ID = "result_song_id"
        const val RESULT_PRACTICED_TODAY = "result_practiced_today"
        const val RESULT_VIDEO_URI = "result_video_uri"
        const val RESULT_OPEN_CHORDS = "result_open_chords"

        fun createIntent(context: Context, song: Song, practicedToday: Boolean): Intent =
            Intent(context, PlayAlongActivity::class.java).apply {
                putExtra(EXTRA_SONG_ID, song.id)
                putExtra(EXTRA_TITLE, song.title)
                putExtra(EXTRA_ARTIST, song.artist)
                putExtra(EXTRA_CHART_ID, song.chartId)
                putExtra(EXTRA_CAPO_LABEL, song.capoLabel)
                putExtra(EXTRA_KEY_LABEL, song.keyLabel)
                putExtra(EXTRA_LINK, song.link)
                putExtra(EXTRA_VIDEO_URI, song.videoUri)
                putExtra(EXTRA_PRACTICED_TODAY, practicedToday)
            }
    }
}

private val SpeedOptions = listOf(0.5f, 0.75f, 0.85f, 1.0f)

@OptIn(UnstableApi::class)
@Composable
private fun PlayAlongScreen(
    title: String,
    artist: String,
    capoLabel: String,
    keyLabel: String,
    chart: SongChart?,
    videoUri: Uri?,
    practicedToday: Boolean,
    onTogglePracticed: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFile: () -> Unit,
    onOpenYouTube: () -> Unit,
    onPracticeChords: () -> Unit,
    onBack: () -> Unit,
) {
    if (videoUri == null) {
        MissingVideoScreen(
            title = title,
            artist = artist,
            expectedFilename = chart?.outputFilename.orEmpty(),
            onPickFolder = onPickFolder,
            onPickFile = onPickFile,
            onOpenYouTube = onOpenYouTube,
            onBack = onBack,
        )
        return
    }

    val context = LocalContext.current
    val sections = remember(chart) { chart?.sections().orEmpty() }
    val capoFret = remember(chart, capoLabel) {
        chart?.capoFret() ?: Regex("""Capo\s+(\d+)""", RegexOption.IGNORE_CASE).find(capoLabel)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = false
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(((chart?.durationSec ?: 0.0) * 1000).toLong()) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    var loopedSection by remember { mutableStateOf<SongSection?>(null) }
    var userStartedOnce by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    ended = false
                    userStartedOnce = true
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.duration > 0L) {
                    durationMs = player.duration
                }
                if (playbackState == Player.STATE_ENDED) {
                    ended = true
                    controlsVisible = true
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Poll playback position and enforce section looping.
    LaunchedEffect(player, loopedSection) {
        while (true) {
            val pos = player.currentPosition.coerceAtLeast(0L)
            positionMs = pos
            if (player.duration > 0L) durationMs = player.duration
            val loop = loopedSection
            if (loop != null && player.isPlaying) {
                val loopEndMs = (loop.endSec * 1000.0).toLong()
                val loopStartMs = (loop.startSec * 1000.0).toLong()
                if (loopEndMs > loopStartMs && pos >= loopEndMs) {
                    player.seekTo(loopStartMs)
                }
            }
            delay(120)
        }
    }

    // Auto-hide controls while playing.
    LaunchedEffect(controlsVisible, isPlaying, ended) {
        if (controlsVisible && isPlaying && !ended) {
            delay(4_000)
            controlsVisible = false
        }
    }

    val activeSection = remember(sections, positionMs) {
        val sec = positionMs / 1000.0
        sections.lastOrNull { sec >= it.startSec - 0.15 }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        // Pre-flight capo reminder banner before first play.
        if (!userStartedOnce && capoFret != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 60.dp),
                shape = CircleShape,
                color = Palette.Brass,
                contentColor = Palette.Background,
            ) {
                Text(
                    "Capo on fret $capoFret" + if (keyLabel.isNotBlank()) "  ·  $keyLabel" else "",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }

        // Translucent controls overlay.
        AnimatedVisibility(
            visible = controlsVisible && !ended,
            enter = fadeIn(Motion.effects(160)),
            exit = fadeOut(Motion.effects(220)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top gradient & bar.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)))
                        .safeDrawingPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Palette.Bone)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Palette.Bone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = listOfNotNull(
                            artist.takeIf { it.isNotBlank() },
                            capoLabel.takeIf { it.isNotBlank() },
                            keyLabel.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.Bone.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // Speed pills right in top bar for quick reach.
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (opt in SpeedOptions) {
                            val selected = speed == opt
                            Box(
                                Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) Palette.Brass else Color.Transparent)
                                    .clickable {
                                        speed = opt
                                        player.playbackParameters = PlaybackParameters(opt)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    if (opt == 1f) "1×" else "${opt}×",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Palette.Background else Palette.Bone,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    // Practiced today toggle.
                    Surface(
                        onClick = onTogglePracticed,
                        shape = CircleShape,
                        color = if (practicedToday) Palette.InTune else Palette.Surface.copy(alpha = 0.85f),
                        contentColor = if (practicedToday) Palette.Background else Palette.Bone,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (practicedToday) "Practiced" else "Mark done",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                // Center transport controls.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) },
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    ) {
                        Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Palette.Bone, modifier = Modifier.size(30.dp))
                    }
                    IconButton(
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(68.dp).clip(CircleShape).background(Palette.Brass),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Palette.Background,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            val max = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
                            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(max))
                        },
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                    ) {
                        Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Palette.Bone, modifier = Modifier.size(30.dp))
                    }
                }

                // Bottom bar: Section chips + Loop toggle + Scrubber.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))))
                        .safeDrawingPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (sections.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Loop toggle for the active/selected section.
                            val loopTarget = loopedSection ?: activeSection ?: sections.first()
                            val loopActive = loopedSection != null
                            Surface(
                                onClick = {
                                    loopedSection = if (loopActive) null else loopTarget
                                    if (!loopActive) {
                                        player.seekTo((loopTarget.startSec * 1000.0).toLong())
                                    }
                                },
                                shape = CircleShape,
                                color = if (loopActive) Palette.Brass else Palette.Surface.copy(alpha = 0.85f),
                                contentColor = if (loopActive) Palette.Background else Palette.Bone,
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Repeat, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        if (loopActive) "Loop: ${loopTarget.name}" else "Loop",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                }
                            }

                            Row(
                                Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                for (sec in sections) {
                                    val isCurrent = activeSection?.index == sec.index
                                    val isLooped = loopedSection?.index == sec.index
                                    Box(
                                        Modifier
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isLooped -> Palette.Brass
                                                    isCurrent -> Palette.Bone.copy(alpha = 0.25f)
                                                    else -> Color.Black.copy(alpha = 0.45f)
                                                },
                                            )
                                            .clickable {
                                                player.seekTo((sec.startSec * 1000.0).toLong())
                                                if (loopedSection != null) loopedSection = sec
                                            }
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            sec.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isLooped) Palette.Background else Palette.Bone,
                                            fontWeight = if (isCurrent || isLooped) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatTime(positionMs), style = MaterialTheme.typography.labelMedium, color = Palette.Bone)
                        Slider(
                            value = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                val target = (frac * durationMs).toLong()
                                positionMs = target
                                player.seekTo(target)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Palette.Brass,
                                activeTrackColor = Palette.Brass,
                                inactiveTrackColor = Palette.Bone.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium, color = Palette.Bone.copy(alpha = 0.78f))
                    }
                }
            }
        }

        // End-of-song completion card.
        if (ended) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.widthIn(max = 440.dp).padding(24.dp),
                ) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Overline("Play-along finished")
                        Spacer(Modifier.height(6.dp))
                        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = {
                                if (!practicedToday) onTogglePracticed()
                                onBack()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (practicedToday) Palette.InTune else Palette.Brass,
                                contentColor = Palette.Background,
                            ),
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (practicedToday) "Practiced today · Done" else "Mark practiced today")
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    ended = false
                                    player.seekTo(0L)
                                    player.play()
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Icon(Icons.Rounded.Replay, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Replay")
                            }
                            FilledTonalButton(
                                onClick = onPracticeChords,
                                modifier = Modifier.weight(1f).height(44.dp),
                            ) {
                                Text("Drill chords")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingVideoScreen(
    title: String,
    artist: String,
    expectedFilename: String,
    onPickFolder: () -> Unit,
    onPickFile: () -> Unit,
    onOpenYouTube: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Palette.Bone)
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Overline("Play-along video not on device yet")
                Spacer(Modifier.height(6.dp))
                Text(
                    if (artist.isNotBlank()) "$title — $artist" else title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (expectedFilename.isNotBlank()) {
                        "Copy your play-along videos onto your phone, then choose their folder once (looking for “$expectedFilename”)."
                    } else {
                        "Choose the folder where your play-along .mp4 videos live, or pick a video file for this song."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPickFolder,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Choose folder")
                    }
                    FilledTonalButton(
                        onClick = onPickFile,
                        modifier = Modifier.weight(1f).height(48.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Icon(Icons.Rounded.VideoFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pick video")
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenYouTube) {
                    Text("Open on YouTube instead")
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
