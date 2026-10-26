package com.ld.tubeld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * Puntero que se mueve con las flechas del control remoto (Smart TV).
 * Solo dibuja; la actividad decide cuándo mostrarlo y dónde está.
 */
class CursorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val d = resources.displayMetrics.density

    /** Posición de la punta del puntero, en píxeles de esta vista. */
    var px = 0f
        private set
    var py = 0f
        private set

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * d
        color = 0xFF0B0B0F.toInt()
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun setColor(c: Int) {
        fill.color = c
        invalidate()
    }

    fun moveTo(x: Float, y: Float) {
        px = x
        py = y
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val s = 26f * d
        path.reset()
        // Flecha clásica con la punta en (px, py)
        path.moveTo(px, py)
        path.lineTo(px, py + s)
        path.lineTo(px + s * 0.28f, py + s * 0.74f)
        path.lineTo(px + s * 0.50f, py + s * 1.12f)
        path.lineTo(px + s * 0.66f, py + s * 1.04f)
        path.lineTo(px + s * 0.45f, py + s * 0.67f)
        path.lineTo(px + s * 0.80f, py + s * 0.67f)
        path.close()
        canvas.drawPath(path, fill)
        canvas.drawPath(path, edge)
    }
}
