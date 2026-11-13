package com.celestiallemon.tuner.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Streaming clean-up before pitch detection: a DC blocker (~20 Hz) and a 4th-order Butterworth low-pass at
 * [lowPassHz]. Pitch lives in the lower harmonics, while mic hiss is spread evenly up to 24 kHz, so cutting the top
 * end raises the signal-to-noise ratio by ~10 dB for guitar. Both filters keep state across calls.
 */
class Preprocessor(sampleRate: Int, lowPassHz: Double) {
    private val dcPole = 1.0 - 2.0 * PI * 20.0 / sampleRate
    private var dcIn = 0.0
    private var dcOut = 0.0
    private val stages = listOf(Biquad(sampleRate, lowPassHz, 0.5412), Biquad(sampleRate, lowPassHz, 1.3066))

    /** Filters [samples] in place and returns their RMS after filtering: the loudness within the instrument's band. */
    fun process(samples: FloatArray, count: Int = samples.size): Double {
        var energy = 0.0
        for (i in 0 until count) {
            val x = samples[i].toDouble()
            val hp = x - dcIn + dcPole * dcOut
            dcIn = x
            dcOut = hp
            var y = hp
            for (stage in stages) y = stage.process(y)
            samples[i] = y.toFloat()
            energy += y * y
        }
        return sqrt(energy / count)
    }

    private class Biquad(sampleRate: Int, cutoff: Double, q: Double) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double
        private var z1 = 0.0
        private var z2 = 0.0

        init {
            val w = 2 * PI * cutoff / sampleRate
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            b0 = (1 - cos(w)) / 2 / a0
            b1 = (1 - cos(w)) / a0
            b2 = b0
            a1 = -2 * cos(w) / a0
            a2 = (1 - alpha) / a0
        }

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }
}
