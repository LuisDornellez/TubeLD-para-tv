package com.ld.tubeld

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Visualizador radial de colores junto al nombre de la app. Solo se anima mientras suena. */
class RadialView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val meter = RadialMeter(24)
    private val dp = resources.displayMetrics.density
    private var running = false
    private var lastFrame = 0L

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private val frame = Runnable { step() }

    private fun step() {
        running = false
        if (!isAttachedToWindow || windowVisibility != VISIBLE) return
        val playing = Media.state.playing
        val now = SystemClock.uptimeMillis()
        var rest = !playing
        if (now - lastFrame >= 16) {
            lastFrame = now
            rest = meter.update(playing)
            invalidate()
        }
        running = true
        if (!rest) postOnAnimation(frame) else postDelayed(frame, 500)
    }

    private fun kick() {
        if (!running) {
            running = true
            postOnAnimation(frame)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        kick()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        running = false
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) kick() else {
            removeCallbacks(frame)
            running = false
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rOut = min(w, h) / 2f - 1.2f * dp
        meter.draw(canvas, w / 2f, h / 2f, rOut * 0.36f, rOut, 2.2f * dp)
    }
}
