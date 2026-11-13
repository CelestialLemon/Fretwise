package com.celestiallemon.tuner

import com.celestiallemon.tuner.metronome.TapTempo
import com.celestiallemon.tuner.metronome.TimeSignature
import com.celestiallemon.tuner.metronome.Accent
import com.celestiallemon.tuner.repertoire.BackupFormat
import com.celestiallemon.tuner.repertoire.DayPlan
import com.celestiallemon.tuner.repertoire.Level
import com.celestiallemon.tuner.repertoire.Song
import com.celestiallemon.tuner.repertoire.inLevel
import com.celestiallemon.tuner.repertoire.planDay
import com.celestiallemon.tuner.repertoire.practiceSummary
import com.celestiallemon.tuner.repertoire.youtubeUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepertoireTest {
    private val today = LocalDate.of(2026, 11, 13)

    private fun song(id: String, level: Level = Level.L1, since: Long = 0, vararg practiced: String) =
        Song(id = id, title = id, level = level, levelSince = since, addedAt = since, practiced = practiced.toList())

    @Test
    fun picksLeastRecentlyPracticedPerLevel() {
        val songs = listOf(
            song("a", Level.L1, 1, "2026-11-10"),
            song("b", Level.L1, 2, "2026-11-08"),
            song("c", Level.L2, 1, "2026-11-12"),
            song("d", Level.L2, 2),
            song("e", Level.Memory, 1, "2026-10-01"),
        )
        val plan = planDay(today, songs, null)
        assertEquals(mapOf(Level.L1 to "b", Level.L2 to "d", Level.Memory to "e"), plan.picks)
        assertNull("empty levels have no slot", plan.picks[Level.L3])
    }

    @Test
    fun neverPracticedTiesGoToTheOldestInTheLevel() {
        val plan = planDay(today, listOf(song("new", Level.L1, 5), song("old", Level.L1, 1)), null)
        assertEquals("old", plan.picks[Level.L1])
    }

    @Test
    fun keepsTodaysPicksAfterTickingOff() {
        val songs = listOf(song("a", Level.L1, 1, "2026-11-13"), song("b", Level.L1, 2, "2026-11-12"))
        val plan = planDay(today, songs, DayPlan("2026-11-13", mapOf(Level.L1 to "a")))
        assertEquals("a", plan.picks[Level.L1])
    }

    @Test
    fun freshPlanOnANewDay() {
        val songs = listOf(song("a", Level.L1, 1, "2026-11-12"), song("b", Level.L1, 2, "2026-11-11"))
        val plan = planDay(today, songs, DayPlan("2026-11-12", mapOf(Level.L1 to "a")))
        assertEquals("b", plan.picks[Level.L1])
        assertEquals("2026-11-13", plan.date)
    }

    @Test
    fun replacesAPickThatMovedLevel() {
        val songs = listOf(song("a", Level.L2, 1), song("b", Level.L1, 2))
        val plan = planDay(today, songs, DayPlan("2026-11-13", mapOf(Level.L1 to "a")))
        assertEquals("b", plan.picks[Level.L1])
        assertEquals("a", plan.picks[Level.L2])
    }

    @Test
    fun toggleAndSummary() {
        val s = song("a").togglePracticed(today)
        assertTrue(s.practicedOn(today))
        assertEquals("Practiced 1× · today", practiceSummary(s, today))
        assertFalse(s.togglePracticed(today).practicedOn(today))
        assertEquals("Not practiced yet", practiceSummary(song("x"), today))
        val older = song("b", Level.L1, 0, "2026-11-01", "2026-11-10")
        assertEquals("Practiced 2× · 3 days ago", practiceSummary(older, today))
    }

    @Test
    fun movingResetsLevelOrder() {
        val a = song("a", Level.L1, 1).movedTo(Level.L2, 100)
        val b = song("b", Level.L2, 50)
        assertEquals(listOf("b", "a"), listOf(a, b).inLevel(Level.L2).map { it.id })
    }

    @Test
    fun youtubeLinkOrSearch() {
        assertEquals("https://youtu.be/x", youtubeUrl(song("a").copy(link = "https://youtu.be/x")))
        assertEquals("https://youtu.be/x", youtubeUrl(song("a").copy(link = "youtu.be/x")))
        assertEquals(
            "https://www.youtube.com/results?search_query=Blackbird+The+Beatles",
            youtubeUrl(song("a").copy(title = "Blackbird", artist = "The Beatles")),
        )
    }

    @Test
    fun backupRoundTrip() {
        val songs = listOf(song("a", Level.L3, 5, "2026-11-01").copy(bpm = 96, notes = "Capo 2\nwatch bar 9", link = "https://x"))
        assertEquals(songs, BackupFormat.decode(BackupFormat.encode(songs)))
    }

    @Test
    fun backupRejectsJunk() {
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode("""{"version": 9, "songs": []}""") }
        val badDate = BackupFormat.encode(listOf(song("a", Level.L1, 0, "yesterday")))
        assertThrows(IllegalArgumentException::class.java) { BackupFormat.decode(badDate) }
    }

    @Test
    fun backupNormalisesPracticeDates() {
        val messy = BackupFormat.encode(listOf(song("a", Level.L1, 0, "2026-11-10", "2026-11-01", "2026-11-10")))
        assertEquals(listOf("2026-11-01", "2026-11-10"), BackupFormat.decode(messy).single().practiced)
    }

    @Test
    fun tapTempoAveragesAndResets() {
        val tap = TapTempo()
        assertNull(tap.tap(0))
        assertEquals(120, tap.tap(500))
        assertEquals(120, tap.tap(1000))
        assertEquals(100, TapTempo().run { tap(0); tap(600) })
        // A long pause starts over.
        assertNull(tap.tap(10_000))
        assertEquals(60, tap.tap(11_000))
    }

    @Test
    fun sixEightAccentsBeatFour() {
        assertEquals(Accent.Strong, TimeSignature.SixEight.accent(0))
        assertEquals(Accent.Medium, TimeSignature.SixEight.accent(3))
        assertEquals(Accent.Normal, TimeSignature.FourFour.accent(3))
    }
}
