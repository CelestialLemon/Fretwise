package com.celestiallemon.tuner

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.celestiallemon.tuner.ui.LocalStillFrames
import com.celestiallemon.tuner.ui.Palette
import com.celestiallemon.tuner.ui.PermissionScreen
import com.celestiallemon.tuner.ui.TunerScreen
import com.celestiallemon.tuner.metronome.MetronomeUiState
import com.celestiallemon.tuner.metronome.TimeSignature
import com.celestiallemon.tuner.repertoire.Level
import com.celestiallemon.tuner.repertoire.RepertoireUiState
import com.celestiallemon.tuner.repertoire.Song
import com.celestiallemon.tuner.repertoire.planDay
import com.celestiallemon.tuner.ui.AppShell
import com.celestiallemon.tuner.ui.FretwiseTheme
import com.celestiallemon.tuner.ui.MetronomeScreen
import com.celestiallemon.tuner.ui.RepertoireActions
import com.celestiallemon.tuner.ui.RepertoireScreen
import com.celestiallemon.tuner.ui.Tab
import java.time.LocalDate
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the screen in sample states into `docs/screenshots/` (`./gradlew recordRoborazziDebug`). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [36], qualifiers = "w392dp-h850dp-xxhdpi")
class ScreenshotTest {
    private fun snap(name: String, content: @Composable () -> Unit) {
        captureRoboImage("../docs/screenshots/$name.png") {
            CompositionLocalProvider(LocalStillFrames provides true) {
                FretwiseTheme {
                    Box(Modifier.fillMaxSize().background(Palette.Background).padding(top = 32.dp)) { content() }
                }
            }
        }
    }


    private val guitar = TunerUiState()

    private fun shell(name: String, tab: Tab, playing: Boolean = false, left: Int = 2, content: @Composable () -> Unit) = snap(name) {
        AppShell(tab = tab, onTab = {}, metronomePlaying = playing, songsLeftToday = left) { content() }
    }

    private fun tuner(name: String, state: TunerUiState) = shell(name, Tab.Tuner) {
        TunerScreen(state, onSelectTuning = { _, _ -> }, onA4Change = {}, onLockString = {})
    }

    @Test
    fun idle() = tuner("1-idle", guitar)

    @Test
    fun flat() = tuner(
        "2-flat",
        guitar.copy(
            reading = Reading(
                active = true, frequency = 107.6, stringIndex = 1, target = Note.parse("A2"),
                cents = -38.0, level = 0.7f, tuned = setOf(0),
            ),
        ),
    )

    @Test
    fun inTune() = tuner(
        "3-in-tune",
        guitar.copy(
            reading = Reading(
                active = true, frequency = 196.1, stringIndex = 3, target = Note.parse("G3"),
                cents = 1.0, inTune = true, level = 0.5f, tuned = setOf(0, 1, 2, 3),
            ),
        ),
    )

    @Test
    fun sharpManualDropD() {
        val dropD = Instruments.Guitar.tunings[1]
        tuner(
            "4-manual-sharp",
            guitar.copy(
                tuning = dropD, auto = false, a4 = 442.0,
                reading = Reading(active = true, frequency = 75.0, stringIndex = 0, target = dropD.strings[0], cents = 14.0, level = 0.4f),
            ),
        )
    }

    @Test
    fun bass() = tuner(
        "5-bass",
        TunerUiState(instrument = Instruments.Bass, tuning = Instruments.Bass.tunings.first()),
    )

    @Test
    fun chromatic() = tuner(
        "6-chromatic",
        TunerUiState(
            instrument = Instruments.Chromatic, tuning = Instruments.Chromatic.tunings.first(),
            reading = Reading(active = true, frequency = 551.0, target = Note.parse("C#5"), cents = -22.0),
        ),
    )

    @Test
    fun sevenString() = tuner(
        "7-seven-string",
        TunerUiState(instrument = Instruments.SevenString, tuning = Instruments.SevenString.tunings.first(),
            reading = Reading(stringIndex = 0, target = Note.parse("B1"), tuned = setOf(1, 2, 3, 4, 5, 6))),
    )

    @Test
    fun metronome() = shell("9-metronome", Tab.Metronome) {
        MetronomeScreen(
            MetronomeUiState(bpm = 92, signature = TimeSignature.SixEight, songTitle = "Blackbird"),
            engine = null, onToggle = {}, onBpm = {}, onNudge = {}, onTap = {}, onSignature = {},
        )
    }

    private val today = LocalDate.of(2026, 11, 13)

    private fun sample(id: String, title: String, artist: String, level: Level, bpm: Int?, vararg practiced: String) =
        Song(id, title, artist, bpm, level = level, levelSince = id.hashCode().toLong(), addedAt = 0, practiced = practiced.toList())

    @Test
    fun repertoire() {
        val songs = listOf(
            sample("1", "Blackbird", "The Beatles", Level.L1, 92),
            sample("2", "Tears in Heaven", "Eric Clapton", Level.L1, 80, "2026-11-10"),
            sample("3", "Dust in the Wind", "Kansas", Level.L1, null, "2026-11-12"),
            sample("4", "Romanza", "Anonymous", Level.L1, 60, "2026-11-11"),
            sample("5", "Wish You Were Here", "Pink Floyd", Level.L2, 60, "2026-11-08", "2026-11-13"),
            sample("6", "Hotel California", "Eagles", Level.L3, 74, "2026-11-09"),
            sample("7", "Wonderwall", "Oasis", Level.Memory, 87, "2026-10-01"),
            sample("8", "Horse with No Name", "America", Level.Memory, 123, "2026-11-01"),
        )
        val state = RepertoireUiState(loaded = true, songs = songs, today = today, plan = planDay(today, songs, null))
        shell("10-repertoire", Tab.Repertoire, left = state.todaySongs.size - state.doneToday) {
            RepertoireScreen(state, RepertoireActions())
        }
    }

    @Test
    fun repertoireEmpty() = shell("11-repertoire-empty", Tab.Repertoire, left = 0) {
        RepertoireScreen(RepertoireUiState(loaded = true, today = today), RepertoireActions())
    }

    @Test
    fun permission() = snap("8-permission") { PermissionScreen(false, {}, {}) }
}
