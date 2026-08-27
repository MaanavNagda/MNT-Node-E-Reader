package com.example.optireader.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.set

fun composeColorFromArgb(argb: Int): ComposeColor =
    ComposeColor(
        android.graphics.Color.red(argb) / 255f,
        android.graphics.Color.green(argb) / 255f,
        android.graphics.Color.blue(argb) / 255f,
        android.graphics.Color.alpha(argb) / 255f,
    )

private fun argbFromHsv(h: Float, s: Float, v: Float): Int =
    android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))

private fun hsvFromArgb(argb: Int): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv
}

private fun formatHexRgb(argb: Int): String {
    val r = android.graphics.Color.red(argb)
    val g = android.graphics.Color.green(argb)
    val b = android.graphics.Color.blue(argb)
    return String.format("#%02X%02X%02X", r, g, b)
}

/** Opaque RGB; returns null if invalid. Accepts `#RRGGBB`, `RRGGBB`, or `#RGB`. */
private fun parseHexRgbToArgb(input: String): Int? {
    var s = input.trim()
    if (s.startsWith('#')) s = s.substring(1)
    if (s.isEmpty()) return null
    if (s.length == 3) {
        s = buildString(6) {
            for (ch in s) append(ch).append(ch)
        }
    }
    if (s.length != 6 || s.any { !it.isDigit() && (it.lowercaseChar() !in 'a'..'f') }) {
        return null
    }
    return (0xFF000000L or s.toLong(16)).toInt()
}

/** Hue (horizontal) × value (vertical, bright at top) at fixed saturation. */
private fun buildHvPlaneBitmap(width: Int, height: Int, saturation: Float): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (width < 2 || height < 2) return bmp
    for (y in 0 until height) {
        for (x in 0 until width) {
            val h = x / (width - 1).toFloat() * 360f
            val v = 1f - y / (height - 1).toFloat()
            bmp[x, y] = argbFromHsv(h, saturation, v)
        }
    }
    return bmp
}

private fun pickHvFromOffset(width: Int, height: Int, position: Offset): Pair<Float, Float> {
    if (width < 2 || height < 2) return 0f to 0f
    val x = position.x.coerceIn(0f, width.toFloat() - 0.001f)
    val y = position.y.coerceIn(0f, height.toFloat() - 0.001f)
    val h = x / (width - 1).toFloat() * 360f
    val v = 1f - y / (height - 1).toFloat()
    return h to v
}

private fun argbToComposeColor(argb: Int): ComposeColor = composeColorFromArgb(argb)

/**
 * Color picker: **hue/value** square always at full saturation (for a stable grid), **saturation** slider
 * (continuous) only affects the preview swatch / hex / RGB and the final color.
 */
@Composable
fun HsvColorPickerContent(
    initialArgb: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember(initialArgb) { hsvFromArgb(initialArgb) }
    var hue by remember(initialArgb) { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember(initialArgb) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialArgb) { mutableFloatStateOf(initialHsv[2]) }

    var hexText by remember(initialArgb) { mutableStateOf(formatHexRgb(initialArgb)) }
    var hexFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(hue, sat, value) {
        onColorChange(argbFromHsv(hue, sat, value))
    }

    val previewArgb = argbFromHsv(hue, sat, value)
    LaunchedEffect(previewArgb, hexFieldFocused) {
        if (!hexFieldFocused) {
            hexText = formatHexRgb(previewArgb)
        }
    }

    fun applyHexFromField() {
        val parsed = parseHexRgbToArgb(hexText)
        if (parsed != null) {
            val hsv = hsvFromArgb(parsed)
            hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
            hexText = formatHexRgb(parsed)
        } else if (hexText.isNotBlank()) {
            hexText = formatHexRgb(argbFromHsv(hue, sat, value))
        }
    }

    val density = LocalDensity.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .background(composeColorFromArgb(previewArgb)),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "R ${android.graphics.Color.red(previewArgb)} · " +
                        "G ${android.graphics.Color.green(previewArgb)} · " +
                        "B ${android.graphics.Color.blue(previewArgb)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            val boxW = maxWidth
            val boxH = maxHeight
            val wPx = with(density) { maxWidth.roundToPx().coerceAtLeast(64) }
            val hPx = with(density) { maxHeight.roundToPx().coerceAtLeast(64) }
            // Full-chroma grid only; saturation is applied via the slider to the preview/output only.
            val plane = remember(wPx, hPx) { buildHvPlaneBitmap(wPx, hPx, 1f) }
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = plane.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(wPx, hPx) {
                            detectTapGestures { offset ->
                                val (h, v) = pickHvFromOffset(wPx, hPx, offset)
                                hue = h
                                value = v
                            }
                        }
                        .pointerInput(wPx, hPx) {
                            detectDragGestures { change, _ ->
                                val (h, v) = pickHvFromOffset(wPx, hPx, change.position)
                                hue = h
                                value = v
                            }
                        },
                )
                val ring = ComposeColor.White.copy(alpha = 0.95f)
                val xCenter = boxW * (hue / 360f)
                val yCenter = boxH * (1f - value)
                Box(
                    modifier = Modifier
                        .offset(
                            x = xCenter - 10.dp,
                            y = yCenter - 10.dp,
                        )
                        .size(20.dp)
                        .border(2.dp, ring, CircleShape)
                        .border(1.dp, ComposeColor.Black.copy(alpha = 0.35f), CircleShape),
                )
            }
        }

        Text(
            text = "Saturation",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            val satTrackStart = argbToComposeColor(argbFromHsv(hue, 0f, value))
            val satTrackEnd = argbToComposeColor(argbFromHsv(hue, 1f, value))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(listOf(satTrackStart, satTrackEnd)),
                    ),
            )
            Slider(
                value = sat,
                onValueChange = { sat = it },
                valueRange = 0f..1f,
                steps = 0,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = ComposeColor.White,
                    activeTrackColor = ComposeColor.Transparent,
                    inactiveTrackColor = ComposeColor.Transparent,
                ),
            )
        }

        val hexError =
            !hexFieldFocused && hexText.isNotBlank() && parseHexRgbToArgb(hexText) == null
        Text(
            text = "Hex",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        OutlinedTextField(
            value = hexText,
            onValueChange = { raw ->
                val filtered = buildString {
                    for (ch in raw) {
                        if (ch == '#' && isEmpty()) append(ch)
                        else if (ch in "0123456789abcdefABCDEF") append(ch)
                    }
                }.let { if (it.length > 7) it.take(7) else it }
                hexText = filtered
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    val focused = focusState.isFocused
                    if (hexFieldFocused && !focused) {
                        applyHexFromField()
                    }
                    hexFieldFocused = focused
                },
            label = { Text("#RRGGBB") },
            singleLine = true,
            isError = hexError,
            supportingText = {
                if (hexError) {
                    Text("Enter a valid hex color (e.g. #FF8800)")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { applyHexFromField() },
            ),
        )
    }
}

@Composable
fun HsvColorPickerDialog(
    title: String,
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var working by remember(initialArgb) { mutableStateOf(initialArgb) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            HsvColorPickerContent(
                initialArgb = initialArgb,
                onColorChange = { working = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(working) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ThemeColorSwatch(
    colorArgb: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box(
            modifier = Modifier
                .size(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .background(composeColorFromArgb(colorArgb), shape)
                .clickable(onClick = onClick),
        )
    }
}
