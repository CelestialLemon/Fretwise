package com.celestiallemon.tuner.metronome

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Beat emphasis: the first beat of a bar, a secondary accent (beat 4 of 6/8), or a plain beat. */
enum class Accent { Strong, Medium, Normal }

/**
 * A sample-accurate click track. Clicks are mixed into a continuous PCM stream, so timing never depends on
 * thread scheduling. The UI asks [playheadBeat] which beat is audible right now, which keeps the flash in
 * step with the sound.
 */
class MetronomeEngine {
    private val sampleRate = 48_000

    private val clicks = mapOf(
        Accent.Strong to synthClick(frequency = 1_760.0, gain = 0.95f),
        Accent.Medium to synthClick(frequency = 1_320.0, gain = 0.75f),
        Accent.Normal to synthClick(frequency = 1_100.0, gain = 0.6f),
    )

    @Volatile var bpm: Int = 80
    @Volatile var signature: TimeSignature = TimeSignature.FourFour
        set(value) {
            field = value
            restartBar = true
        }

    @Volatile private var restartBar = false
    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var track: AudioTrack? = null

    /** A beat that has been written to the stream. */
    data class Beat(val frame: Long, val serial: Long, val index: Int, val accent: Accent)

    private val log = ArrayDeque<Beat>()

    val isRunning get() = running

    /** Called on the audio thread if playback ends by itself (the device refused or lost the audio stream). */
    @Volatile var onFailure: (() -> Unit)? = null

    /** One play session. Each has its own flag, so a worker left over from an earlier session can't resume. */
    private class Session {
        @Volatile var active = true
    }

    private var session: Session? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        synchronized(log) { log.clear() }
        val current = Session().also { session = it }
        thread = Thread({
            val ok = try {
                run(current)
            } catch (e: Exception) {
                false
            }
            synchronized(this) {
                // Only the live session may report a failure or reset the engine.
                if (current.active && session === current) {
                    current.active = false
                    running = false
                    if (!ok) onFailure?.invoke()
                }
            }
        }, "metronome").apply { start() }
    }

    fun stop() {
        val worker = synchronized(this) {
            session?.active = false
            session = null
            running = false
            thread.also { thread = null }
        }
        // Join outside the lock: the worker takes it briefly as it finishes.
        worker?.join(500)
    }

    /** The latest beat whose click has reached the speaker, or null before the first one. */
    fun playheadBeat(): Beat? {
        val t = track ?: return null
        val head = runCatching { t.playbackHeadPosition.toLong() and 0xFFFFFFFFL }.getOrNull() ?: return null
        return synchronized(log) { log.lastOrNull { it.frame <= head } }
    }

    /** Plays until [running] goes false. Returns false if the audio stream failed. */
    private fun run(session: Session): Boolean {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(minBuffer * 2)
            .build()
        val chunk = FloatArray(256)
        var written = 0L
        var lastBeat = -1L
        var serial = 0L
        var beatInBar = 0
        var sound: FloatArray? = null
        var soundPos = 0
        var ok = true
        try {
            t.play()
            track = t
            while (session.active) {
                chunk.fill(0f)
                val end = written + chunk.size
                var i = 0
                while (i < chunk.size) {
                    val frame = written + i
                    // Measured from the previous beat with the current tempo, so tempo changes apply at once.
                    val due = if (lastBeat < 0) 0L else lastBeat + (sampleRate * 60.0 / bpm).toLong()
                    if (frame >= due) {
                        lastBeat = frame
                        if (restartBar) {
                            restartBar = false
                            beatInBar = 0
                        }
                        val sig = signature
                        val index = beatInBar % sig.beats
                        val accent = sig.accent(index)
                        synchronized(log) {
                            log.addLast(Beat(frame, serial++, index, accent))
                            while (log.size > 32) log.removeFirst()
                        }
                        sound = clicks.getValue(accent)
                        soundPos = 0
                        beatInBar = index + 1
                    }
                    sound?.let { s ->
                        chunk[i] = s[soundPos++]
                        if (soundPos >= s.size) sound = null
                    }
                    i++
                }
                if (t.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING) < 0) {
                    ok = false
                    break
                }
                written = end
            }
        } finally {
            runCatching { t.pause(); t.flush(); t.stop() }
            if (track === t) track = null
            t.release()
        }
        return ok
    }

    /** A short woodblock-like tick: two inharmonic partials with a fast decay and a soft attack. */
    private fun synthClick(frequency: Double, gain: Float): FloatArray {
        val length = (sampleRate * 0.045).toInt()
        return FloatArray(length) { n ->
            val t = n.toDouble() / sampleRate
            val attack = (n / (sampleRate * 0.0008)).coerceAtMost(1.0)
            val body = sin(2 * PI * frequency * t) * exp(-t * 95) +
                0.45 * sin(2 * PI * frequency * 2.76 * t) * exp(-t * 160)
            (gain * attack * body / 1.45).toFloat()
        }
    }
}
