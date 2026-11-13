package com.celestiallemon.tuner

import com.celestiallemon.tuner.audio.Frame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** What the screen shows. */
data class Reading(
    /** A string or note is sounding now (or was within the hold time). */
    val active: Boolean = false,
    val frequency: Double? = null,
    /** The string being tuned, as an index into the tuning, or null in chromatic mode / before any string is heard. */
    val stringIndex: Int? = null,
    /** The note being tuned towards. */
    val target: Note? = null,
    /** How far off the target, in cents (positive = sharp). */
    val cents: Double = 0.0,
    val inTune: Boolean = false,
    /** Strings that have held in tune long enough to count as done. */
    val tuned: Set<Int> = emptySet(),
    /** Loudness of the sound, 0..1, used to animate the string. */
    val level: Float = 0f,
    /** Bumped every time a string becomes tuned, to trigger haptics once. */
    val tunedEvent: Int = 0,
)

/**
 * Turns noisy per-hop pitch estimates into a steady reading:
 * - an adaptive noise gate, so room noise and fret buzz don't wiggle the needle;
 * - a median filter that drops one-off octave glitches as a note decays, then light exponential smoothing;
 * - automatic string choice with hysteresis, so a badly detuned string doesn't flicker between neighbours;
 * - "tuned" checkmarks that need the string to stay in tune for a moment, not just pass through.
 * Not thread-safe; the caller serialises access.
 */
class TunerTracker(tuning: Tuning, a4: Double) {
    var tuning: Tuning = tuning
        set(value) {
            if (value != field) {
                field = value
                lockedString = null
                currentString = null
                tuned.clear()
                resetPitch()
            }
        }

    var a4: Double = a4
        set(value) {
            if (value != field) {
                field = value
                tuned.clear()
                resetPitch()
            }
        }

    /** A string picked by hand; null means pick automatically. */
    var lockedString: Int? = null
        set(value) {
            field = value
            if (value != null) currentString = value
            inTuneSeconds = 0.0
        }

    private val history = ArrayDeque<Double>()
    private var smoothed: Double? = null
    private var jumpFrames = 0
    private var silentSeconds = Double.MAX_VALUE
    private var noiseFloorDb = -70.0
    private var tracking = false

    private var currentString: Int? = null
    private var candidateString: Int? = null
    private var candidateFrames = 0
    private var chromaticNote: Int? = null

    private var inTuneSeconds = 0.0
    private var offSeconds = 0.0
    private val tuned = mutableSetOf<Int>()
    private var tunedEvent = 0
    private var lastFrequency: Double? = null
    private var level = 0f

    fun process(frame: Frame): Reading {
        updateNoiseFloor(frame.levelDb)
        val gate = noiseFloorDb + if (tracking) GateCloseDb else GateOpenDb
        val pitch = frame.pitch
        val minClarity = if (tracking) 0.72 else 0.86
        val valid = pitch != null && frame.levelDb > max(gate, AbsoluteGateDb) && pitch.clarity >= minClarity
        level = (((frame.levelDb - gate) / 35.0).coerceIn(0.0, 1.0)).toFloat()

        if (valid) {
            accept(centsFromA4(pitch.frequency, a4), pitch.frequency, frame.seconds)
        } else {
            silentSeconds += frame.seconds
            if (silentSeconds > HoldSeconds) {
                tracking = false
                resetPitch()
            }
        }
        return current()
    }

    private fun updateNoiseFloor(levelDb: Double) {
        // Falls fast, rises slowly (~1.5 dB/s), so a sustained note barely moves it but a noisier room is learned.
        noiseFloorDb = if (levelDb < noiseFloorDb) noiseFloorDb + (levelDb - noiseFloorDb) * 0.3 else noiseFloorDb + 0.03
        noiseFloorDb = noiseFloorDb.coerceIn(-95.0, -40.0)
    }

    private fun accept(cents: Double, frequency: Double, seconds: Double) {
        val reference = smoothed
        if (reference != null && history.isNotEmpty() && abs(cents - median()) > JumpCents) {
            // A big jump is either a glitch (ignore it) or a new note (accept it once it persists).
            jumpFrames++
            if (jumpFrames < JumpConfirmFrames) {
                silentSeconds += seconds
                return
            }
            resetPitch()
        }
        jumpFrames = 0
        silentSeconds = 0.0
        tracking = true

        history.addLast(cents)
        if (history.size > MedianSize) history.removeFirst()
        val median = median()
        val fresh = smoothed == null
        smoothed = smoothed?.let { it + Smoothing * (median - it) } ?: median
        lastFrequency = frequency

        chooseTarget(fresh)
        trackInTune(seconds)
    }

    private fun chooseTarget(fresh: Boolean) {
        val pitch = smoothed ?: return
        val strings = tuning.strings
        if (strings.isEmpty()) {
            val nearest = (pitch / 100).roundToInt() + 69
            val current = chromaticNote
            // Keep the current note until we're clearly past the halfway point to the next one.
            if (fresh || current == null || abs(pitch - (current - 69) * 100.0) > 58) {
                if (current != nearest) inTuneSeconds = 0.0
                chromaticNote = nearest
            }
            return
        }
        lockedString?.let { currentString = it; return }

        val nearest = strings.indices.minBy { abs(pitch - strings[it].cents) }
        val current = currentString
        if (fresh || current == null) {
            switchTo(nearest)
            return
        }
        if (nearest == current) {
            candidateFrames = 0
            return
        }
        // Only switch once another string is clearly closer for a few frames.
        val margin = abs(pitch - strings[current].cents) - abs(pitch - strings[nearest].cents)
        if (margin > SwitchMarginCents && nearest == candidateString) candidateFrames++ else candidateFrames = 1
        candidateString = nearest
        if (candidateFrames >= SwitchFrames) switchTo(nearest)
    }

    private fun switchTo(index: Int) {
        if (currentString != index) inTuneSeconds = 0.0
        currentString = index
        candidateString = null
        candidateFrames = 0
    }

    private fun trackInTune(seconds: Double) {
        val cents = centsOff() ?: return
        val string = if (tuning.strings.isEmpty()) null else currentString
        if (abs(cents) <= InTuneCents) {
            inTuneSeconds += seconds
            offSeconds = 0.0
            if (string != null && inTuneSeconds >= ConfirmSeconds && tuned.add(string)) tunedEvent++
            if (string == null && inTuneSeconds >= ConfirmSeconds && inTuneSeconds - seconds < ConfirmSeconds) tunedEvent++
        } else {
            inTuneSeconds = 0.0
            if (abs(cents) > DetunedCents) {
                offSeconds += seconds
                if (string != null && offSeconds > ConfirmSeconds) tuned.remove(string)
            }
        }
    }

    private fun target(): Note? =
        if (tuning.strings.isEmpty()) chromaticNote?.let { Note(it) } else currentString?.let { tuning.strings[it] }

    private fun centsOff(): Double? {
        val pitch = smoothed ?: return null
        return pitch - (target() ?: return null).cents
    }

    /** The reading as of the last frame, e.g. after changing the locked string. */
    fun current(): Reading {
        val cents = centsOff()
        val active = cents != null
        return Reading(
            active = active,
            frequency = if (active) lastFrequency else null,
            stringIndex = if (tuning.strings.isEmpty()) null else currentString,
            target = target(),
            cents = cents ?: 0.0,
            inTune = cents != null && abs(cents) <= InTuneCents,
            tuned = tuned.toSet(),
            level = if (active) level else 0f,
            tunedEvent = tunedEvent,
        )
    }

    private fun median(): Double {
        val sorted = history.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun resetPitch() {
        history.clear()
        smoothed = null
        jumpFrames = 0
        inTuneSeconds = 0.0
        lastFrequency = null
    }

    companion object {
        /** Within this many cents counts as in tune. Most ears can't hear under ~5 cents on a single string. */
        const val InTuneCents = 3.0

        /** A tuned string loses its checkmark once it drifts this far for a moment. */
        const val DetunedCents = 10.0

        private const val ConfirmSeconds = 0.3
        private const val HoldSeconds = 1.2
        private const val MedianSize = 5
        private const val Smoothing = 0.35
        private const val JumpCents = 70.0
        private const val JumpConfirmFrames = 3
        private const val SwitchMarginCents = 25.0
        private const val SwitchFrames = 4
        private const val GateOpenDb = 14.0
        private const val GateCloseDb = 7.0
        private const val AbsoluteGateDb = -68.0
    }
}
