package com.example.optireader.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor
import androidx.core.view.WindowCompat
import com.example.optireader.reading.ReadingWpm
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.delay

internal data class ReaderEtaInfo(
    val chapterMinsLeft: Int,
    val bookTimeLeftText: String,
) {
    val chapterTimeLeftText: String
        get() {
            val mins = chapterMinsLeft.coerceAtLeast(0)
            val h = mins / 60
            val m = mins % 60
            return if (h > 0) {
                "${h} hrs ${m} mins left in chapter"
            } else {
                "${m} mins left in chapter"
            }
        }
}

internal fun computeReaderEtaInfo(
    wordsCount: Int,
    progress01Book: Float,
    totalUnitsBook: Int,
    validReadingSecondsForWpm: Long,
    sessionReadingSeconds: Long,
    avgWpmUser: Float,
    avgWpmBook: Float,
    remainingUnitsChapter: Int,
): ReaderEtaInfo {
    val wordsCountSafe = max(0, wordsCount)
    val totalUnitsSafe = max(1, totalUnitsBook)
    val remainingUnitsChapterSafe = max(0, remainingUnitsChapter)

    val clampedProgress = progress01Book.coerceIn(0f, 1f)
    val wordsRead = (wordsCountSafe.toFloat() * clampedProgress).toInt().coerceIn(0, wordsCountSafe)

    val secForRate = if (validReadingSecondsForWpm >= 0L) validReadingSecondsForWpm else sessionReadingSeconds
    val minutesSpent = (secForRate / 60f).coerceAtLeast(0.1f)

    val currentWpm = if (wordsRead > 0) wordsRead.toFloat() / minutesSpent else 0f
    val baselineWpm = when {
        avgWpmBook > 1f -> avgWpmBook
        avgWpmUser > 1f -> avgWpmUser
        else -> ReadingWpm.DEFAULT_WPM
    }
    val effectiveWpm = (if (currentWpm > 1f) currentWpm else baselineWpm)
        .coerceIn(ReadingWpm.WPM_MIN, ReadingWpm.WPM_MAX)

    if (effectiveWpm <= 1f || wordsCountSafe == 0) {
        return ReaderEtaInfo(
            chapterMinsLeft = 0,
            bookTimeLeftText = "0 hrs 0 mins left in book",
        )
    }

    val remainingWordsBook = (wordsCountSafe - wordsRead).coerceAtLeast(0)
    val etaMinutesBook = remainingWordsBook.toFloat() / effectiveWpm

    val wordsPerUnit = wordsCountSafe.toFloat() / totalUnitsSafe.toFloat()
    val remainingWordsChapter = wordsPerUnit * remainingUnitsChapterSafe.toFloat()
    val etaMinutesChapter = remainingWordsChapter / effectiveWpm

    val minutesLeftBookCeil = max(0, ceil(etaMinutesBook).toInt())
    val hoursLeft = minutesLeftBookCeil / 60
    val minsLeft = minutesLeftBookCeil % 60

    val chapterMinsLeft = max(0, ceil(etaMinutesChapter).toInt())
    return ReaderEtaInfo(
        chapterMinsLeft = chapterMinsLeft,
        bookTimeLeftText = "${hoursLeft} hrs ${minsLeft} mins left in book",
    )
}

internal data class DeviceStatus(
    val batteryPct: Int,
    val isCharging: Boolean,
    val time: LocalTime,
)

@Composable
internal fun rememberDeviceStatus(
    tickMillis: Long = 20_000L,
): DeviceStatus {
    val context = LocalContext.current
    val battery = remember { mutableStateOf(0 to false) }
    var now by remember { mutableStateOf(DeviceStatus(0, false, LocalTime.now())) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val pct = if (scale > 0 && level >= 0) {
                    ((level * 100) / scale).coerceIn(0, 100)
                } else {
                    0
                }
                val charging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                battery.value = pct to charging
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        if (sticky != null) receiver.onReceive(context, sticky)

        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = DeviceStatus(
                batteryPct = battery.value.first,
                isCharging = battery.value.second,
                time = LocalTime.now(),
            )
            delay(tickMillis)
        }
    }

    return now.copy(
        batteryPct = battery.value.first,
        isCharging = battery.value.second,
        time = now.time,
    )
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Matches system status / nav icon contrast to reader text: dark text → dark status icons;
 * light text → light status icons.
 *
 * When [windowBarBackgroundColor] is set, fills the status and navigation bar regions with that
 * color so edge-to-edge does not show the activity window (often black on dark theme) behind
 * transparent system bars.
 */
@Composable
internal fun ReaderStatusBarAppearanceEffect(
    contentColor: Color,
    windowBarBackgroundColor: Color? = null,
) {
    val view = LocalView.current
    DisposableEffect(contentColor, windowBarBackgroundColor) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            val darkIcons = contentColor.luminance() < 0.5f
            c.isAppearanceLightStatusBars = darkIcons
            c.isAppearanceLightNavigationBars = darkIcons
            val bg = windowBarBackgroundColor
            if (bg != null) {
                val argb = bg.toArgb()
                window.statusBarColor = argb
                window.navigationBarColor = argb
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isStatusBarContrastEnforced = false
                    window.isNavigationBarContrastEnforced = false
                }
            }
        }
        onDispose {
            val w = view.context.findActivity()?.window ?: return@onDispose
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = true
            c.isAppearanceLightNavigationBars = true
            w.statusBarColor = AndroidColor.TRANSPARENT
            w.navigationBarColor = AndroidColor.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                w.isStatusBarContrastEnforced = true
                w.isNavigationBarContrastEnforced = true
            }
        }
    }
}

/** Bottom status strip (chapter ETA, battery, time, progress). Caller may place it below the reader or overlay. */
@Composable
internal fun ReaderMinimalStatusBar(
    chapterTitle: String,
    chapterPageText: String,
    readProgress01: Float,
    etaInfo: ReaderEtaInfo,
    contentColor: Color,
    /** Matches EPUB page / PDF backdrop so the dock does not look like a separate panel. */
    backgroundColor: Color,
    controls: ReaderControlsSettings = ReaderControlsSettings(),
    modifier: Modifier = Modifier,
) {
    val device = rememberDeviceStatus()

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val pctFmt = remember { DecimalFormat("0.0") }

    val batteryPct = device.batteryPct
    val isCharging = device.isCharging
    val percentReadText = "${pctFmt.format((readProgress01.coerceIn(0f, 1f) * 100f).coerceIn(0f, 100f))}%"

    Surface(
        color = backgroundColor,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (controls.showChapterTimeLeft || controls.showBookTimeLeft) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    if (controls.showChapterTimeLeft) {
                        Text(
                            text = etaInfo.chapterTimeLeftText,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1,
                        )
                    } else {
                        SpacerWidth(0.dp)
                    }
                    if (controls.showBookTimeLeft) {
                        Text(
                            text = etaInfo.bookTimeLeftText,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (controls.showBattery) {
                        val batteryIcon = if (isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull
                        Icon(
                            imageVector = batteryIcon,
                            contentDescription = "Battery",
                            tint = contentColor,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(
                            text = "$batteryPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                        )
                    }
                    if (controls.showBattery && controls.showTime) SpacerWidth(8.dp)
                    if (controls.showTime) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = "Time",
                            tint = contentColor,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(
                            text = device.time.format(timeFmt),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }

                if (controls.showPageCount) {
                    Text(
                        text = if (chapterPageText.isNotBlank()) {
                            "$chapterTitle ($chapterPageText)"
                        } else {
                            chapterTitle
                        }.trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                } else {
                    SpacerWidth(0.dp)
                }

                if (controls.showPercentage) {
                    Text(
                        text = percentReadText,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                        maxLines = 1,
                    )
                } else {
                    SpacerWidth(0.dp)
                }
            }
        }
    }
}

@Composable
private fun SpacerWidth(width: Dp) {
    Box(modifier = Modifier.width(width))
}
