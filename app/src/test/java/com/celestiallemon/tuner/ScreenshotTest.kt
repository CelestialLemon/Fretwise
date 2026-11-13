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
import com.celestiallemon.tuner.ui.TunerTheme
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
                TunerTheme {
                    Box(Modifier.fillMaxSize().background(Palette.Background).padding(top = 32.dp, bottom = 16.dp)) { content() }
                }
            }
        }
    }


    private val guitar = TunerUiState()

    private fun tuner(name: String, state: TunerUiState) = snap(name) {
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
    fun permission() = snap("8-permission") { PermissionScreen(false, {}, {}) }
}
