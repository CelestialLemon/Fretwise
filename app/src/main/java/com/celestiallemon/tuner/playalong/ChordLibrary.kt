package com.celestiallemon.tuner.playalong

/**
 * A barre spanning strings [fromString]..[toString] (0 = low E, 5 = high e) across [fret].
 */
data class ChordBarre(
    val fret: Int,
    val fromString: Int,
    val toString: Int,
)

/**
 * A guitar chord fingering on 6 strings (0 = low E .. 5 = high e).
 * [frets] holds -1 for muted ('x'), 0 for open, or a positive fret number.
 * [fingers] holds "", "1".."4", or "T" (thumb).
 */
data class ChordShape(
    val name: String,
    val frets: List<Int>,
    val fingers: List<String>,
    val baseFret: Int = 1,
    val barre: ChordBarre? = null,
)

enum class StrumDir { Down, Up, Rest }

data class StrumSlot(val dir: StrumDir, val label: String)

data class StrumPattern(
    val id: String,
    val name: String,
    val cycleBeats: Int,
    val slots: List<StrumSlot>,
)

/** Standard open & barre chord shapes matching the play-along overlay library. */
object ChordLibrary {
    private fun shape(
        name: String,
        frets: List<Int>,
        fingers: List<String>,
        baseFret: Int = 1,
        barre: ChordBarre? = null,
    ) = name to ChordShape(name, frets, fingers, baseFret, barre)

    val standard: Map<String, ChordShape> = linkedMapOf(
        shape("C", listOf(-1, 3, 2, 0, 1, 0), listOf("", "3", "2", "", "1", "")),
        shape("G", listOf(3, 2, 0, 0, 0, 3), listOf("2", "1", "", "", "", "3")),
        shape("D", listOf(-1, -1, 0, 2, 3, 2), listOf("", "", "", "1", "3", "2")),
        shape("Em", listOf(0, 2, 2, 0, 0, 0), listOf("", "1", "2", "", "", "")),
        shape("Am", listOf(-1, 0, 2, 2, 1, 0), listOf("", "", "2", "3", "1", "")),
        shape("Am7", listOf(-1, 0, 2, 0, 1, 0), listOf("", "", "2", "", "1", "")),
        shape("Dm", listOf(-1, -1, 0, 2, 3, 1), listOf("", "", "", "2", "3", "1")),
        shape("A", listOf(-1, 0, 2, 2, 2, 0), listOf("", "", "1", "2", "3", "")),
        shape("E", listOf(0, 2, 2, 1, 0, 0), listOf("", "2", "3", "1", "", "")),
        shape("F", listOf(-1, -1, 3, 2, 1, 1), listOf("", "", "3", "2", "1", "1")),
        shape("Fmaj7", listOf(-1, -1, 3, 2, 1, 0), listOf("", "", "3", "2", "1", "")),
        shape("Bm", listOf(-1, 2, 4, 4, 3, 2), listOf("", "1", "3", "4", "2", "1"), barre = ChordBarre(2, 1, 5)),
        shape("Bm7", listOf(-1, 2, 0, 2, 0, 2), listOf("", "1", "", "2", "", "3")),
        shape("B7", listOf(-1, 2, 1, 2, 0, 2), listOf("", "2", "1", "3", "", "4")),
        shape("B", listOf(-1, 2, 4, 4, 4, 2), listOf("", "1", "2", "3", "4", "1"), barre = ChordBarre(2, 1, 5)),
        shape("D/F#", listOf(2, -1, 0, 2, 3, 2), listOf("T", "", "", "1", "3", "2")),
        shape("Dsus4", listOf(-1, -1, 0, 2, 3, 3), listOf("", "", "", "1", "2", "3")),
        shape("Asus2", listOf(-1, 0, 2, 2, 0, 0), listOf("", "", "1", "2", "", "")),
        shape("Asus4", listOf(-1, 0, 2, 2, 3, 0), listOf("", "", "1", "2", "3", "")),
        shape("Cadd9", listOf(-1, 3, 2, 0, 3, 3), listOf("", "2", "1", "", "3", "4")),
        shape("Em7", listOf(0, 2, 2, 0, 3, 3), listOf("", "1", "2", "", "3", "4")),
        shape("F#m", listOf(2, 4, 4, 2, 2, 2), listOf("1", "3", "4", "1", "1", "1"), barre = ChordBarre(2, 0, 5)),
        shape("C#m", listOf(-1, 4, 6, 6, 5, 4), listOf("", "1", "3", "4", "2", "1"), baseFret = 4, barre = ChordBarre(4, 1, 5)),
        shape("G#m", listOf(4, 6, 6, 4, 4, 4), listOf("1", "3", "4", "1", "1", "1"), baseFret = 4, barre = ChordBarre(4, 0, 5)),
    )

    val allNames: List<String> = standard.keys.toList()

    fun find(name: String): ChordShape? = standard[name]

    val strumPatterns: Map<String, StrumPattern> = linkedMapOf(
        "fourBeat" to StrumPattern(
            id = "fourBeat",
            name = "Island 4/4",
            cycleBeats = 4,
            slots = listOf(
                StrumSlot(StrumDir.Down, "1"),
                StrumSlot(StrumDir.Rest, "&"),
                StrumSlot(StrumDir.Down, "2"),
                StrumSlot(StrumDir.Up, "&"),
                StrumSlot(StrumDir.Rest, "3"),
                StrumSlot(StrumDir.Up, "&"),
                StrumSlot(StrumDir.Down, "4"),
                StrumSlot(StrumDir.Up, "&"),
            ),
        ),
        "twoBeat" to StrumPattern(
            id = "twoBeat",
            name = "Quick 2-beat",
            cycleBeats = 2,
            slots = listOf(
                StrumSlot(StrumDir.Down, "1"),
                StrumSlot(StrumDir.Rest, "&"),
                StrumSlot(StrumDir.Down, "2"),
                StrumSlot(StrumDir.Up, "&"),
            ),
        ),
        "soft4" to StrumPattern(
            id = "soft4",
            name = "Soft 4/4",
            cycleBeats = 4,
            slots = listOf(
                StrumSlot(StrumDir.Down, "1"),
                StrumSlot(StrumDir.Rest, "2"),
                StrumSlot(StrumDir.Down, "3"),
                StrumSlot(StrumDir.Rest, "4"),
            ),
        ),
        "driving4" to StrumPattern(
            id = "driving4",
            name = "Driving 8ths",
            cycleBeats = 4,
            slots = listOf(
                StrumSlot(StrumDir.Down, "1"),
                StrumSlot(StrumDir.Up, "&"),
                StrumSlot(StrumDir.Down, "2"),
                StrumSlot(StrumDir.Up, "&"),
                StrumSlot(StrumDir.Down, "3"),
                StrumSlot(StrumDir.Up, "&"),
                StrumSlot(StrumDir.Down, "4"),
                StrumSlot(StrumDir.Up, "&"),
            ),
        ),
        "waltz6" to StrumPattern(
            id = "waltz6",
            name = "Waltz 6/8",
            cycleBeats = 6,
            slots = listOf(
                StrumSlot(StrumDir.Down, "1"),
                StrumSlot(StrumDir.Down, "2"),
                StrumSlot(StrumDir.Up, "3"),
                StrumSlot(StrumDir.Down, "4"),
                StrumSlot(StrumDir.Down, "5"),
                StrumSlot(StrumDir.Up, "6"),
            ),
        ),
    )

    fun patternFor(id: String): StrumPattern = strumPatterns[id] ?: strumPatterns.getValue("fourBeat")
}
