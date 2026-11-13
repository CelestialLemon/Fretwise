package com.celestiallemon.tuner.metronome

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TimeSignature(val label: String, val beats: Int) {
    TwoFour("2/4", 2),
    ThreeFour("3/4", 3),
    FourFour("4/4", 4),
    SixEight("6/8", 6);

    fun accent(index: Int) = when {
        index == 0 -> Accent.Strong
        this == SixEight && index == 3 -> Accent.Medium
        else -> Accent.Normal
    }
}

const val MinBpm = 30
const val MaxBpm = 250

data class MetronomeUiState(
    val bpm: Int = 80,
    val signature: TimeSignature = TimeSignature.FourFour,
    val playing: Boolean = false,
    /** Title of the song whose tempo was loaded, until the tempo is changed by hand. */
    val songTitle: String? = null,
)

/** Italian tempo marking for a BPM, shown under the number. */
fun tempoName(bpm: Int) = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 176 -> "Vivace"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

/**
 * Turns taps into a tempo: the average of the last few intervals. A pause longer than [resetAfterMs]
 * starts a new count.
 */
class TapTempo(private val resetAfterMs: Long = 2_000, private val window: Int = 4) {
    private val taps = ArrayDeque<Long>()

    /** Records a tap at [timeMs] and returns the tempo, or null until there are two taps. */
    fun tap(timeMs: Long): Int? {
        if (taps.isNotEmpty() && timeMs - taps.last() > resetAfterMs) taps.clear()
        taps.addLast(timeMs)
        while (taps.size > window + 1) taps.removeFirst()
        if (taps.size < 2) return null
        val average = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
        return (60_000 / average).toInt().coerceIn(MinBpm, MaxBpm)
    }
}

class MetronomeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("metronome", Context.MODE_PRIVATE)
    val engine = MetronomeEngine()
    private val tapTempo = TapTempo()

    private val _state = MutableStateFlow(
        MetronomeUiState(
            bpm = prefs.getInt("bpm", 80).coerceIn(MinBpm, MaxBpm),
            signature = TimeSignature.entries.firstOrNull { it.name == prefs.getString("signature", null) } ?: TimeSignature.FourFour,
        ),
    )
    val state: StateFlow<MetronomeUiState> = _state.asStateFlow()

    init {
        engine.bpm = _state.value.bpm
        engine.signature = _state.value.signature
        engine.onFailure = { _state.update { it.copy(playing = false) } }
    }

    fun toggle() = if (_state.value.playing) stop() else play()

    fun play() {
        // Mark playing first so a failure reported straight away by the engine isn't overwritten.
        _state.update { it.copy(playing = true) }
        engine.start()
    }

    fun stop() {
        if (!_state.value.playing) return
        engine.stop()
        _state.update { it.copy(playing = false) }
    }

    fun setBpm(bpm: Int) = applyBpm(bpm, songTitle = null)

    fun nudge(delta: Int) = setBpm(_state.value.bpm + delta)

    fun tap(timeMs: Long) {
        tapTempo.tap(timeMs)?.let(::setBpm)
    }

    /** Loads a song's tempo without starting playback. */
    fun loadSong(title: String, bpm: Int) = applyBpm(bpm, songTitle = title)

    fun setSignature(signature: TimeSignature) {
        engine.signature = signature
        _state.update { it.copy(signature = signature) }
        prefs.edit { putString("signature", signature.name) }
    }

    private fun applyBpm(bpm: Int, songTitle: String?) {
        val value = bpm.coerceIn(MinBpm, MaxBpm)
        engine.bpm = value
        _state.update { it.copy(bpm = value, songTitle = songTitle) }
        prefs.edit { putInt("bpm", value) }
    }

    override fun onCleared() = engine.stop()
}
