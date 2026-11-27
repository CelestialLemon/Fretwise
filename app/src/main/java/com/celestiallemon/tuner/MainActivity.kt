package com.celestiallemon.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celestiallemon.tuner.metronome.MetronomeViewModel
import com.celestiallemon.tuner.repertoire.RepertoireViewModel
import com.celestiallemon.tuner.repertoire.Song
import com.celestiallemon.tuner.ui.AppShell
import com.celestiallemon.tuner.ui.ChordPracticeScreen
import com.celestiallemon.tuner.ui.FretwiseTheme
import com.celestiallemon.tuner.ui.MetronomeScreen
import com.celestiallemon.tuner.ui.Motion
import com.celestiallemon.tuner.ui.PermissionScreen
import com.celestiallemon.tuner.ui.RepertoireActions
import com.celestiallemon.tuner.ui.RepertoireScreen
import com.celestiallemon.tuner.ui.Tab
import com.celestiallemon.tuner.ui.TunerScreen

private sealed interface PracticeRoute {
    data object Home : PracticeRoute
    data class Chords(val songId: String?) : PracticeRoute
    data object Metronome : PracticeRoute
}

class MainActivity : ComponentActivity() {
    private val tuner: TunerViewModel by viewModels()
    private val metronome: MetronomeViewModel by viewModels()
    private val repertoire: RepertoireViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("capture", false)) tuner.capture(java.io.File(getExternalFilesDir(null), "capture.f32"))
        val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
        val rawTab = intent.getStringExtra("tab") ?: prefs.getString("tab", null)
        val startTab = when {
            rawTab.equals("Practice", true) || rawTab.equals("Repertoire", true) || rawTab.equals("Metronome", true) -> Tab.Practice
            else -> Tab.Tuner
        }
        val initialRoute: PracticeRoute = if (rawTab.equals("Metronome", true)) PracticeRoute.Metronome else PracticeRoute.Home

        setContent {
            FretwiseTheme {
                var tab by remember { mutableStateOf(startTab) }
                var practiceRoute by remember { mutableStateOf(initialRoute) }
                val metronomeState by metronome.state.collectAsStateWithLifecycle()
                val repertoireState by repertoire.state.collectAsStateWithLifecycle()

                val playAlongLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val data = result.data ?: return@rememberLauncherForActivityResult
                        val songId = data.getStringExtra(PlayAlongActivity.RESULT_SONG_ID) ?: return@rememberLauncherForActivityResult
                        if (data.getBooleanExtra(PlayAlongActivity.RESULT_PRACTICED_CHANGED, false)) {
                            val done = data.getBooleanExtra(PlayAlongActivity.RESULT_PRACTICED_TODAY, false)
                            val dateStr = data.getStringExtra(PlayAlongActivity.RESULT_PRACTICED_DATE)
                            val day = runCatching { dateStr?.let(java.time.LocalDate::parse) }.getOrNull() ?: repertoireState.today
                            repertoire.setPracticedOn(songId, day, done)
                        }
                        val linkedUri = data.getStringExtra(PlayAlongActivity.RESULT_VIDEO_URI)
                        if (linkedUri != null) {
                            repertoire.setVideoUri(songId, linkedUri.toUri())
                        } else if (data.getBooleanExtra(PlayAlongActivity.RESULT_CLEAR_VIDEO_URI, false)) {
                            repertoire.clearVideoUri(songId)
                        }
                        repertoire.refreshVideoFolder()
                        if (data.getBooleanExtra(PlayAlongActivity.RESULT_OPEN_CHORDS, false)) {
                            tab = Tab.Practice
                            practiceRoute = PracticeRoute.Chords(songId)
                        }
                    }
                }

                fun launchPlayAlong(song: Song) {
                    metronome.stop()
                    val practiced = song.practicedOn(repertoireState.today)
                    playAlongLauncher.launch(
                        PlayAlongActivity.createIntent(this@MainActivity, song, practiced, repertoireState.today.toString()),
                    )
                }

                // The clicks would confuse the pitch detector, so the metronome stops when the tuner opens,
                // and it stops whenever the app leaves the screen.
                LaunchedEffect(tab) {
                    if (tab == Tab.Tuner) metronome.stop()
                    prefs.edit { putString("tab", tab.name) }
                }
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { metronome.stop() }
                LifecycleResumeEffect(Unit) {
                    repertoire.refreshDay()
                    onPauseOrDispose { }
                }

                BackHandler(enabled = tab == Tab.Practice && practiceRoute != PracticeRoute.Home) {
                    practiceRoute = PracticeRoute.Home
                }

                AppShell(
                    tab = tab,
                    onTab = { next ->
                        if (next == Tab.Practice && tab == Tab.Practice) {
                            practiceRoute = PracticeRoute.Home
                        } else {
                            tab = next
                        }
                    },
                    metronomePlaying = metronomeState.playing,
                    songsLeftToday = repertoireState.todaySongs.size - repertoireState.doneToday,
                ) { shown ->
                    when (shown) {
                        Tab.Tuner -> TunerTab(onStartPractice = {
                            tab = Tab.Practice
                            practiceRoute = PracticeRoute.Home
                        })
                        Tab.Practice -> AnimatedContent(
                            targetState = practiceRoute,
                            transitionSpec = { fadeIn(Motion.effects(180)) togetherWith fadeOut(Motion.effects(120)) },
                            label = "practiceRoute",
                        ) { route ->
                            when (route) {
                                PracticeRoute.Home -> RepertoireScreen(
                                    state = repertoireState,
                                    messages = repertoire.messages,
                                    metronomeBpm = metronomeState.bpm,
                                    metronomeSignature = metronomeState.signature.label,
                                    metronomePlaying = metronomeState.playing,
                                    actions = RepertoireActions(
                                        add = { repertoire.add(it) },
                                        edit = repertoire::edit,
                                        delete = repertoire::delete,
                                        move = repertoire::move,
                                        toggleDone = repertoire::toggleDone,
                                        swap = repertoire::swap,
                                        export = repertoire::export,
                                        readImport = repertoire::readImport,
                                        confirmImport = repertoire::confirmImport,
                                        cancelImport = repertoire::cancelImport,
                                        openMetronome = { song ->
                                            if (song?.bpm != null) {
                                                val sig = repertoireState.chartFor(song)?.timeSignature()
                                                metronome.loadSong(song.title, song.bpm, sig)
                                            }
                                            practiceRoute = PracticeRoute.Metronome
                                        },
                                        openChords = { song ->
                                            metronome.stop()
                                            practiceRoute = PracticeRoute.Chords(song?.id)
                                        },
                                        openPlayAlong = ::launchPlayAlong,
                                        setVideoFolder = repertoire::setVideoFolder,
                                        importBundled = repertoire::importBundledCharts,
                                    ),
                                )
                                is PracticeRoute.Chords -> ChordPracticeScreen(
                                    songs = repertoireState.songs,
                                    charts = repertoireState.charts,
                                    initialSongId = route.songId,
                                    engine = metronome.engine,
                                    metronomePlaying = metronomeState.playing,
                                    onStartMetronome = { bpm, sig ->
                                        metronome.setSignature(sig)
                                        metronome.setBpm(bpm)
                                        metronome.play()
                                    },
                                    onStopMetronome = metronome::stop,
                                    onBack = { practiceRoute = PracticeRoute.Home },
                                    onPlaySong = ::launchPlayAlong,
                                )
                                PracticeRoute.Metronome -> MetronomeScreen(
                                    state = metronomeState,
                                    engine = metronome.engine,
                                    onToggle = metronome::toggle,
                                    onBpm = metronome::setBpm,
                                    onNudge = metronome::nudge,
                                    onTap = metronome::tap,
                                    onSignature = metronome::setSignature,
                                    onBack = { practiceRoute = PracticeRoute.Home },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** The tuner, with the microphone permission flow. The mic only runs while this tab is on screen. */
    @SuppressLint("MissingPermission") // start() is only called once the permission is granted.
    @Composable
    private fun TunerTab(onStartPractice: () -> Unit) {
        var granted by remember { mutableStateOf(hasMicPermission()) }
        var askedOnce by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            askedOnce = true
        }
        if (granted) {
            LifecycleResumeEffect(Unit) {
                tuner.start()
                onPauseOrDispose { tuner.stop() }
            }
            val state by tuner.state.collectAsStateWithLifecycle()
            TunerScreen(
                state = state,
                onSelectTuning = tuner::selectTuning,
                onA4Change = tuner::setA4,
                onLockString = tuner::lockString,
                onStartPractice = onStartPractice,
            )
        } else {
            // Re-check on resume, in case it was granted from Settings.
            LifecycleResumeEffect(Unit) {
                granted = hasMicPermission()
                onPauseOrDispose { }
            }
            LaunchedEffect(Unit) { if (!askedOnce) launcher.launch(Manifest.permission.RECORD_AUDIO) }
            PermissionScreen(
                permanentlyDenied = askedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO),
                onAllow = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                onOpenSettings = {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                },
            )
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
