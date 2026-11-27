package com.celestiallemon.tuner.playalong

import android.content.Context

/** Loads bundled play-along song charts from `assets/charts/`. */
object ChartRepository {
    private const val CHARTS_DIR = "charts"

    fun loadBundled(context: Context): List<SongChart> {
        val files = runCatching { context.assets.list(CHARTS_DIR).orEmpty() }.getOrDefault(emptyArray())
        return files
            .filter { it.endsWith(".json", ignoreCase = true) }
            .mapNotNull { fileName ->
                runCatching {
                    context.assets.open("$CHARTS_DIR/$fileName").bufferedReader().use {
                        SongChart.decode(it.readText())
                    }
                }.getOrNull()
            }
            .sortedBy { it.outputFilename.ifBlank { it.title } }
    }
}
