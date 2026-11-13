package com.celestiallemon.tuner

import com.celestiallemon.tuner.audio.AudioInput
import com.celestiallemon.tuner.audio.Frame
import com.celestiallemon.tuner.audio.PitchDetector
import com.celestiallemon.tuner.audio.Preprocessor
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max

/** Replays a raw mic capture (float32 LE, 48 kHz) through the app's pipeline. Set CAPTURE to the file. */
class CaptureReplayTest {
    @Test
    fun replay() {
        val path = System.getenv("CAPTURE")
        assumeTrue(path != null && File(path).exists())
        val bytes = File(path!!).readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val samples = FloatArray(buf.remaining()).also { buf.get(it) }
        val rate = AudioInput.SampleRate
        val hopSize = AudioInput.HopSize
        val range = Instruments.Guitar.searchRange(440.0)
        val detector = PitchDetector(rate, range.start, range.endInclusive)
        val pre = Preprocessor(rate, detector.lowPassHz)
        val tracker = TunerTracker(Instruments.Guitar.tunings.first(), 440.0)
        val window = FloatArray(detector.windowSize)
        val hop = FloatArray(hopSize)
        val out = StringBuilder()
        var i = 0
        var frame = 0
        val segments = StringBuilder()
        var segLabel = ""
        var segStart = 0.0
        val segCents = mutableListOf<Double>()
        while (i + hopSize <= samples.size) {
            System.arraycopy(samples, i, hop, 0, hopSize)
            val rms = pre.process(hop)
            System.arraycopy(window, hopSize, window, 0, window.size - hopSize)
            System.arraycopy(hop, 0, window, window.size - hopSize, hopSize)
            val db = 20 * log10(max(rms, 1e-9))
            val pitch = detector.detect(window)
            val r = tracker.process(Frame(pitch, db, hopSize.toDouble() / rate))
            val t = i.toDouble() / rate
            val label = if (r.active) "${r.target}" else ""
            if (label != segLabel) {
                if (segLabel.isNotEmpty()) segments.append("%6.2f-%6.2fs  %-4s %s\n".format(segStart, t, segLabel, segCents.sorted().let { "median %+.0f¢, range %+.0f..%+.0f".format(it[it.size / 2], it.first(), it.last()) }))
                segLabel = label; segStart = t; segCents.clear()
            }
            if (r.active) segCents.add(r.cents)
            if (frame % 5 == 0) {
                val note = pitch?.let { Note((centsFromA4(it.frequency, 440.0) / 100).let { c -> Math.round(c).toInt() } + 69) }
                out.append(
                    "%6.2fs %6.1fdB  %8s %5s %5s | %s\n".format(
                        i.toDouble() / rate, db,
                        pitch?.let { "%.1f".format(it.frequency) } ?: "-",
                        note?.toString() ?: "", pitch?.let { "%.2f".format(it.clarity) } ?: "",
                        if (r.active) "${r.target} ${"%+.0f".format(r.cents)}" else "",
                    ),
                )
            }
            i += hopSize
            frame++
        }
        File(System.getenv("REPLAY_OUT") ?: "replay.txt").writeText(segments.toString() + "\n" + out.toString())
    }
}
