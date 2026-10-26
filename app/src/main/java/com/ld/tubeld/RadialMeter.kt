package com.ld.tubeld

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Visualizador radial: rayitas redondeadas en círculo, en la paleta Esmeralda (menta, verde y
 * petróleo) que fluye despacio mientras suena. Cada rayita crece con la música; en reposo queda quieto y pequeño.
 * Lo usan la barra superior (RadialView) y la carátula de la notificación.
 */
class RadialMeter(private val n: Int = 24) {

    private val rest = 0.14f
    private val fill = FloatArray(n) { rest }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val stops = intArrayOf(0xFFA7F3D0.toInt(), 0xFF34D399.toInt(), 0xFF0E9F9A.toInt())
    private var hue = 0f
    private var lastT = 0L

    fun reset() {
        for (i in 0 until n) fill[i] = rest
    }

    /** Avanza la animación. Devuelve true si ya está en reposo (no hace falta animar). */
    fun update(playing: Boolean): Boolean {
        val now = SystemClock.uptimeMillis()
        val steps = ((now - lastT).coerceIn(16L, 300L)) / 16f
        lastT = now
        val real = now - Media.levelsAt < 700
        val t = now / 1000.0
        val half = n / 2
        var settled = true
        for (i in 0 until n) {
            val k = if (i < half) i else n - 1 - i
            val target = if (!playing) rest else {
                val lv = if (real) {
                    val pos = k.toFloat() / (half - 1) * 5f
                    val i0 = floor(pos).toInt().coerceIn(0, 5)
                    val i1 = min(5, i0 + 1)
                    val fr = pos - i0
                    Media.levels[i0] * (1f - fr) + Media.levels[i1] * fr
                } else {
                    (0.30 + 0.55 * abs(sin(t * (2.1 + k * 0.35) + k * 1.3)) *
                        (0.6 + 0.4 * sin(t * 0.9 + k))).toFloat()
                }
                rest + (1f - rest) * lv.coerceIn(0f, 1f)
            }
            val k0 = if (target > fill[i]) 0.55f else 0.16f
            fill[i] += (target - fill[i]) * (1f - (1f - k0).pow(steps))
            if (!playing) {
                if (abs(fill[i] - rest) < 0.005f) fill[i] = rest else settled = false
            }
        }
        if (playing) hue = (hue + 0.6f * steps) % 360f
        return !playing && settled
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float, rIn: Float, rOut: Float, stroke: Float) {
        paint.strokeWidth = stroke
        val maxLen = rOut - rIn
        for (i in 0 until n) {
            val f = fill[i].coerceIn(0f, 1f)
            val a = i * 6.2831855f / n - 1.5707964f
            // Color que fluye por el círculo y vuelve al inicio sin saltos (onda triangular).
            val u = (i.toFloat() / n + hue / 360f) % 1f
            val sp = (1f - abs(2f * u - 1f)) * (stops.size - 1)
            val i0 = min(stops.size - 2, floor(sp).toInt())
            paint.color = ColorUtils.blendARGB(stops[i0], stops[i0 + 1], sp - i0)
            paint.alpha = (150 + 105 * f).toInt()
            val len = maxLen * f
            val c = cos(a)
            val s = sin(a)
            canvas.drawLine(cx + c * rIn, cy + s * rIn, cx + c * (rIn + len), cy + s * (rIn + len), paint)
        }
    }
}
