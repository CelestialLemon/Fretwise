package com.celestiallemon.tuner

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import com.celestiallemon.tuner.audio.AudioInput
import com.celestiallemon.tuner.audio.PitchDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TunerUiState(
    val instrument: Instrument = Instruments.Guitar,
    val tuning: Tuning = Instruments.Guitar.tunings.first(),
    val a4: Double = 440.0,
    /** True when the app picks the string from what it hears. */
    val auto: Boolean = true,
    val reading: Reading = Reading(),
    val error: String? = null,
)

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("tuner", Context.MODE_PRIVATE)
    private val audio = AudioInput(application)

    private val _state = MutableStateFlow(loadSettings())
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    private val tracker = TunerTracker(_state.value.tuning, _state.value.a4)

    init {
        configureDetector()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        _state.update { it.copy(error = null) }
        audio.start(
            onFrame = { frame ->
                val reading = synchronized(tracker) { tracker.process(frame) }
                _state.update { it.copy(reading = reading) }
            },
            onError = { message -> _state.update { it.copy(error = message) } },
        )
    }

    fun stop() = audio.stop()

    fun selectTuning(instrument: Instrument, tuning: Tuning) {
        val instrumentChanged = instrument != _state.value.instrument
        synchronized(tracker) { tracker.tuning = tuning }
        _state.update { it.copy(instrument = instrument, tuning = tuning, auto = true, reading = Reading()) }
        if (instrumentChanged) configureDetector()
        saveSettings()
    }

    fun setA4(a4: Double) {
        val value = a4.coerceIn(415.0, 466.0)
        synchronized(tracker) { tracker.a4 = value }
        _state.update { it.copy(a4 = value) }
        configureDetector()
        saveSettings()
    }

    /** Pins the tuner to one string, or with null goes back to detecting the string automatically. */
    fun lockString(index: Int?) {
        val reading = synchronized(tracker) {
            tracker.lockedString = index
            tracker.current()
        }
        _state.update { it.copy(auto = index == null, reading = reading) }
    }

    private fun configureDetector() {
        val range = _state.value.instrument.searchRange(_state.value.a4)
        audio.setDetector { sampleRate -> PitchDetector(sampleRate, range.start, range.endInclusive) }
    }

    private fun loadSettings(): TunerUiState {
        val instrument = Instruments.find(prefs.getString("instrument", null))
        val tuning = instrument.tunings.firstOrNull { it.id == prefs.getString("tuning", null) } ?: instrument.tunings.first()
        val a4 = prefs.getFloat("a4", 440f).toDouble()
        return TunerUiState(instrument = instrument, tuning = tuning, a4 = a4)
    }

    private fun saveSettings() {
        val s = _state.value
        prefs.edit {
            putString("instrument", s.instrument.id)
            putString("tuning", s.tuning.id)
            putFloat("a4", s.a4.toFloat())
        }
    }

    override fun onCleared() {
        audio.stop()
    }
}
