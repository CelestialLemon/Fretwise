package com.celestiallemon.tuner

import kotlin.math.ln
import kotlin.math.pow

/** A pitch in equal temperament, identified by its MIDI number (A4 = 69). [flat] picks the spelling (E♭ vs D♯). */
data class Note(val midi: Int, val flat: Boolean = false) {
    val letter: String get() = (if (flat) FlatNames else SharpNames)[Math.floorMod(midi, 12)]
    val octave: Int get() = Math.floorDiv(midi, 12) - 1

    /** Cents above A4, the scale the tracker works in. */
    val cents: Double get() = (midi - 69) * 100.0

    fun frequency(a4: Double): Double = a4 * 2.0.pow((midi - 69) / 12.0)

    override fun toString() = "$letter$octave"

    companion object {
        private val SharpNames = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        private val FlatNames = listOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

        /** Parses names like "E2", "Eb2", "F#3". */
        fun parse(name: String): Note {
            val base = "C D EF G A B".indexOf(name[0].uppercaseChar())
            require(base >= 0) { "Bad note $name" }
            var i = 1
            var accidental = 0
            var flat = false
            if (i < name.length && (name[i] == '#' || name[i] == 'b')) {
                accidental = if (name[i] == '#') 1 else -1
                flat = name[i] == 'b'
                i++
            }
            val octave = name.substring(i).toInt()
            return Note((octave + 1) * 12 + base + accidental, flat)
        }
    }
}

/** Cents of [frequency] above A4 tuned to [a4]. */
fun centsFromA4(frequency: Double, a4: Double): Double = 1200.0 * ln(frequency / a4) / ln(2.0)

data class Tuning(val id: String, val name: String, val strings: List<Note>) {
    /** Low-to-high note names, e.g. "E A D G B E". */
    val summary: String get() = strings.joinToString(" ") { it.letter }
}

/**
 * An instrument and its tunings. Strings are listed in nut order (left to right as you look at the headstock),
 * which for every instrument here is low to high except the re-entrant ukulele.
 */
data class Instrument(
    val id: String,
    val name: String,
    val tunings: List<Tuning>,
    /** How many strings sit on the left side of the headstock; the rest go on the right. */
    val leftPegs: Int,
) {
    val isChromatic: Boolean get() = tunings.first().strings.isEmpty()
}

private fun tuning(id: String, name: String, notes: String) = Tuning(id, name, notes.split(" ").map(Note::parse))

object Instruments {
    val Guitar = Instrument(
        id = "guitar",
        name = "Guitar",
        leftPegs = 3,
        tunings = listOf(
            tuning("standard", "Standard", "E2 A2 D3 G3 B3 E4"),
            tuning("drop-d", "Drop D", "D2 A2 D3 G3 B3 E4"),
            tuning("half-down", "Half step down", "Eb2 Ab2 Db3 Gb3 Bb3 Eb4"),
            tuning("full-down", "Full step down", "D2 G2 C3 F3 A3 D4"),
            tuning("drop-c-sharp", "Drop C♯", "Db2 Ab2 Db3 Gb3 Bb3 Eb4"),
            tuning("drop-c", "Drop C", "C2 G2 C3 F3 A3 D4"),
            tuning("dadgad", "DADGAD", "D2 A2 D3 G3 A3 D4"),
            tuning("open-g", "Open G", "D2 G2 D3 G3 B3 D4"),
            tuning("open-d", "Open D", "D2 A2 D3 F#3 A3 D4"),
            tuning("open-e", "Open E", "E2 B2 E3 G#3 B3 E4"),
            tuning("open-c", "Open C", "C2 G2 C3 G3 C4 E4"),
        ),
    )

    val SevenString = Instrument(
        id = "guitar7",
        name = "7-string",
        leftPegs = 4,
        tunings = listOf(
            tuning("standard", "Standard", "B1 E2 A2 D3 G3 B3 E4"),
            tuning("drop-a", "Drop A", "A1 E2 A2 D3 G3 B3 E4"),
        ),
    )

    val Bass = Instrument(
        id = "bass",
        name = "Bass",
        leftPegs = 2,
        tunings = listOf(
            tuning("standard", "Standard", "E1 A1 D2 G2"),
            tuning("drop-d", "Drop D", "D1 A1 D2 G2"),
            tuning("half-down", "Half step down", "Eb1 Ab1 Db2 Gb2"),
            tuning("five-string", "5-string", "B0 E1 A1 D2 G2"),
        ),
    )

    val Ukulele = Instrument(
        id = "ukulele",
        name = "Ukulele",
        leftPegs = 2,
        tunings = listOf(
            tuning("standard", "Standard", "G4 C4 E4 A4"),
            tuning("low-g", "Low G", "G3 C4 E4 A4"),
            tuning("baritone", "Baritone", "D3 G3 B3 E4"),
        ),
    )

    val Chromatic = Instrument(
        id = "chromatic",
        name = "Chromatic",
        leftPegs = 0,
        tunings = listOf(Tuning("chromatic", "Any note", emptyList())),
    )

    val All = listOf(Guitar, SevenString, Bass, Ukulele, Chromatic)

    fun find(id: String?): Instrument = All.firstOrNull { it.id == id } ?: Guitar
}

/** The frequency range the pitch detector should search for an instrument, leaving room for badly detuned strings. */
fun Instrument.searchRange(a4: Double): ClosedFloatingPointRange<Double> {
    if (isChromatic) return 40.0..1600.0
    val notes = tunings.flatMap { it.strings }
    val low = notes.minOf { it.frequency(a4) }
    val high = notes.maxOf { it.frequency(a4) }
    // About a fourth below the lowest string and an octave-ish above the highest.
    return (low * 0.72)..(high * 1.7)
}
