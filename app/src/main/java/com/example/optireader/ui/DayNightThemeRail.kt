package com.example.optireader.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.optireader.R

private val DaySkyTop = Color(0xFF7EC8E3)
private val DaySkyBottom = Color(0xFFB8E8D0)
private val DayHill = Color(0xFF5CB85C)

// Standard night: blue-grays and charcoal (no purple indigo)
private val NightSkyGrayTop = Color(0xFF3D4F5F)
private val NightSkyGrayBottom = Color(0xFF252F38)
private val NightHillGray = Color(0xFF3A454E)
private val NightHillGrayDeep = Color(0xFF2C343C)

// AMOLED night: black and near-black only
private val NightAmoledSky = Color.Black
private val NightAmoledSkyBottom = Color(0xFF030303)
private val NightAmoledHill = Color(0xFF0A0A0A)
private val NightAmoledHillDeep = Color(0xFF050505)

private data class NightPalette(
    val skyTop: Color,
    val skyBottom: Color,
    val hill: Color,
    val hillDeep: Color,
    val knobNight: Color,
    val iconNight: Color,
    val starAlphaMul: Float,
    /** Extra stars and galaxy streaks; false for AMOLED (minimal sky). */
    val richNightSky: Boolean,
)

private data class StarDot(val ux: Float, val uy: Float, val radius: Float, val alphaMul: Float = 1f)

private val AmoledNightStars = listOf(
    StarDot(0.12f, 0.18f, 1.8f),
    StarDot(0.35f, 0.12f, 1.8f),
    StarDot(0.58f, 0.2f, 1.8f),
    StarDot(0.88f, 0.14f, 1.8f),
    StarDot(0.72f, 0.1f, 1.8f),
)

private val RichNightStars = listOf(
    StarDot(0.06f, 0.14f, 1.1f),
    StarDot(0.11f, 0.09f, 0.9f, 0.75f),
    StarDot(0.15f, 0.2f, 1.4f),
    StarDot(0.22f, 0.11f, 1f),
    StarDot(0.28f, 0.17f, 1.2f, 0.9f),
    StarDot(0.34f, 0.08f, 0.85f, 0.65f),
    StarDot(0.4f, 0.15f, 1.6f),
    StarDot(0.48f, 0.11f, 1f),
    StarDot(0.52f, 0.19f, 1.15f),
    StarDot(0.6f, 0.08f, 0.95f, 0.8f),
    StarDot(0.65f, 0.16f, 1.3f),
    StarDot(0.72f, 0.12f, 1f),
    StarDot(0.78f, 0.2f, 1.25f, 0.85f),
    StarDot(0.84f, 0.09f, 0.9f, 0.7f),
    StarDot(0.9f, 0.15f, 1.5f),
    StarDot(0.93f, 0.07f, 0.8f, 0.6f),
    StarDot(0.18f, 0.14f, 0.85f, 0.55f),
    StarDot(0.55f, 0.14f, 1f, 0.72f),
    StarDot(0.7f, 0.07f, 0.75f, 0.5f),
    StarDot(0.25f, 0.2f, 1.1f, 0.88f),
    StarDot(0.82f, 0.18f, 1.05f),
)

private fun nightPalette(useAmoledDark: Boolean): NightPalette {
    return if (useAmoledDark) {
        NightPalette(
            skyTop = NightAmoledSky,
            skyBottom = NightAmoledSkyBottom,
            hill = NightAmoledHill,
            hillDeep = NightAmoledHillDeep,
            knobNight = Color(0xFF121212),
            iconNight = Color(0xFF78909C),
            starAlphaMul = 0.55f,
            richNightSky = false,
        )
    } else {
        NightPalette(
            skyTop = NightSkyGrayTop,
            skyBottom = NightSkyGrayBottom,
            hill = NightHillGray,
            hillDeep = NightHillGrayDeep,
            knobNight = Color(0xFF455A64),
            iconNight = Color(0xFFB0BEC5),
            starAlphaMul = 0.7f,
            richNightSky = true,
        )
    }
}

@Composable
fun DayNightThemeRail(
    useDayTheme: Boolean,
    useAmoledDark: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = if (useDayTheme) 0f else 1f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "dayNightProgress",
    )
    val dayStateLabel = stringResource(R.string.theme_state_day)
    val nightStateLabel = stringResource(R.string.theme_state_night)
    val night = remember(useAmoledDark) { nightPalette(useAmoledDark) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.drawer_day_theme),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .alpha(if (enabled) 1f else 0.45f)
                .clip(RoundedCornerShape(38.dp))
                .semantics {
                    role = Role.Switch
                    stateDescription = if (useDayTheme) dayStateLabel else nightStateLabel
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                ),
        ) {
            val knob = 54.dp
            val padH = 6.dp
            val travel = maxWidth - knob - padH * 2
            val knobOffset = padH + travel * progress

            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val topLeft = lerp(DaySkyTop, night.skyTop, progress)
                    val bottomLeft = lerp(DaySkyBottom, night.skyBottom, progress)
                    val topRight = lerp(DaySkyTop, night.skyTop, progress)
                    val bottomRight = lerp(DaySkyBottom, night.skyBottom, progress)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(topLeft, topRight, bottomRight, bottomLeft),
                            start = Offset(0f, 0f),
                            end = Offset(w, h),
                        ),
                    )

                    if (night.richNightSky && progress > 0.04f) {
                        val galaxyA = progress * 0.4f
                        fun galaxyStreak(
                            degrees: Float,
                            pivotX: Float,
                            pivotY: Float,
                            topLeftX: Float,
                            topLeftY: Float,
                            streakW: Float,
                            streakH: Float,
                        ) {
                            rotate(degrees, pivot = Offset(w * pivotX, h * pivotY)) {
                                drawRoundRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFF4A148C).copy(alpha = 0.45f * galaxyA),
                                            Color(0xFF7E57C2).copy(alpha = 0.55f * galaxyA),
                                            Color(0xFFCE93D8).copy(alpha = 0.35f * galaxyA),
                                            Color(0xFF5E35B1).copy(alpha = 0.3f * galaxyA),
                                            Color.Transparent,
                                        ),
                                        start = Offset(0f, streakH * 0.5f),
                                        end = Offset(w * streakW, streakH * 0.5f),
                                    ),
                                    topLeft = Offset(w * topLeftX, h * topLeftY),
                                    size = Size(w * streakW, h * streakH),
                                    cornerRadius = CornerRadius(h * streakH * 0.5f, h * streakH * 0.5f),
                                )
                            }
                        }
                        galaxyStreak(
                            degrees = -22f,
                            pivotX = 0.4f,
                            pivotY = 0.2f,
                            topLeftX = 0.02f,
                            topLeftY = 0.14f,
                            streakW = 0.72f,
                            streakH = 0.055f,
                        )
                        galaxyStreak(
                            degrees = 18f,
                            pivotX = 0.62f,
                            pivotY = 0.16f,
                            topLeftX = 0.28f,
                            topLeftY = 0.08f,
                            streakW = 0.55f,
                            streakH = 0.038f,
                        )
                        rotate(degrees = -6f, pivot = Offset(w * 0.5f, h * 0.11f)) {
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF311B92).copy(alpha = 0.12f * galaxyA),
                                        Color(0xFF9575CD).copy(alpha = 0.22f * galaxyA),
                                        Color(0xFFD1C4E9).copy(alpha = 0.14f * galaxyA),
                                        Color(0xFF512DA8).copy(alpha = 0.1f * galaxyA),
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(w * 0.5f, h * 0.06f),
                                ),
                                topLeft = Offset(w * 0.08f, h * 0.06f),
                                size = Size(w * 0.84f, h * 0.07f),
                                cornerRadius = CornerRadius(h * 0.035f, h * 0.035f),
                            )
                        }
                    }

                    val meadowAlpha = 1f - progress
                    if (meadowAlpha > 0.02f) {
                        val meadowPath = Path().apply {
                            moveTo(0f, h * 0.58f)
                            quadraticTo(w * 0.2f, h * 0.52f, w * 0.45f, h * 0.56f)
                            quadraticTo(w * 0.7f, h * 0.5f, w, h * 0.57f)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(
                            path = meadowPath,
                            color = Color(0xFF7FD67F).copy(alpha = meadowAlpha * 0.85f),
                        )
                        val meadowPath2 = Path().apply {
                            moveTo(0f, h * 0.68f)
                            quadraticTo(w * 0.25f, h * 0.62f, w * 0.55f, h * 0.66f)
                            quadraticTo(w * 0.8f, h * 0.6f, w, h * 0.65f)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(
                            path = meadowPath2,
                            color = Color(0xFF4CAF50).copy(alpha = meadowAlpha * 0.9f),
                        )
                    }

                    val shadowAlpha = progress
                    if (shadowAlpha > 0.02f) {
                        val nightPath = Path().apply {
                            moveTo(0f, h * 0.58f)
                            quadraticTo(w * 0.2f, h * 0.52f, w * 0.45f, h * 0.56f)
                            quadraticTo(w * 0.7f, h * 0.5f, w, h * 0.57f)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(
                            path = nightPath,
                            color = night.hill.copy(alpha = shadowAlpha * 0.75f),
                        )
                        val nightPath2 = Path().apply {
                            moveTo(0f, h * 0.68f)
                            quadraticTo(w * 0.25f, h * 0.62f, w * 0.55f, h * 0.66f)
                            quadraticTo(w * 0.8f, h * 0.6f, w, h * 0.65f)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(
                            path = nightPath2,
                            color = night.hillDeep.copy(alpha = shadowAlpha * 0.85f),
                        )
                    }

                    val starAlpha = progress * 0.9f * night.starAlphaMul
                    if (starAlpha > 0.03f) {
                        val stars = if (night.richNightSky) RichNightStars else AmoledNightStars
                        for (s in stars) {
                            drawCircle(
                                color = Color(0xFFE8EEF2).copy(alpha = starAlpha * s.alphaMul),
                                radius = s.radius,
                                center = Offset(w * s.ux, h * s.uy),
                            )
                        }
                    }

                    if (meadowAlpha > 0.15f) {
                        val flowers = listOf(
                            Offset(w * 0.15f, h * 0.72f) to Color(0xFFFFEB3B),
                            Offset(w * 0.42f, h * 0.7f) to Color(0xFFE91E63),
                            Offset(w * 0.68f, h * 0.73f) to Color(0xFFFFEB3B),
                        )
                        for ((o, c) in flowers) {
                            drawCircle(
                                color = c.copy(alpha = meadowAlpha * 0.8f),
                                radius = 2.5f,
                                center = o,
                            )
                        }
                    }
                }

                val knobNight = night.knobNight
                val iconNight = night.iconNight
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = knobOffset, top = 10.dp, bottom = 10.dp)
                        .size(knob)
                        .shadow(5.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            lerp(
                                Color(0xFFFFFDE7),
                                knobNight,
                                progress,
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier
                            .size(30.dp)
                            .alpha(1f - progress),
                    )
                    Icon(
                        imageVector = Icons.Filled.DarkMode,
                        contentDescription = null,
                        tint = iconNight,
                        modifier = Modifier
                            .size(28.dp)
                            .alpha(progress),
                    )
                }
            }
        }
    }
}
