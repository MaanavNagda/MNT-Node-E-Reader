package com.example.optireader.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/** Matches [drawProceduralWoodGrain] bases so list padding / gaps do not flash a different color. */
val WoodPanelBaseDay: Color = Color(0xFFF5E8DC)
val WoodPanelBaseNight: Color = Color(0xFF6E5040)

/**
 * Canvas-drawn wood grain (no bitmap). Used behind each library shelf row so the texture
 * scrolls with the list.
 */
fun DrawScope.drawProceduralWoodGrain(
    isDay: Boolean,
    isAmoled: Boolean,
    seed: Int,
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    val base = when {
        isAmoled -> Color.Black
        isDay -> WoodPanelBaseDay
        else -> WoodPanelBaseNight
    }
    drawRect(base)

    val r = Random(seed.toLong())
    val plankH = 22.dp.toPx()

    if (!isAmoled) {
        val strandCount = (w / 1.4f).toInt().coerceIn(40, 220)
        repeat(strandCount) {
            val x0 = r.nextFloat() * w
            val alpha = r.nextFloat() * 0.22f + 0.08f
            val stroke = r.nextFloat() * 1.6f + 0.55f
            val drift = r.nextFloat() * 10f - 5f
            val c = if (isDay) Color(0xFF4A3020).copy(alpha = alpha) else Color.Black.copy(alpha = alpha)
            drawLine(
                color = c,
                start = Offset(x0, 0f),
                end = Offset(x0 + drift, h),
                strokeWidth = stroke,
            )
        }
        repeat(strandCount / 2) {
            val x0 = r.nextFloat() * w
            val alpha = r.nextFloat() * 0.1f + 0.04f
            val c = if (isDay) Color(0xFFFFFAF2).copy(alpha = alpha) else Color(0xFF8B7355).copy(alpha = alpha)
            drawLine(
                color = c,
                start = Offset(x0, 0f),
                end = Offset(x0 + r.nextFloat() * 4f - 2f, h),
                strokeWidth = r.nextFloat() * 0.55f + 0.2f,
            )
        }
        repeat((h / 12f).toInt().coerceIn(10, 72)) {
            val y = r.nextFloat() * h
            val alpha = r.nextFloat() * 0.14f + 0.05f
            val c = if (isDay) Color(0xFF7A5520).copy(alpha = alpha) else Color(0xFF1A0E08).copy(alpha = alpha)
            drawLine(
                color = c,
                start = Offset(0f, y),
                end = Offset(w, y + r.nextFloat() * 3.5f - 1.75f),
                strokeWidth = r.nextFloat() * 1.1f + 0.25f,
            )
        }
        repeat((w * h / 9000f).toInt().coerceIn(8, 48)) {
            val cx = r.nextFloat() * w
            val cy = r.nextFloat() * h
            val rx = r.nextFloat() * 18f + 6f
            val ry = r.nextFloat() * 10f + 4f
            drawOval(
                color = Color.Black.copy(alpha = if (isDay) 0.06f else 0.12f),
                topLeft = Offset(cx - rx, cy - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = r.nextFloat() * 1.2f + 0.4f),
            )
        }
        val waveSteps = (w / 5f).toInt().coerceAtLeast(12)
        var px = 0f
        var py = r.nextFloat() * h * 0.5f + h * 0.15f
        val step = w / waveSteps
        repeat(waveSteps - 1) {
            val nx = px + step
            val ny = (
                py + sin((seed + it).toDouble() * 0.7).toFloat() * (if (isDay) 7f else 6f) +
                    r.nextFloat() * 4f - 2f
                ).coerceIn(0f, h)
            drawLine(
                color = Color.Black.copy(alpha = if (isDay) 0.09f else 0.14f),
                start = Offset(px, py),
                end = Offset(nx, ny),
                strokeWidth = 1.8f,
            )
            px = nx
            py = ny
        }
        repeat(6) {
            val angle = r.nextFloat() * 0.4f - 0.2f
            val y0 = r.nextFloat() * h
            val len = w * 0.85f
            drawLine(
                color = Color.Black.copy(alpha = if (isDay) 0.05f else 0.09f),
                start = Offset(r.nextFloat() * w * 0.1f, y0),
                end = Offset(r.nextFloat() * w * 0.1f + len, y0 + len * angle),
                strokeWidth = 2.2f,
            )
        }
    } else {
        repeat((w / 5f).toInt().coerceIn(16, 120)) {
            val x = r.nextFloat() * w
            drawLine(
                color = Color.White.copy(alpha = r.nextFloat() * 0.09f + 0.04f),
                start = Offset(x, 0f),
                end = Offset(x + r.nextFloat() * 3f - 1.5f, h),
                strokeWidth = r.nextFloat() * 1f + 0.45f,
            )
        }
    }

    val plankTop = h - plankH
    val plankShade = when {
        isAmoled -> Color.White.copy(alpha = 0.06f)
        isDay -> Color(0xFF3D2818).copy(alpha = 0.14f)
        else -> Color.Black.copy(alpha = 0.22f)
    }
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to plankShade,
        ),
        topLeft = Offset(0f, plankTop),
        size = Size(w, plankH),
    )
    drawLine(
        color = Color.White.copy(
            alpha = when {
                isAmoled -> 0.06f
                isDay -> 0.1f
                else -> 0.14f
            },
        ),
        start = Offset(0f, plankTop),
        end = Offset(w, plankTop),
        strokeWidth = 1f,
    )
}

fun Modifier.woodGrainShelfRow(
    isDay: Boolean,
    isAmoled: Boolean,
    rowSeed: Int,
): Modifier = drawBehind {
    drawProceduralWoodGrain(isDay = isDay, isAmoled = isAmoled, seed = rowSeed)
}
