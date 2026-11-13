package com.celestiallemon.tuner.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.celestiallemon.tuner.R
import com.celestiallemon.tuner.repertoire.Level

/*
 * Fretwise design system.
 *
 * A dark, warm "practice room at night" theme built on Material 3. Colours come from the instrument
 * (rosewood, walnut, bone, brass, nickel). The repertoire levels run from hot to cool: a song you just
 * picked up glows like an ember, and by the time it reaches Memory it has cooled to a calm lake blue.
 */

/** Raw colour tokens. Prefer the Material roles (MaterialTheme.colorScheme) or the semantic helpers below. */
object Palette {
    val Background = Color(0xFF16110F)
    val Surface = Color(0xFF221A16)
    val SurfaceHigh = Color(0xFF30251F)
    val SurfaceHighest = Color(0xFF3B2E27)
    val Outline = Color(0xFF4A3B33)
    val Bone = Color(0xFFF3E7D3)
    val Muted = Color(0xFFA8978A)
    val Faint = Color(0xFF6E5F55)
    val Brass = Color(0xFFE0B465)
    val BrassDeep = Color(0xFF5A4520)
    val InTune = Color(0xFF7FD6A3)
    val Off = Color(0xFFF08A5D)
    val Danger = Color(0xFFF2856F)

    // Level ramp, hot to cool.
    val Ember = Color(0xFFF08A5D)
    val Amber = Color(0xFFE8B25C)
    val Sage = Color(0xFFA8CC7E)
    val Lake = Color(0xFF86B9DE)

    val WoodLight = Color(0xFF6A4330)
    val WoodDark = Color(0xFF3B2519)
    val Fretboard = Color(0xFF1F1511)
    val Nut = Color(0xFFEDE3CF)
    val Nickel = Color(0xFFCDD0D6)
    val WoundString = Color(0xFFC9A16A)
    val PlainString = Color(0xFFDADCE1)
}

/** The accent colour of each repertoire level. */
val Level.color: Color
    get() = when (this) {
        Level.L1 -> Palette.Ember
        Level.L2 -> Palette.Amber
        Level.L3 -> Palette.Sage
        Level.Memory -> Palette.Lake
    }

/** A tinted surface for containers that belong to a level (badges, selected rows). */
val Level.container: Color get() = color.copy(alpha = 0.16f)

private fun outfit(weight: Int) = Font(
    R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Outfit: geometric and friendly, used for numbers, headings and anything big. Body text stays on the system font. */
val Display = FontFamily(outfit(300), outfit(400), outfit(500), outfit(600), outfit(700))

val FretwiseTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 84.sp, lineHeight = 84.sp, letterSpacing = (-3).sp),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    /** Used as an overline: small caps-style section labels. */
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.6.sp),
)

val FretwiseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

/**
 * Motion tokens. Things that move across the screen use springs so they can be interrupted mid-flight
 * and still feel physical; colour and opacity changes use short eased tweens.
 */
object Motion {
    /** M3 "emphasized" curve, for things entering the screen. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Position and size: a touch of overshoot. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.72f, stiffness = 420f)

    /** Quick spatial moves for small elements (thumbs, indicators). */
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.68f, stiffness = 900f)

    /** Bouncy pops for confirmations (a tick, a beat). */
    fun <T> pop(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 600f)

    /** Colour and opacity. */
    fun <T> effects(durationMillis: Int = 220): FiniteAnimationSpec<T> = tween(durationMillis, easing = Emphasized)

    val slide: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.85f, stiffness = 500f, visibilityThreshold = IntOffset(1, 1))
}

@Composable
fun FretwiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Brass,
            onPrimary = Palette.Background,
            primaryContainer = Palette.BrassDeep,
            onPrimaryContainer = Color(0xFFFFE2B0),
            secondary = Palette.Bone,
            onSecondary = Palette.Background,
            secondaryContainer = Palette.SurfaceHighest,
            onSecondaryContainer = Palette.Bone,
            tertiary = Palette.InTune,
            onTertiary = Palette.Background,
            background = Palette.Background,
            onBackground = Palette.Bone,
            surface = Palette.Background,
            onSurface = Palette.Bone,
            surfaceVariant = Palette.SurfaceHigh,
            onSurfaceVariant = Palette.Muted,
            surfaceContainerLowest = Palette.Background,
            surfaceContainerLow = Palette.Surface,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.SurfaceHigh,
            surfaceContainerHighest = Palette.SurfaceHighest,
            inverseSurface = Palette.Bone,
            inverseOnSurface = Palette.Background,
            inversePrimary = Palette.BrassDeep,
            outline = Palette.Outline,
            outlineVariant = Color(0xFF3A2E28),
            error = Palette.Danger,
            onError = Palette.Background,
            scrim = Color(0xFF000000),
        ),
        typography = FretwiseTypography,
        shapes = FretwiseShapes,
        content = content,
    )
}
