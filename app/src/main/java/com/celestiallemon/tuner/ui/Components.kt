package com.celestiallemon.tuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.celestiallemon.tuner.repertoire.Level

/** Shrinks slightly while pressed and springs back, so taps feel physical. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.94f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) pressed else 1f, Motion.spatialFast(), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Small overline text above a group of content. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

/** Big screen title with optional trailing actions. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 8.dp).height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
        actions()
    }
}

/** A coloured pill naming a repertoire level. */
@Composable
fun LevelBadge(level: Level, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(level.container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(level.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = level.color)
    }
}

/**
 * A pill-shaped single-choice control. The selected highlight slides between options on a spring
 * instead of jumping.
 */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
) {
    BoxWithConstraints(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val index = options.indexOf(selected).coerceAtLeast(0)
        val offset by animateDpAsState(segment * index, Motion.spatial(), label = "segment")
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            for (option in options) {
                val isSelected = option == selected
                val color by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    Motion.effects(),
                    label = "segmentText",
                )
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(
                            selected = isSelected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .pressScale(interaction, 0.9f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label(option), style = MaterialTheme.typography.titleMedium, fontFamily = Display, color = color)
                }
            }
        }
    }
}

/** An empty-state block: a big soft icon, a title and a line of guidance. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action()
    }
}

/** A pendulum metronome, drawn to match the rounded Material icons. */
val Icons.Rounded.Metronome: ImageVector by lazy {
    ImageVector.Builder("Metronome", 24.dp, 24.dp, 24f, 24f).apply {
        // Body: a trapezoid with a rounded top.
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(10.2f, 3f)
            lineTo(13.8f, 3f)
            lineTo(18.6f, 20f)
            lineTo(5.4f, 20f)
            close()
        }
        // Pendulum arm from the pivot near the base, tilted right.
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 16.5f)
            lineTo(16.2f, 5.2f)
        }
        // Sliding weight on the arm.
        path(fill = SolidColor(Color.Black)) {
            moveTo(16.4f, 10.4f)
            arcTo(1.9f, 1.9f, 0f, true, true, 12.6f, 10.4f)
            arcTo(1.9f, 1.9f, 0f, true, true, 16.4f, 10.4f)
            close()
        }
    }.build()
}
