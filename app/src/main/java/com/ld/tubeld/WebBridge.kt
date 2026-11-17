package com.ld.tubeld

import android.webkit.JavascriptInterface

/** Puente por el que la página informa qué está sonando. Solo recibe datos, no ejecuta nada. */
class WebBridge {
    @JavascriptInterface
    fun update(
        title: String?,
        artist: String?,
        art: String?,
        playing: Boolean,
        posMs: Double,
        durMs: Double
    ) {
        Media.push(
            Media.State(
                (title ?: "").take(200),
                (artist ?: "").take(200),
                (art ?: "").take(500),
                playing,
                if (posMs.isFinite()) posMs.toLong().coerceAtLeast(0) else 0,
                if (durMs.isFinite()) durMs.toLong().coerceAtLeast(0) else 0
            )
        )
    }

    /** Niveles de audio para el visualizador de la notificación: 6 números 0..1 separados por coma. */
    @JavascriptInterface
    fun lv(csv: String?) {
        try {
            val p = (csv ?: return).split(",")
            if (p.size < 6) return
            val a = FloatArray(6) { (p[it].toFloatOrNull() ?: 0f).coerceIn(0f, 1f) }
            Media.levels = a
            Media.levelsAt = android.os.SystemClock.uptimeMillis()
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun dbg(msg: String?) {
        Media.onDebug?.invoke((msg ?: "").take(160))
    }

    /** Errores de JavaScript de la página: van al registro de errores. */
    @JavascriptInterface
    fun log(msg: String?) {
        ErrorLog.add("JS", (msg ?: "").take(600))
    }

    /** Ancho de la página en píxeles CSS. */
    @JavascriptInterface
    fun vw(w: Double) {
        if (w.isFinite() && w in 200.0..6000.0) Media.cssW = w
    }
}
