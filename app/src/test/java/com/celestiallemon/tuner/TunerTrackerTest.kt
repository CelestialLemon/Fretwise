package com.celestiallemon.tuner

import com.celestiallemon.tuner.audio.Frame
import com.celestiallemon.tuner.audio.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class TunerTrackerTest {
    private val standard = Instruments.Guitar.tunings.first()
    private val hop = 1024.0 / 48_000

    private fun TunerTracker.play(note: String, cents: Double, frames: Int, level: Double = -30.0): Reading {
        val f = Note.parse(note).frequency(a4) * 2.0.pow(cents / 1200)
        var reading = Reading()
        repeat(frames) { reading = process(Frame(Pitch(f, 0.97), level, hop)) }
        return reading
    }

    private fun TunerTracker.silence(frames: Int): Reading {
        var reading = Reading()
        repeat(frames) { reading = process(Frame(null, -80.0, hop)) }
        return reading
    }

    @Test
    fun picksTheNearestStringEvenWhenWayOff() {
        val tracker = TunerTracker(standard, 440.0)
        tracker.silence(50)
        // A string tuned 40 cents flat is still the A string, not "close to G♯".
        val reading = tracker.play("A2", -40.0, 20)
        assertEquals(1, reading.stringIndex)
        assertEquals(-40.0, reading.cents, 0.5)
        assertFalse(reading.inTune)
    }

    @Test
    fun ignoresBriefOctaveGlitches() {
        val tracker = TunerTracker(standard, 440.0)
        tracker.silence(50)
        tracker.play("E2", 5.0, 20)
        val glitch = tracker.play("E3", 5.0, 2)
        assertEquals(0, glitch.stringIndex)
        assertEquals(5.0, glitch.cents, 0.5)
    }

    @Test
    fun followsANewString() {
        val tracker = TunerTracker(standard, 440.0)
        tracker.silence(50)
        tracker.play("E2", 0.0, 20)
        val reading = tracker.play("G3", 12.0, 12)
        assertEquals(3, reading.stringIndex)
        assertEquals(12.0, reading.cents, 1.0)
    }

    @Test
    fun marksAStringTunedAfterItHoldsInTune() {
        val tracker = TunerTracker(standard, 440.0)
        tracker.silence(50)
        val passing = tracker.play("D3", 1.0, 8)
        assertTrue(passing.inTune)
        assertFalse(2 in passing.tuned)
        val held = tracker.play("D3", 1.0, 20)
        assertTrue(2 in held.tuned)
        assertEquals(1, held.tunedEvent)
        // Holds the reading through a short gap, then goes idle but keeps the checkmark.
        assertTrue(tracker.silence(20).active)
        val idle = tracker.silence(60)
        assertFalse(idle.active)
        assertTrue(2 in idle.tuned)
    }

    @Test
    fun lockedStringMeasuresAgainstThatString() {
        val tracker = TunerTracker(standard, 440.0)
        tracker.silence(50)
        tracker.lockedString = 5
        val reading = tracker.play("D4", 0.0, 20)
        assertEquals(5, reading.stringIndex)
        assertEquals(-200.0, reading.cents, 0.5)
    }

    @Test
    fun respectsTheReferencePitch() {
        val tracker = TunerTracker(standard, 432.0)
        tracker.silence(50)
        val reading = tracker.play("A2", 0.0, 20)
        assertEquals(0.0, reading.cents, 0.5)
        assertEquals(108.0, reading.frequency!!, 0.1)
    }

    @Test
    fun quietNoiseIsIgnored() {
        val tracker = TunerTracker(standard, 440.0)
        tracker.silence(50)
        assertFalse(tracker.play("A2", 0.0, 20, level = -75.0).active)
    }

    @Test
    fun chromaticModeNamesTheNote() {
        val tracker = TunerTracker(Instruments.Chromatic.tunings.first(), 440.0)
        tracker.silence(50)
        val reading = tracker.play("C#5", -20.0, 20)
        assertEquals("C♯5", reading.target.toString())
        assertEquals(-20.0, reading.cents, 0.5)
    }
}
