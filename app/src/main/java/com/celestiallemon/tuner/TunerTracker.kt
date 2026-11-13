package com.celestiallemon.tuner

import com.celestiallemon.tuner.audio.Frame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
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
    private var recentLevelDb = -120.0

    /** Pitches waiting to prove they're steady (a string) rather than gliding (a voice). */
    private val pending = ArrayDeque<Double>()

    fun process(frame: Frame): Reading {
        updateNoiseFloor(frame.levelDb)
        val gate = noiseFloorDb + if (tracking) GateCloseDb else GateOpenDb
        level = (((frame.levelDb - gate) / 30.0).coerceIn(0.0, 1.0)).toFloat()

        // A sudden rise in loudness is a new pluck: start over so the new note shows straight away.
        val onset = frame.levelDb > recentLevelDb + OnsetDb && frame.levelDb > gate
        recentLevelDb = if (frame.levelDb > recentLevelDb) frame.levelDb else recentLevelDb + (frame.levelDb - recentLevelDb) * 0.25
        if (onset && smoothed != null) resetPitch()

        val pitch = frame.pitch
        val minClarity = if (tracking) HoldClarity else StartClarity
        val cents = pitch?.let { fold(centsFromA4(it.frequency, a4)) }
        val valid = pitch != null && cents != null && frame.levelDb > max(gate, AbsoluteGateDb) &&
            pitch.clarity >= minClarity && plausible(cents)

        if (valid) {
            val hz = a4 * 2.0.pow(cents!! / 1200)
            if (smoothed == null) confirm(cents, hz, frame.seconds) else accept(cents, hz, frame.seconds)
        } else {
            pending.clear()
            silentSeconds += frame.seconds
            if (silentSeconds > HoldSeconds) {
                tracking = false
                resetPitch()
            }
        }
        return current()
    }

    /**
     * Fixes octave slips using the tuning. Phone mics barely pick up the fundamental of the low strings, so the
     * detector sometimes reports a string's 2nd harmonic (D3 heard as D4), or, as a note fades, an octave below.
     * In standard tuning a slipped reading lands ~200+ cents from every string while the octave-corrected one lands
     * right on a string, so: if a reading is far from every string but an octave away is close to one, take that.
     * With a string picked by hand, readings an octave either side of it are folded onto it.
     */
    private fun fold(cents: Double): Double {
        val strings = tuning.strings
        if (strings.isEmpty()) return cents
        lockedString?.let { locked ->
            val target = strings[locked].cents
            return when {
                abs(cents - 1200 - target) < 100 -> cents - 1200
                abs(cents + 1200 - target) < 100 -> cents + 1200
                else -> cents
            }
        }
        fun distance(c: Double) = strings.minOf { abs(it.cents - c) }
        if (distance(cents) < FoldFarCents) return cents
        return when {
            distance(cents - 1200) <= FoldNearCents -> cents - 1200
            distance(cents + 1200) <= FoldNearCents -> cents + 1200
            else -> cents
        }
    }

    /**
     * In auto mode, ignores pitches nowhere near any string of the tuning (hum, whistles, a voice an octave away).
     * A string picked by hand or chromatic mode accepts anything the detector finds.
     */
    private fun plausible(cents: Double): Boolean {
        val strings = tuning.strings
        if (strings.isEmpty() || lockedString != null) return true
        return strings.any { abs(it.cents - cents) <= PlausibleCents }
    }

    /** Starts tracking only once a few frames in a row agree, which a plucked string does and speech rarely does. */
    private fun confirm(cents: Double, frequency: Double, seconds: Double) {
        pending.addLast(cents)
        if (pending.size > ConfirmFrames) pending.removeFirst()
        if (pending.size < ConfirmFrames || pending.max() - pending.min() > SteadyCents) return
        history.clear()
        history.addAll(pending)
        pending.clear()
        smoothed = median()
        silentSeconds = 0.0
        jumpFrames = 0
        tracking = true
        lastFrequency = frequency
        chooseTarget(fresh = true)
        trackInTune(seconds)
    }

    private fun updateNoiseFloor(levelDb: Double) {
        // Falls fast, rises slowly (~1.5 dB/s), so a sustained note barely moves it but a noisier room is learned.
        noiseFloorDb = if (levelDb < noiseFloorDb) noiseFloorDb + (levelDb - noiseFloorDb) * 0.3 else noiseFloorDb + 0.03
        noiseFloorDb = noiseFloorDb.coerceIn(-95.0, -40.0)
    }

    private fun accept(measured: Double, frequency: Double, seconds: Double) {
        // A ringing string doesn't change octave; a reading exactly an octave off is the detector slipping.
        var cents = measured
        var hz = frequency
        val diff = cents - median()
        if (abs(diff - 1200) < OctaveSlipCents) { cents -= 1200; hz /= 2 }
        if (abs(diff + 1200) < OctaveSlipCents) { cents += 1200; hz *= 2 }
        if (history.isNotEmpty() && abs(cents - median()) > JumpCents) {
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
        lastFrequency = hz
        if (fresh) {
            // A real new note after a jump still has to prove itself steady.
            history.clear()
            smoothed = null
            confirm(cents, hz, seconds)
            return
        }

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
        pending.clear()
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
        /** Tuned on recordings from a Pixel 7a across a room: strings land only 10-15 dB above the noise. */
        private const val GateOpenDb = 6.0
        private const val GateCloseDb = 3.0
        private const val AbsoluteGateDb = -80.0
        private const val StartClarity = 0.72
        private const val HoldClarity = 0.6
        private const val ConfirmFrames = 4
        private const val SteadyCents = 30.0
        private const val OnsetDb = 8.0
        private const val OctaveSlipCents = 60.0
        private const val PlausibleCents = 400.0
        private const val FoldFarCents = 150.0
        private const val FoldNearCents = 50.0
    }
}
