package com.example.optireader.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Wall color behind the app. Uses the same hues as [WoodPanelBaseDay] / [WoodPanelBaseNight] so
 * list insets and non-library screens do not show a contrasting frame next to the wood panels.
 */
@Composable
fun ShelfBackground(
    isDay: Boolean,
    isAmoledDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when {
        isAmoledDark -> Color.Black
        isDay -> WoodPanelBaseDay
        else -> WoodPanelBaseNight
    }
    Box(modifier.fillMaxSize().background(color))
}
