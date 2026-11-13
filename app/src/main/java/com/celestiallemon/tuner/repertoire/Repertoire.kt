package com.celestiallemon.tuner.repertoire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** How well a song is learned. Songs enter at [L1] and are promoted by hand until they reach [Memory]. */
@Serializable
enum class Level(val label: String, val softCap: Int?) {
    L1("L1", 3),
    L2("L2", 3),
    L3("L3", 4),
    Memory("Memory", null);

    val next: Level? get() = entries.getOrNull(ordinal + 1)
    val previous: Level? get() = entries.getOrNull(ordinal - 1)
}

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    /** Target tempo, if known. */
    val bpm: Int? = null,
    val notes: String = "",
    /** A tab, video, or anything else to open for this song. */
    val link: String = "",
    val level: Level = Level.L1,
    /** When the song entered its current level (epoch ms). Songs within a level are listed oldest first. */
    val levelSince: Long,
    val addedAt: Long,
    /** Every day this song was practiced, as ISO dates, ascending, no repeats. */
    val practiced: List<String> = emptyList(),
) {
    val practiceCount: Int get() = practiced.size
    val lastPracticed: LocalDate? get() = practiced.lastOrNull()?.let(LocalDate::parse)

    fun practicedOn(day: LocalDate) = day.toString() in practiced

    /** Ticks or unticks [day] as practiced. */
    fun togglePracticed(day: LocalDate): Song {
        val key = day.toString()
        return copy(practiced = if (key in practiced) practiced - key else (practiced + key).sorted())
    }

    fun movedTo(level: Level, now: Long) = if (level == this.level) this else copy(level = level, levelSince = now)
}

/** Today's practice set: one song id per level. */
@Serializable
data class DayPlan(val date: String, val picks: Map<Level, String>)

@Serializable
data class RepertoireData(
    val version: Int = 1,
    val songs: List<Song> = emptyList(),
    val today: DayPlan? = null,
)

/** Songs of [level], oldest in the level first. */
fun List<Song>.inLevel(level: Level) = filter { it.level == level }.sortedWith(compareBy({ it.levelSince }, { it.addedAt }))

/**
 * Builds the practice set for [day]. Picks from an earlier plan on the same day are kept (so ticking a song
 * off doesn't make the app pick another), as long as the song still exists and is still in that level.
 * Empty slots get the song in that level that was practiced least recently; never-practiced songs come first.
 */
fun planDay(day: LocalDate, songs: List<Song>, previous: DayPlan?): DayPlan {
    val kept = if (previous?.date == day.toString()) previous.picks else emptyMap()
    val picks = buildMap {
        for (level in Level.entries) {
            val candidates = songs.inLevel(level)
            val keep = kept[level]?.takeIf { id -> candidates.any { it.id == id } }
            val pick = keep ?: leastRecentlyPracticed(candidates)?.id
            if (pick != null) put(level, pick)
        }
    }
    return DayPlan(day.toString(), picks)
}

private fun leastRecentlyPracticed(songs: List<Song>): Song? =
    // ISO dates sort lexically; "" (never practiced) sorts first. Ties go to the song that's been in the level longest.
    songs.minWithOrNull(compareBy({ it.practiced.lastOrNull() ?: "" }, { it.levelSince }, { it.addedAt }))

/** "Practiced 12× · last 3 days ago", or "Not practiced yet". */
fun practiceSummary(song: Song, today: LocalDate): String {
    val last = song.lastPracticed ?: return "Not practiced yet"
    val count = song.practiceCount
    return "Practiced $count× · ${relativeDay(last, today)}"
}

fun relativeDay(day: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days < 7L -> "$days days ago"
        days < 14L -> "a week ago"
        days < 60L -> "${days / 7} weeks ago"
        days < 365L -> "${days / 30} months ago"
        else -> "over a year ago"
    }
}

/** A YouTube URL for [song]: its saved link if there is one, otherwise a search for "title artist". */
fun youtubeUrl(song: Song): String {
    val link = song.link.trim()
    if (link.isNotEmpty()) return if ("://" in link) link else "https://$link"
    val query = listOf(song.title, song.artist).filter { it.isNotBlank() }.joinToString(" ")
    return "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8")
}

object BackupFormat {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(songs: List<Song>): String = json.encodeToString(RepertoireData.serializer(), RepertoireData(songs = songs))

    /** Parses a backup file, throwing [IllegalArgumentException] with a readable message if it isn't one. */
    fun decode(text: String): List<Song> {
        val data = try {
            json.decodeFromString(RepertoireData.serializer(), text)
        } catch (e: Exception) {
            throw IllegalArgumentException("That file isn't a Fretwise backup.", e)
        }
        require(data.version <= 1) { "This backup is from a newer version of Fretwise." }
        require(data.songs.map { it.id }.toSet().size == data.songs.size) { "The backup has duplicate songs." }
        return data.songs
    }
}
