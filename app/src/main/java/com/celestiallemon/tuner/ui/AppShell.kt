package com.celestiallemon.tuner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

enum class Tab(val label: String, val icon: ImageVector) {
    Tuner("Tuner", Icons.Rounded.GraphicEq),
    Metronome("Metronome", Icons.Rounded.Metronome),
    Repertoire("Repertoire", Icons.AutoMirrored.Rounded.LibraryBooks),
}

/**
 * The app frame: bottom navigation and a fade-through between tabs (with a slight drift in the direction
 * of travel). Each tab keeps its scroll position and other UI state while you're on another tab.
 */
@Composable
fun AppShell(
    tab: Tab,
    onTab: (Tab) -> Unit,
    metronomePlaying: Boolean,
    songsLeftToday: Int,
    content: @Composable (Tab) -> Unit,
) {
    // Keep the screen awake while tuning or while the metronome runs.
    val view = LocalView.current
    val keepOn = tab == Tab.Tuner || metronomePlaying
    SideEffect { view.keepScreenOn = keepOn }
    val saveable = rememberSaveableStateHolder()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                for (item in Tab.entries) {
                    val selected = item == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onTab(item) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    when {
                                        item == Tab.Metronome && metronomePlaying && !selected -> PlayingDot()
                                        item == Tab.Repertoire && songsLeftToday > 0 -> Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ) { Text("$songsLeftToday") }
                                    }
                                },
                            ) {
                                val iconLabel = when {
                                    item == Tab.Repertoire && songsLeftToday > 0 -> "${item.label}, $songsLeftToday songs left to practice today"
                                    else -> null
                                }
                                Icon(item.icon, iconLabel)
                            }
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (
                    fadeIn(tween(220, delayMillis = 70, easing = Motion.Emphasized)) +
                        scaleIn(tween(260, delayMillis = 70, easing = Motion.Emphasized), initialScale = 0.97f) +
                        slideInHorizontally(tween(300, easing = Motion.Emphasized)) { direction * it / 14 }
                    ) togetherWith fadeOut(tween(90))
            },
            label = "tabs",
        ) { shown ->
            saveable.SaveableStateProvider(shown.name) { content(shown) }
        }
    }
}

/** A small brass dot that breathes while the metronome plays on another tab. */
@Composable
private fun PlayingDot() {
    val transition = rememberInfiniteTransition(label = "playingDot")
    val alpha by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dotAlpha")
    // A dark ring keeps the dot readable on top of the icon.
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(2.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Palette.Brass),
    )
}
