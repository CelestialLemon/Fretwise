package com.celestiallemon.tuner

import com.celestiallemon.tuner.metronome.TimeSignature
import com.celestiallemon.tuner.playalong.BeatPoint
import com.celestiallemon.tuner.playalong.ChordEvent
import com.celestiallemon.tuner.playalong.ChordLibrary
import com.celestiallemon.tuner.playalong.SongChart
import com.celestiallemon.tuner.repertoire.BackupFormat
import com.celestiallemon.tuner.repertoire.Level
import com.celestiallemon.tuner.repertoire.Song
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
}
