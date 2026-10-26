package com.ld.tubeld

/** Estado del reproductor y comandos entre la página (WebView), la actividad y la notificación. */
object Media {
    const val PLAY = "play"
    const val PAUSE = "pause"
    const val NEXT = "next"
    const val PREV = "prev"
    const val SEEK = "seek"
    const val CLOSE = "close"

    data class State(
        val title: String = "",
        val artist: String = "",
        val artUrl: String = "",
        val playing: Boolean = false,
        val posMs: Long = 0,
        val durMs: Long = 0
    )

    @Volatile var state = State()

    /** La pone la actividad: ejecuta el comando dentro de la página. */
    @Volatile var onCommand: ((String, Long) -> Unit)? = null

    /** La pone el servicio: se entera de los cambios que informa la página. */
    @Volatile var onState: ((State) -> Unit)? = null

    /** La pone la actividad: muestra un aviso con información de diagnóstico que llega de la página. */
    @Volatile var onDebug: ((String) -> Unit)? = null

    /** Niveles (0..1) de 6 bandas que mide la página cuando el ecualizador está encendido. */
    @Volatile var levels = FloatArray(6)
    @Volatile var levelsAt = 0L

    fun send(cmd: String, arg: Long = 0) {
        onCommand?.invoke(cmd, arg)
    }

    fun push(s: State) {
        state = s
        onState?.invoke(s)
    }

    fun reset() {
        state = State()
    }
}
