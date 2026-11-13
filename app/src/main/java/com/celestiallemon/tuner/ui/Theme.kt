package com.celestiallemon.tuner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.celestiallemon.tuner.R

/** Colours taken from the instrument: rosewood, walnut, bone, brass, nickel. */
object Palette {
    val Background = Color(0xFF16110F)
    val Surface = Color(0xFF221A16)
    val SurfaceHigh = Color(0xFF30251F)
    val Outline = Color(0xFF4A3B33)
    val Bone = Color(0xFFF3E7D3)
    val Muted = Color(0xFFA8978A)
    val Faint = Color(0xFF6E5F55)
    val Brass = Color(0xFFE0B465)
    val InTune = Color(0xFF7FD6A3)
    val Off = Color(0xFFF08A5D)

    val WoodLight = Color(0xFF6A4330)
    val WoodDark = Color(0xFF3B2519)
    val Fretboard = Color(0xFF1F1511)
    val Nut = Color(0xFFEDE3CF)
    val Nickel = Color(0xFFCDD0D6)
    val WoundString = Color(0xFFC9A16A)
    val PlainString = Color(0xFFDADCE1)
}

private fun outfit(weight: Int) = Font(
    R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(outfit(300), outfit(400), outfit(500), outfit(600), outfit(700))

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Brass,
            onPrimary = Palette.Background,
            background = Palette.Background,
            onBackground = Palette.Bone,
            surface = Palette.Surface,
            onSurface = Palette.Bone,
            surfaceContainerLow = Palette.Surface,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.Muted,
            outline = Palette.Outline,
            outlineVariant = Palette.Outline,
            secondaryContainer = Palette.SurfaceHigh,
            onSecondaryContainer = Palette.Bone,
        ),
        content = content,
    )
}
