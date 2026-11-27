package com.celestiallemon.tuner

import com.celestiallemon.tuner.metronome.TimeSignature
import com.celestiallemon.tuner.playalong.BeatPoint
import com.celestiallemon.tuner.playalong.ChordEvent
import com.celestiallemon.tuner.playalong.ChordLibrary
import com.celestiallemon.tuner.playalong.SongChart
import com.celestiallemon.tuner.repertoire.BackupFormat
import com.celestiallemon.tuner.repertoire.Level
import com.celestiallemon.tuner.repertoire.Song
import com.celestiallemon.tuner.repertoire.mergeBundledCharts
import com.celestiallemon.tuner.ui.strumSlotIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PlayAlongTest {
    private val chartsDir = File("src/main/assets/charts")

    @Test
    fun allBundledChartsParseAndResolveEveryChord() {
        val files = chartsDir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
        assertEquals("Expected 6 bundled play-along charts", 6, files.size)
        for (file in files) {
            val chart = SongChart.decode(file.readText())
            assertTrue("${file.name} title blank", chart.title.isNotBlank())
            assertTrue("${file.name} outputFilename blank", chart.outputFilename.endsWith(".mp4"))
            val chords = chart.uniqueChords()
            assertTrue("${file.name} has no chords", chords.isNotEmpty())
            for (c in chords) {
                assertNotNull("Chord $c in ${file.name} missing from ChordLibrary", ChordLibrary.find(c))
            }
            val sections = chart.sections()
            assertTrue("${file.name} has no sections", sections.isNotEmpty())
            for (sec in sections) {
                assertTrue("${file.name} section ${sec.name} end < start", sec.endSec >= sec.startSec)
            }
            val prog = chart.practiceProgression()
            assertTrue("${file.name} has empty progression", prog.isNotEmpty())
        }
    }

    @Test
    fun perfectParsesCapoSixEightAndOffset() {
        val perfect = SongChart.decode(File(chartsDir, "perfect.json").readText())
        assertEquals(1, perfect.capoFret())
        assertEquals(TimeSignature.SixEight, perfect.timeSignature())
        assertEquals(190, perfect.roundedBpm)
        assertEquals("waltz6", perfect.defaultStrumPattern().id)
        val firstSection = perfect.sections().first()
        assertEquals("Verse 1", firstSection.name)
        assertEquals(22.199, firstSection.startSec, 0.01)
    }

    @Test
    fun beatToSecAndBackWithBeatPoints() {
        val chart = SongChart(
            id = "test",
            title = "Test",
            bpm = 120.0,
            bar1StartSec = 1.0,
            beatPoints = listOf(BeatPoint(1.0, 0.0), BeatPoint(3.0, 4.0), BeatPoint(7.0, 8.0)),
            events = listOf(ChordEvent("C", 0.0, 4.0, section = "Intro"), ChordEvent("G", 4.0, 4.0, section = "Verse")),
        )
        assertEquals(2.0, chart.beatToSec(2.0), 1e-6)
        assertEquals(5.0, chart.beatToSec(6.0), 1e-6)
        assertEquals(6.0, chart.secToBeat(5.0), 1e-6)
        assertNull(chart.capoFret())
    }

    @Test
    fun oldBackupWithoutPlayAlongFieldsDecodesCleanly() {
        val legacyJson = """
            {
              "version": 1,
              "songs": [
                {
                  "id": "old-1",
                  "title": "Blackbird",
                  "artist": "The Beatles",
                  "bpm": 92,
                  "level": "L2",
                  "levelSince": 1000,
                  "addedAt": 500,
                  "practiced": ["2026-11-10"]
                }
              ]
            }
        """.trimIndent()
        val songs = BackupFormat.decode(legacyJson)
        assertEquals(1, songs.size)
        val song = songs.single()
        assertEquals("Blackbird", song.title)
        assertNull(song.chartId)
        assertTrue(song.chords.isEmpty())
        assertEquals(false, song.hasPlayAlong)
    }

    @Test
    fun withPracticedIsIdempotent() {
        val day = LocalDate.of(2026, 11, 27)
        val song = Song(id = "1", title = "Mine", level = Level.L1, levelSince = 0, addedAt = 0)
        val done = song.withPracticed(day, true)
        assertEquals(listOf("2026-11-27"), done.practiced)
        assertEquals(done, done.withPracticed(day, true))
        assertEquals(emptyList<String>(), done.withPracticed(day, false).practiced)
    }

    @Test
    fun practiceProgressionPicksFullRepeatingChorusPhrase() {
        val perfect = SongChart.decode(File(chartsDir, "perfect.json").readText())
        val prog = perfect.practiceProgression()
        assertEquals(listOf("Em", "C", "G", "D"), prog.map { it.chord })
        assertEquals(listOf(6, 6, 6, 6), prog.map { it.beats })

        // Mine's chorus repeats every 16 beats across 5 chords: C(4) G(4) D(4) Em(2) D(2).
        val mine = SongChart.decode(File(chartsDir, "mine.json").readText())
        val mineProg = mine.practiceProgression()
        assertEquals(listOf("C", "G", "D", "Em", "D"), mineProg.map { it.chord })
        assertEquals(listOf(4, 4, 4, 2, 2), mineProg.map { it.beats })
    }

    @Test
    fun mergeBundledChartsNeverCreatesDuplicateIdsOrHijacksDifferentArtist() {
        val charts = chartsDir.listFiles { f -> f.extension == "json" }!!
            .sortedBy { it.name }
            .map { SongChart.decode(it.readText()) }
        val initial = mergeBundledCharts(emptyList(), charts, 1_000L).songs
        // Rename seeded Mine to Speak Now and sync twice.
        val renamed = initial.map { if (it.chartId == "mine") it.copy(title = "Speak Now") else it }
        val pass1 = mergeBundledCharts(renamed, charts, 2_000L)
        val pass2 = mergeBundledCharts(pass1.songs, charts, 3_000L)

        assertEquals(pass2.songs.size, pass2.songs.map { it.id }.toSet().size)
        assertEquals(0, pass2.added)
        assertEquals(0, pass2.linked)
        // Backup round-trip must accept the merged repertoire cleanly.
        assertEquals(pass2.songs, BackupFormat.decode(BackupFormat.encode(pass2.songs)))

        // A user song with the same title ("Mine") by a different artist must not be hijacked.
        val otherMine = Song(id = "user-mine", title = "Mine", artist = "Bazzi", level = Level.L2, levelSince = 0, addedAt = 0)
        val mergedWithOther = mergeBundledCharts(listOf(otherMine), charts, 4_000L)
        assertNull(mergedWithOther.songs.first { it.id == "user-mine" }.chartId)
        assertEquals(1 + charts.size, mergedWithOther.songs.size)
    }

    @Test
    fun strumSlotIndexCoversEveryOffbeatSlot() {
        val visited = mutableSetOf<Int>()
        for (beat in 0 until 4) {
            visited += strumSlotIndex(beat, 0.1f, cycleBeats = 4, slotCount = 8)
            visited += strumSlotIndex(beat, 0.6f, cycleBeats = 4, slotCount = 8)
        }
        assertEquals((0..7).toSet(), visited)
    }
}
