package com.ld.tubeld

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.graphics.Rect
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Diez barras verticales (31 Hz a 16 kHz) que se arrastran con el dedo, de -12 a +12 dB.
 * Con control remoto (Smart TV): izquierda/derecha eligen la banda, OK activa la edición,
 * arriba/abajo cambian el nivel y OK otra vez suelta el control para seguir navegando.
 */
class EqBandsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val vals = FloatArray(10)
    var onChange: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val labels = arrayOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    private val padTop = 30 * d
    private val padBottom = 24 * d

    // Manejo con flechas (control remoto)
    private var sel = 0
    private var editing = false

    // Colores según el modo día/noche (la vista se crea cada vez que se abre el ecualizador)
    private val pal = Pal.cur
    private val gold = pal.gold
    private val muted = pal.muted

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.track
        strokeWidth = 4 * d
        strokeCap = Paint.Cap.ROUND
    }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        strokeWidth = 6 * d
        strokeCap = Paint.Cap.ROUND
    }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold }
    private val knobIn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.knobIn }
    private val zero = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.track
        strokeWidth = 1 * d
    }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted
        textSize = 10 * d
        textAlign = Paint.Align.CENTER
    }
    private val txtVal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        textSize = 10 * d
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        textSize = 11 * d
        textAlign = Paint.Align.LEFT
    }
    private val focusFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (gold and 0x00FFFFFF) or 0x22000000
    }
    private val focusLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.STROKE
        strokeWidth = 2 * d
    }

    init {
        isFocusable = true
    }

    fun setValues(v: FloatArray) {
        for (i in 0 until 10) vals[i] = v.getOrElse(i) { 0f }.coerceIn(-12f, 12f)
        invalidate()
    }

    fun values(): FloatArray = vals.copyOf()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec((220 * d).toInt(), MeasureSpec.EXACTLY)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val top = padTop
        val bottom = height - padBottom
        val mid = (top + bottom) / 2f
        val half = (bottom - top) / 2f
        val col = w / 10f
        canvas.drawLine(0f, mid, w, mid, zero)
        if (isFocused) {
            rect.set(col * sel + 2 * d, top - 12 * d, col * (sel + 1) - 2 * d, height - 2 * d)
            canvas.drawRoundRect(rect, 10 * d, 10 * d, focusFill)
            canvas.drawRoundRect(rect, 10 * d, 10 * d, focusLine)
            canvas.drawText(
                if (editing) "Arriba/Abajo: nivel  ·  OK: soltar" else "Izq/Der: banda  ·  OK: editar",
                2 * d, 12 * d, hint
            )
        }
        for (i in 0 until 10) {
            val cx = col * (i + 0.5f)
            canvas.drawLine(cx, top, cx, bottom, track)
            val y = mid - vals[i] / 12f * half
            if (abs(vals[i]) > 0.01f) canvas.drawLine(cx, mid, cx, y, bar)
            canvas.drawCircle(cx, y, (if (isFocused && editing && i == sel) 13 else 10) * d, knob)
            canvas.drawCircle(cx, y, 4 * d, knobIn)
            if (abs(vals[i]) > 0.01f) {
                val s = (if (vals[i] > 0) "+" else "") + (if (vals[i] % 1f == 0f) vals[i].toInt().toString() else vals[i].toString())
                val ty = if (vals[i] > 0) y - 14 * d else y + 24 * d
                canvas.drawText(s, cx, ty, txtVal)
            }
            canvas.drawText(labels[i], cx, height - 6 * d, txt)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                var changed = false
                for (p in 0 until e.pointerCount) {
                    if (setFrom(e.getX(p), e.getY(p))) changed = true
                }
                if (changed) onChange?.invoke()
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) editing = false
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) {
                    editing = !editing
                    invalidate()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (sel > 0) {
                sel--
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (sel < 9) {
                sel++
                invalidate()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> if (editing) {
                val dir = if (keyCode == KeyEvent.KEYCODE_DPAD_UP) 1f else -1f
                val nv = (vals[sel] + dir).coerceIn(-12f, 12f)
                if (nv != vals[sel]) {
                    vals[sel] = nv
                    invalidate()
                    onChange?.invoke()
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun setFrom(x: Float, y: Float): Boolean {
        val i = (x / (width / 10f)).toInt().coerceIn(0, 9)
        val half = (height - padTop - padBottom) / 2f
        val mid = padTop + half
        var db = (mid - y) / half * 12f
        db = ((db * 2f).roundToInt() / 2f).coerceIn(-12f, 12f)
        if (db == vals[i]) return false
        vals[i] = db
        invalidate()
        return true
    }
}
