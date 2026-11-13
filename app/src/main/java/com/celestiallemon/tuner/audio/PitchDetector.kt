package com.celestiallemon.tuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class Pitch(
    val frequency: Double,
    /** Height of the chosen normalized-autocorrelation peak, 0..1. Near 1 means a clean, steady tone. */
    val clarity: Double,
)

/**
 * McLeod Pitch Method (McLeod & Wyvill, "A smarter way to find pitch", 2005).
 *
 * Computes the normalized square difference function (NSDF) of a window via FFT autocorrelation, takes the first
 * "key maximum" whose height is close to the best one (which steers clear of the octave errors plain
 * autocorrelation makes on harmonic-rich plucked strings), then refines the period by measuring it across several
 * repetitions. The refinement divides the interpolation error by the number of periods, which is what gets the
 * high strings to sub-cent precision.
 */
class PitchDetector(
    val sampleRate: Int,
    minFrequency: Double,
    maxFrequency: Double,
    /** Fraction of the best peak a shorter-period peak needs to win. Higher = fewer octave-down errors. */
    private val cutoff: Double = 0.9,
) {
    private val maxLag: Int = ceil(sampleRate / minFrequency).toInt()
    private val minLag: Int = max(2, (sampleRate / maxFrequency).toInt())

    /**
     * Six periods of the lowest note, rounded up to a power of two (8192 samples, 170 ms, for guitar). Long enough
     * to average out noise and to measure the period over several repetitions; still quick to follow a new pluck.
     */
    val windowSize: Int = nextPowerOfTwo(max(4096, 6 * maxLag))

    /** Where to cut the input's top end (see [Preprocessor]): a few harmonics above the highest expected note. */
    val lowPassHz: Double = (maxFrequency * 4).coerceIn(1000.0, 4000.0)

    private val fftSize = windowSize * 2
    private val fft = Fft(fftSize)
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)

    /** NSDF up to half the window; the upper part is only used to refine the period. */
    private val nsdfLength = windowSize / 2
    private val nsdf = DoubleArray(nsdfLength)
    private val window = DoubleArray(windowSize)

    /** Detects the pitch of the last [windowSize] samples of [samples] (anything before that is ignored). */
    fun detect(samples: FloatArray): Pitch? {
        require(samples.size >= windowSize)
        val offset = samples.size - windowSize
        var mean = 0.0
        for (i in 0 until windowSize) mean += samples[offset + i]
        mean /= windowSize
        for (i in 0 until windowSize) window[i] = samples[offset + i] - mean
        return detectWindow()
    }

    private fun detectWindow(): Pitch? {
        // Autocorrelation r(τ) = IFFT(|FFT(x)|²), zero-padded to avoid wrap-around.
        window.copyInto(re)
        re.fill(0.0, windowSize, fftSize)
        im.fill(0.0)
        fft.transform(re, im)
        for (i in 0 until fftSize) {
            re[i] = re[i] * re[i] + im[i] * im[i]
            im[i] = 0.0
        }
        // The power spectrum is real and even, so a forward transform equals the inverse up to scale.
        fft.transform(re, im)

        // NSDF n(τ) = 2·r(τ) / m(τ) with m(τ) = Σ x[j]² + x[j+τ]² over the overlap, updated incrementally.
        var m = 0.0
        for (x in window) m += 2 * x * x
        if (m < 1e-12) return null
        for (tau in 0 until nsdfLength) {
            if (tau > 0) {
                val a = window[tau - 1]
                val b = window[windowSize - tau]
                m -= a * a + b * b
            }
            nsdf[tau] = if (m > 1e-12) 2 * (re[tau] / fftSize) / m else 0.0
        }

        val tau = pickPeriod() ?: return null
        val (lag, clarity) = interpolate(tau)
        val period = refine(lag, clarity)
        return Pitch(sampleRate / period, clarity.coerceAtMost(1.0))
    }

    /** Finds the key maxima (the highest point of each positive lobe after the first) and applies the MPM cutoff. */
    private fun pickPeriod(): Int? {
        val limit = min(maxLag + 1, nsdfLength - 1)
        var tau = 1
        while (tau < limit && nsdf[tau] > 0) tau++ // skip the lobe around τ = 0
        if (tau >= limit) return null

        var keyCount = 0
        while (tau < limit) {
            while (tau < limit && nsdf[tau] <= 0) tau++
            var best = -1
            while (tau < limit && nsdf[tau] > 0) {
                if (best < 0 || nsdf[tau] > nsdf[best]) best = tau
                tau++
            }
            if (best >= minLag && best < limit - 1 && keyCount < keyMaxima.size) keyMaxima[keyCount++] = best
        }
        if (keyCount == 0) return null

        var highest = 0.0
        for (i in 0 until keyCount) highest = max(highest, nsdf[keyMaxima[i]])
        if (highest < 0.45) return null
        val threshold = cutoff * highest
        for (i in 0 until keyCount) if (nsdf[keyMaxima[i]] >= threshold) return keyMaxima[i]
        return null
    }

    private val keyMaxima = IntArray(256)

    /** Parabolic interpolation of the peak at [tau]: returns (fractional lag, peak height). */
    private fun interpolate(tau: Int): Pair<Double, Double> {
        val a = nsdf[tau - 1]
        val b = nsdf[tau]
        val c = nsdf[tau + 1]
        val denominator = a - 2 * b + c
        if (abs(denominator) < 1e-12) return tau.toDouble() to b
        val shift = 0.5 * (a - c) / denominator
        return (tau + shift) to (b - 0.25 * (a - c) * shift)
    }

    /**
     * Measures the period over as many repetitions as fit in the NSDF. The peak near k·period is located and
     * interpolated the same way; dividing by k shrinks the error k-fold. Stops at the first repetition that has
     * lost too much correlation (pitch drifting, string decaying).
     */
    private fun refine(lag: Double, clarity: Double): Double {
        var best = lag
        var k = 2
        while (true) {
            val expected = lag * k
            val center = expected.toInt()
            val radius = max(2, (lag * 0.08).toInt())
            if (center + radius + 1 >= nsdfLength) break
            var peak = center
            for (t in center - radius..center + radius) if (nsdf[t] > nsdf[peak]) peak = t
            if (peak == center - radius || peak == center + radius) break
            val (fine, height) = interpolate(peak)
            if (height < clarity * 0.85) break
            best = fine / k
            k++
        }
        return best
    }

    companion object {
        fun nextPowerOfTwo(n: Int): Int {
            var p = 1
            while (p < n) p = p shl 1
            return p
        }
    }
}

/** In-place iterative radix-2 complex FFT. */
class Fft(private val n: Int) {
    private val cosTable = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinTable = DoubleArray(n / 2) { -sin(2 * PI * it / n) }
    private val bitReverse = IntArray(n)

    init {
        require(n > 1 && n and (n - 1) == 0) { "FFT size must be a power of two" }
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) bitReverse[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = bitReverse[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val l = j + half
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }
}
