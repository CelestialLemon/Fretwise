package com.celestiallemon.tuner

import com.celestiallemon.tuner.audio.PitchDetector
import com.celestiallemon.tuner.audio.Preprocessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val Rate = 48_000

/**
 * A plucked string built from its harmonics: amplitudes falling off like a pluck near the bridge, upper harmonics
 * decaying faster, random phases, optional stiffness (inharmonicity) and a high-pass to mimic a phone mic that
 * barely picks up the fundamental of low strings.
 */
fun pluck(
    f0: Double,
    seconds: Double,
    random: Random,
    inharmonicity: Double = 0.0,
    fundamentalGain: Double = 1.0,
    noise: Double = 0.003,
): FloatArray {
    val n = (seconds * Rate).toInt()
    val out = FloatArray(n)
    var k = 1
    while (k * f0 < 6000 && k <= 40) {
        val fk = k * f0 * sqrt(1 + inharmonicity * k * k)
        val amp = (if (k == 1) fundamentalGain else 1.0) * abs(sin(PI * k * 0.18)) / k
        val decay = 0.8 + 0.25 * k
        val phase = random.nextDouble(2 * PI)
        for (i in 0 until n) {
            val t = i.toDouble() / Rate
            out[i] += (amp * exp(-decay * t) * sin(2 * PI * fk * t + phase)).toFloat()
        }
        k++
    }
    for (i in 0 until n) out[i] = out[i] * 0.3f + (random.nextDouble(-1.0, 1.0) * noise).toFloat()
    return out
}

private fun cents(measured: Double, expected: Double) = 1200 * kotlin.math.ln(measured / expected) / kotlin.math.ln(2.0)

class PitchDetectorTest {
    private fun detectorFor(instrument: Instrument) =
        instrument.searchRange(440.0).let { PitchDetector(Rate, it.start, it.endInclusive) }

    /** The window that ends [atSeconds] into the signal, filtered the way the app filters the mic. */
    private fun PitchDetector.window(signal: FloatArray, atSeconds: Double): FloatArray {
        val filtered = signal.copyOf()
        Preprocessor(Rate, lowPassHz).process(filtered)
        val end = (atSeconds * Rate).toInt()
        return filtered.copyOfRange(end - windowSize, end)
    }

    @Test
    fun sineWavesAreMeasuredToATenthOfACent() {
        val detector = detectorFor(Instruments.Chromatic)
        for (f in listOf(41.2, 55.0, 82.41, 110.0, 146.83, 196.0, 246.94, 329.63, 440.0, 659.25, 987.77)) {
            val signal = FloatArray(detector.windowSize) { (0.5 * sin(2 * PI * f * it / Rate)).toFloat() }
            val pitch = detector.detect(signal)!!
            assertEquals("$f Hz", 0.0, cents(pitch.frequency, f), 0.1)
            assertTrue(pitch.clarity > 0.99)
        }
    }

    @Test
    fun everyGuitarStringIsFoundInTheRightOctaveWithinACent() {
        val detector = detectorFor(Instruments.Guitar)
        val random = Random(7)
        var worst = 0.0
        for (tuning in Instruments.Guitar.tunings) for (note in tuning.strings) {
            // Detuned up to a semitone either way, as when restringing.
            for (detune in listOf(-80.0, -23.0, 0.0, 9.0, 45.0)) {
                val f = note.frequency(440.0) * Math.pow(2.0, detune / 1200)
                val signal = pluck(f, 3.0, random)
                for (at in listOf(0.2, 0.6, 1.5, 2.8)) {
                    val pitch = detector.detect(detector.window(signal, at))
                    assertNotNull("$note $detune¢ at ${at}s", pitch)
                    val error = cents(pitch!!.frequency, f)
                    worst = maxOf(worst, abs(error))
                    assertEquals("${tuning.name} $note $detune¢ at ${at}s", 0.0, error, 1.0)
                }
            }
        }
        println("Worst guitar error: %.3f cents".format(worst))
    }

    @Test
    fun lowStringsWithAlmostNoFundamentalStayInTheirOctave() {
        val detector = detectorFor(Instruments.Guitar)
        val random = Random(3)
        for (name in listOf("C2", "D2", "E2", "A2", "D3")) {
            val f = Note.parse(name).frequency(440.0)
            // Fundamental at -20 dB relative to the other harmonics, like a small phone mic.
            val signal = pluck(f, 2.0, random, fundamentalGain = 0.1)
            for (at in listOf(0.2, 1.0, 1.9)) {
                val pitch = detector.detect(detector.window(signal, at))!!
                assertEquals("$name at ${at}s", 0.0, cents(pitch.frequency, f), 1.5)
            }
        }
    }

    @Test
    fun stiffStringsReadCloseToTheirFundamental() {
        val detector = detectorFor(Instruments.Guitar)
        val random = Random(11)
        for (note in Instruments.Guitar.tunings.first().strings) {
            val f = note.frequency(440.0)
            val signal = pluck(f, 1.5, random, inharmonicity = 4e-5)
            val pitch = detector.detect(detector.window(signal, 0.5))!!
            assertEquals("$note", 0.0, cents(pitch.frequency, f), 3.0)
        }
    }

    @Test
    fun bassAndUkuleleStrings() {
        for (instrument in listOf(Instruments.Bass, Instruments.Ukulele, Instruments.SevenString)) {
            val detector = detectorFor(instrument)
            val random = Random(5)
            for (note in instrument.tunings.flatMap { it.strings }.distinct()) {
                val f = note.frequency(440.0)
                val signal = pluck(f, 2.0, random)
                for (at in listOf(0.4, 1.5)) {
                    val pitch = detector.detect(detector.window(signal, at))
                    assertNotNull("${instrument.name} $note", pitch)
                    assertEquals("${instrument.name} $note at ${at}s", 0.0, cents(pitch!!.frequency, f), 1.0)
                }
            }
        }
    }

    @Test
    fun noiseHasNoClearPitch() {
        val detector = detectorFor(Instruments.Guitar)
        val random = Random(1)
        repeat(20) {
            val signal = FloatArray(detector.windowSize) { random.nextDouble(-0.1, 0.1).toFloat() }
            val pitch = detector.detect(signal)
            assertTrue("clarity ${pitch?.clarity}", pitch == null || pitch.clarity < 0.6)
        }
    }
}
