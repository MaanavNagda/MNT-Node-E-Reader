package com.example.optireader.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import android.view.KeyEvent as AndroidKeyEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun Modifier.readerEdgeSlideControls(
    context: Context,
    controls: ReaderControlsSettings,
    onVolumeChangedPercent: (Int) -> Unit = {},
): Modifier {
    if (!controls.leftSlideBrightness && !controls.rightSlideVolume) return this
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val activity = context.findActivity()
    return this.pointerInput(controls.leftSlideBrightness, controls.rightSlideVolume) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var mode = 0 // 1=brightness, 2=volume
            run {
                val w = size.width.toFloat().coerceAtLeast(1f)
                val edge = w * 0.22f
                mode = when {
                    controls.leftSlideBrightness && down.position.x <= edge -> 1
                    controls.rightSlideVolume && down.position.x >= w - edge -> 2
                    else -> 0
                }
            }
            var accumDy = 0f
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (mode == 0) continue
                val delta = change.positionChange()
                if (abs(delta.y) < abs(delta.x)) continue
                change.consume()
                accumDy += delta.y
                val threshold = 36f
                while (abs(accumDy) >= threshold) {
                    val up = accumDy < 0f
                    when (mode) {
                        1 -> {
                            val act = activity ?: break
                            val lp = act.window.attributes
                            val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                            val next = (cur + if (up) 0.03f else -0.03f).coerceIn(0.02f, 1f)
                            lp.screenBrightness = next
                            act.window.attributes = lp
                        }
                        2 -> {
                            val dir = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                            audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
                            val cur = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            val max = (audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1).coerceAtLeast(1)
                            onVolumeChangedPercent(((cur * 100f) / max).toInt().coerceIn(0, 100))
                        }
                    }
                    accumDy += if (up) threshold else -threshold
                }
            }
        }
    }
}

@Composable
fun ReaderVolumeKeysInterceptor(
    controls: ReaderControlsSettings,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val requester = remember { FocusRequester() }
    var lastVolumeTurnUptimeMs by remember { mutableLongStateOf(0L) }
    var turnJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { requester.requestFocus() }
    val mod = Modifier
        .focusRequester(requester)
        .focusable()
        .onPreviewKeyEvent { evt ->
            if (!controls.volumeKeysPageTurn || evt.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val code = evt.nativeKeyEvent.keyCode
            val isUp = code == AndroidKeyEvent.KEYCODE_VOLUME_UP
            val isDown = code == AndroidKeyEvent.KEYCODE_VOLUME_DOWN
            if (!isUp && !isDown) return@onPreviewKeyEvent false
            val now = SystemClock.uptimeMillis()
            val minIntervalMs = 333L // Hard cap: 3 page turns per second while holding key.
            val speedMs = controls.volumeKeyAnimationSpeedMs
                .coerceIn(READER_VOLUME_KEY_ANIMATION_SPEED_MIN_MS, READER_VOLUME_KEY_ANIMATION_SPEED_MAX_MS)
                .toLong()
            val effectiveMinGapMs = maxOf(minIntervalMs, speedMs)
            val last = lastVolumeTurnUptimeMs
            if (last > 0L && now - last < effectiveMinGapMs) return@onPreviewKeyEvent true
            if (turnJob?.isActive == true) return@onPreviewKeyEvent true
            val forwardIsUp = controls.volumeUpForward
            val forward = (isUp && forwardIsUp) || (isDown && !forwardIsUp)
            turnJob = scope.launch {
                if (forward) onForward() else onBackward()
                lastVolumeTurnUptimeMs = SystemClock.uptimeMillis()
                // Keep key-driven turns on the same cadence as visible swipe-like page animation.
                delay(effectiveMinGapMs)
            }
            true
        }
    content(mod)
}

