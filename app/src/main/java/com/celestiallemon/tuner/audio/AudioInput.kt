package com.celestiallemon.tuner.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlin.math.log10
import kotlin.math.max

/** One analysis step: the detected pitch (if any) and the loudness of the most recent hop, in dBFS. */
data class Frame(val pitch: Pitch?, val levelDb: Double, val seconds: Double)

/**
 * Reads the microphone on its own thread and runs the detector every [HopSize] samples (~21 ms at 48 kHz).
 * The detector can be swapped while running (different instruments need different window lengths).
 */
class AudioInput(private val context: Context) {
    @Volatile private var detectorFactory: ((Int) -> PitchDetector)? = null
    @Volatile private var thread: Thread? = null
    @Volatile private var running = false

    /**
     * Debugging aid: when set, raw mic samples are appended here (float32 LE, 48 kHz) for replaying through
     * CaptureReplayTest. Only enabled by launching with `adb shell am start ... --ez capture true`.
     */
    @Volatile var captureFile: java.io.File? = null

    /** Sets how detectors are built for the device sample rate. Takes effect on the next hop. */
    fun setDetector(factory: (sampleRate: Int) -> PitchDetector) {
        detectorFactory = factory
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onFrame: (Frame) -> Unit, onError: (String) -> Unit) {
        if (running) return
        running = true
        thread = Thread({ loop(onFrame, onError) }, "tuner-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun loop(onFrame: (Frame) -> Unit, onError: (String) -> Unit) {
        val sampleRate = SampleRate
        val record = openRecord(sampleRate)
        if (record == null) {
            running = false
            onError("Couldn't open the microphone. Another app might be using it.")
            return
        }
        try {
            record.startRecording()
            var factory: ((Int) -> PitchDetector)? = null
            var detector: PitchDetector? = null
            var preprocessor = Preprocessor(sampleRate, 4000.0)
            var buffer = FloatArray(4096)
            val hop = FloatArray(HopSize)

            while (running) {
                var read = 0
                while (read < HopSize && running) {
                    val n = record.read(hop, read, HopSize - read, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        onError("The microphone stopped responding.")
                        running = false
                        break
                    }
                    read += n
                }
                if (!running) break
                captureFile?.let { file ->
                    val bytes = java.nio.ByteBuffer.allocate(HopSize * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    for (x in hop) bytes.putFloat(x)
                    java.io.FileOutputStream(file, true).use { it.write(bytes.array()) }
                }

                if (detectorFactory !== factory) {
                    factory = detectorFactory
                    detector = factory?.invoke(sampleRate)
                    preprocessor = Preprocessor(sampleRate, detector?.lowPassHz ?: 4000.0)
                    buffer = FloatArray(detector?.windowSize ?: 4096)
                }

                val rms = preprocessor.process(hop)
                System.arraycopy(buffer, HopSize, buffer, 0, buffer.size - HopSize)
                System.arraycopy(hop, 0, buffer, buffer.size - HopSize, HopSize)

                val levelDb = 20 * log10(max(rms, 1e-9))
                // Skip the FFT work entirely in silence.
                val pitch = if (levelDb > -75) detector?.detect(buffer) else null
                onFrame(Frame(pitch, levelDb, HopSize.toDouble() / sampleRate))
            }
        } catch (e: Exception) {
            Log.e("AudioInput", "Audio loop failed", e)
            onError("The microphone stopped responding.")
        } finally {
            runCatching { record.stop() }
            record.release()
            running = false
        }
    }

    /**
     * Prefers the UNPROCESSED source (no noise suppression, no gain riding, flat low end), then VOICE_RECOGNITION,
     * which most phones also leave unprocessed, then plain MIC.
     */
    @SuppressLint("MissingPermission")
    private fun openRecord(sampleRate: Int): AudioRecord? {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val unprocessed = audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val sources = buildList {
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (minBuffer <= 0) return null
        for (source in sources) {
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .build(),
                    )
                    .setBufferSizeInBytes(max(minBuffer, HopSize * 4 * 4))
                    .build()
            }.getOrNull() ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            record.release()
        }
        return null
    }

    companion object {
        const val SampleRate = 48_000
        const val HopSize = 1024
    }
}
