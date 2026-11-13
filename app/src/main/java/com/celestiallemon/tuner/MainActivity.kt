package com.celestiallemon.tuner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.celestiallemon.tuner.metronome.MetronomeViewModel
import com.celestiallemon.tuner.repertoire.RepertoireViewModel
import com.celestiallemon.tuner.ui.AppShell
import com.celestiallemon.tuner.ui.FretwiseTheme
import com.celestiallemon.tuner.ui.MetronomeScreen
import com.celestiallemon.tuner.ui.PermissionScreen
import com.celestiallemon.tuner.ui.RepertoireActions
import com.celestiallemon.tuner.ui.RepertoireScreen
import com.celestiallemon.tuner.ui.Tab
import com.celestiallemon.tuner.ui.TunerScreen

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
        val startTab = intent.getStringExtra("tab")?.let { name -> Tab.entries.firstOrNull { it.name.equals(name, true) } }
            ?: Tab.entries.firstOrNull { it.name == prefs.getString("tab", null) }
            ?: Tab.Tuner

        setContent {
            FretwiseTheme {
                var tab by remember { mutableStateOf(startTab) }
                val metronomeState by metronome.state.collectAsStateWithLifecycle()
                val repertoireState by repertoire.state.collectAsStateWithLifecycle()

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

                AppShell(
                    tab = tab,
                    onTab = { tab = it },
                    metronomePlaying = metronomeState.playing,
                    songsLeftToday = repertoireState.todaySongs.size - repertoireState.doneToday,
                ) { shown ->
                    when (shown) {
                        Tab.Tuner -> TunerTab()
                        Tab.Metronome -> MetronomeScreen(
                            state = metronomeState,
                            engine = metronome.engine,
                            onToggle = metronome::toggle,
                            onBpm = metronome::setBpm,
                            onNudge = metronome::nudge,
                            onTap = metronome::tap,
                            onSignature = metronome::setSignature,
                        )
                        Tab.Repertoire -> RepertoireScreen(
                            state = repertoireState,
                            messages = repertoire.messages,
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
                                    song.bpm?.let { metronome.loadSong(song.title, it) }
                                    tab = Tab.Metronome
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    /** The tuner, with the microphone permission flow. The mic only runs while this tab is on screen. */
    @SuppressLint("MissingPermission") // start() is only called once the permission is granted.
    @Composable
    private fun TunerTab() {
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
