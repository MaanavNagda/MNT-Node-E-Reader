package com.example.optireader.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Lets horizontal swipes reach the parent while vertical drags stay for WebView scrolling if needed.
 */
class EpubSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    var onTap: (() -> Unit)? = null
    var swipeThresholdPx: Float = 48f
    var swipeThresholdBackPx: Float = 48f
    var tapSlopPx: Float = 16f

    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var trackingHorizontal = false
    private var swipeTriggered = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                moved = false
                trackingHorizontal = false
                swipeTriggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val mx = ev.x - downX
                val my = ev.y - downY
                if (!moved && (mx * mx + my * my) > tapSlopPx * tapSlopPx) moved = true
            }
            MotionEvent.ACTION_UP -> {
                // Tap has highest priority when there was no meaningful movement.
                if (!moved && !swipeTriggered) {
                    onTap?.invoke()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                trackingHorizontal = false
                swipeTriggered = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                moved = false
                trackingHorizontal = false
            }
            MotionEvent.ACTION_MOVE -> {
                val mx = ev.x - downX
                val my = ev.y - downY
                if (!moved && (mx * mx + my * my) > tapSlopPx * tapSlopPx) moved = true
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (!trackingHorizontal && dx > dy * 1.15f && dx > 20f) {
                    trackingHorizontal = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                trackingHorizontal = false
            }
            MotionEvent.ACTION_MOVE -> {
                val mx = event.x - downX
                val my = event.y - downY
                if (!moved && (mx * mx + my * my) > tapSlopPx * tapSlopPx) moved = true
                if (!trackingHorizontal) {
                    val dx = abs(mx)
                    val dy = abs(my)
                    if (dx > dy * 1.15f && dx > 20f) {
                        trackingHorizontal = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = event.x - downX
                if (!moved) {
                    onTap?.invoke()
                } else if (dx < -swipeThresholdPx) {
                    swipeTriggered = true
                    onSwipeLeft?.invoke()
                } else if (dx > swipeThresholdBackPx) {
                    swipeTriggered = true
                    onSwipeRight?.invoke()
                }
                trackingHorizontal = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
