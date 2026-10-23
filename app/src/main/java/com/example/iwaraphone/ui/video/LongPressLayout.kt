package com.example.iwaraphone.ui.video

import android.content.Context
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * 包在 PlayerView 外层，识别长按（不拦截子 View 的普通点击）。
 * 长按结束时给子 View 发 CANCEL，避免松手被当成一次点击去切换控制栏。
 */
class LongPressLayout(ctx: Context) : FrameLayout(ctx) {
    var canStart: (MotionEvent) -> Boolean = { true }
    var onStart: () -> Unit = {}
    var onEnd: () -> Unit = {}
    private var active = false

    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (!canStart(e)) return
            active = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onStart()
        }
    })

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        val finished = ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL
        if (active && finished) {
            active = false
            onEnd()
            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            return true
        }
        if (active) return true
        super.dispatchTouchEvent(ev)
        return true
    }
}
