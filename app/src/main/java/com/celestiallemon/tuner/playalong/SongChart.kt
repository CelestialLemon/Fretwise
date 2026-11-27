package com.celestiallemon.tuner.playalong

import com.celestiallemon.tuner.metronome.TimeSignature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/** One chord span on the song's beat grid. */
@Serializable
data class ChordEvent(
    /** Chord symbol (e.g. "C", "Em", "N.C."). */
    val c: String,
    /** Start beat relative to bar 1. */
    val b: Double,
    /** Length in beats. */
    val n: Double,
    /** Optional section header starting on this chord (e.g. "Verse 1", "Chorus"). */
    val section: String? = null,
    /** Optional strum mode override ("soft4", "ring", "stop", etc.). */
    val strum: String? = null,
    val letRing: Boolean = false,
)

@Serializable
data class BeatPoint(val t: Double, val b: Double)

@Serializable
data class LyricLine(
    val startSec: Double,
    val endSec: Double,
    val text: String,
    val soft: Boolean = false,
)

/** A navigable section of a song derived from its [ChordEvent] markers. */
data class SongSection(
    val index: Int,
    val name: String,
    val startBeat: Double,
    val endBeat: Double,
    val startSec: Double,
    val endSec: Double,
    val chords: List<String>,
)

/** One step in a chord-change drill loop. */
data class ProgressionStep(
    val chord: String,
    val beats: Int,
)

/**
 * Parsed play-along chart (same schema as `guitar-playalong/songs/<id>.json`).
 */
@Serializable
data class SongChart(
    val id: String,
    val title: String,
    val artist: String = "",
    val keyLabel: String = "",
    val capoLabel: String = "",
    val bpm: Double = 120.0,
    val bar1StartSec: Double = 0.0,
    val timeSig: String = "4/4",
    val durationSec: Double = 0.0,
    val outputFilename: String = "",
    val events: List<ChordEvent> = emptyList(),
    val lyrics: List<LyricLine> = emptyList(),
    val beatPoints: List<BeatPoint> = emptyList(),
) {
    val roundedBpm: Int get() = bpm.roundToInt().coerceIn(30, 250)

    /** Parses e.g. "Capo 2" -> 2, "No capo" -> null. */
    fun capoFret(): Int? = Regex("""Capo\s+(\d+)""", RegexOption.IGNORE_CASE)
        .find(capoLabel)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

    fun timeSignature(): TimeSignature = when (timeSig.trim()) {
        "2/4" -> TimeSignature.TwoFour
        "3/4" -> TimeSignature.ThreeFour
        "6/8" -> TimeSignature.SixEight
        else -> TimeSignature.FourFour
    }

    /** Converts a beat number to seconds in the video/audio timeline. */
    fun beatToSec(beat: Double): Double {
        if (beatPoints.size >= 2) {
            if (beat <= beatPoints.first().b) {
                val p0 = beatPoints[0]
                val p1 = beatPoints[1]
                val secPerBeat = (p1.t - p0.t) / (p1.b - p0.b)
                return (p0.t + (beat - p0.b) * secPerBeat).coerceAtLeast(0.0)
            }
            for (i in 0 until beatPoints.lastIndex) {
                val p0 = beatPoints[i]
                val p1 = beatPoints[i + 1]
                if (beat <= p1.b) {
                    val frac = (beat - p0.b) / (p1.b - p0.b)
                    return (p0.t + frac * (p1.t - p0.t)).coerceAtLeast(0.0)
                }
            }
            val prev = beatPoints[beatPoints.lastIndex - 1]
            val last = beatPoints.last()
            val secPerBeat = (last.t - prev.t) / (last.b - prev.b)
            return (last.t + (beat - last.b) * secPerBeat).coerceAtLeast(0.0)
        }
        val beatSec = 60.0 / bpm.coerceAtLeast(1.0)
        return (bar1StartSec + beat * beatSec).coerceAtLeast(0.0)
    }

    /** Converts a video timestamp in seconds to its beat position. */
    fun secToBeat(sec: Double): Double {
        if (beatPoints.size >= 2) {
            if (sec <= beatPoints.first().t) {
                val p0 = beatPoints[0]
                val p1 = beatPoints[1]
                val rate = (p1.b - p0.b) / (p1.t - p0.t)
                return p0.b + (sec - p0.t) * rate
            }
            for (i in 0 until beatPoints.lastIndex) {
                val p0 = beatPoints[i]
                val p1 = beatPoints[i + 1]
                if (sec <= p1.t) {
                    val frac = (sec - p0.t) / (p1.t - p0.t)
                    return p0.b + frac * (p1.b - p0.b)
                }
            }
            val prev = beatPoints[beatPoints.lastIndex - 1]
            val last = beatPoints.last()
            val rate = (last.b - prev.b) / (last.t - prev.t)
            return last.b + (sec - last.t) * rate
        }
        val beatSec = 60.0 / bpm.coerceAtLeast(1.0)
        return (sec - bar1StartSec) / beatSec
    }

    /** Distinct playable chord names in order of first appearance (skips "N.C."). */
    fun uniqueChords(): List<String> = events
        .asSequence()
        .map { it.c.trim() }
        .filter { it.isNotEmpty() && !it.equals("N.C.", ignoreCase = true) }
        .distinct()
        .toList()

    /** Navigable song sections with start/end timestamps and the chords used in each section. */
    fun sections(): List<SongSection> {
        val starts = events.withIndex().filter { !it.value.section.isNullOrBlank() }
        if (starts.isEmpty()) return emptyList()
        val finalBeat = events.maxOfOrNull { it.b + it.n } ?: 0.0
        val maxDuration = if (durationSec > 0.0) durationSec else beatToSec(finalBeat)
        return starts.mapIndexed { idx, (evIdx, ev) ->
            val nextStart = starts.getOrNull(idx + 1)
            val endBeat = nextStart?.value?.b ?: finalBeat
            val slice = events.subList(evIdx, nextStart?.index ?: events.size)
            val sectionChords = slice
                .map { it.c.trim() }
                .filter { it.isNotEmpty() && !it.equals("N.C.", ignoreCase = true) }
                .distinct()
            val startSec = beatToSec(ev.b).coerceIn(0.0, maxDuration)
            val endSec = beatToSec(endBeat).coerceIn(startSec, maxDuration)
            SongSection(
                index = idx,
                name = ev.section!!.trim(),
                startBeat = ev.b,
                endBeat = endBeat,
                startSec = startSec,
                endSec = endSec,
                chords = sectionChords,
            )
        }
    }

    /**
     * Picks a concise 4-to-8 chord progression loop from the Chorus (or first Verse/Intro)
     * so chord practice drills the real harmonic feel of the song.
     */
    fun practiceProgression(): List<ProgressionStep> {
        val playable = events.filter { it.c.isNotBlank() && !it.c.equals("N.C.", ignoreCase = true) }
        if (playable.isEmpty()) return emptyList()

        val anchorIdx = events.indexOfFirst { it.section?.contains("Chorus", ignoreCase = true) == true }
            .takeIf { it >= 0 }
            ?: events.indexOfFirst { it.section?.contains("Verse", ignoreCase = true) == true }
                .takeIf { it >= 0 }
            ?: 0

        val window = events.drop(anchorIdx)
            .filter { it.c.isNotBlank() && !it.c.equals("N.C.", ignoreCase = true) }
            .take(8)

        // Trim repeated halves (e.g. [C, G, D, Em, C, G, D, Em] -> [C, G, D, Em]).
        val trimmed = if (window.size == 8 && window.take(4).map { it.c to it.n } == window.drop(4).map { it.c to it.n }) {
            window.take(4)
        } else {
            window.take(4).ifEmpty { window }
        }
        val barBeats = timeSignature().beats
        return trimmed.map { ev ->
            val beats = ev.n.roundToInt().coerceIn(2, maxOf(barBeats * 2, 8))
            ProgressionStep(ev.c, beats)
        }
    }

    /** Suggested strumming pattern for chord-practice drills. */
    fun defaultStrumPattern(): StrumPattern = when (timeSignature()) {
        TimeSignature.SixEight -> ChordLibrary.patternFor("waltz6")
        else -> {
            val avgBeats = practiceProgression().map { it.beats }.average()
            if (avgBeats <= 2.2) ChordLibrary.patternFor("twoBeat") else ChordLibrary.patternFor("fourBeat")
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun decode(text: String): SongChart = json.decodeFromString(serializer(), text)
    }
}
