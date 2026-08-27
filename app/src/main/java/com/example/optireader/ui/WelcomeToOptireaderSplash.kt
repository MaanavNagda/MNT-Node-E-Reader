package com.example.optireader.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.optireader.R
import kotlinx.coroutines.delay

private const val WelcomeHoldMs = 2000L
private const val WelcomeAnimMs = 650

/**
 * Shown when the library and TBR have no books; calls [onFinished] when the intro animation ends.
 */
@Composable
fun WelcomeToOptireaderSplash(
    onFinished: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(WelcomeHoldMs)
        onFinished()
    }
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(WelcomeAnimMs, easing = FastOutSlowInEasing),
        label = "welcomeAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(WelcomeAnimMs, easing = FastOutSlowInEasing),
        label = "welcomeScale",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            },
        ) {
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
