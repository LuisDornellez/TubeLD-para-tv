package com.ld.tubeld

/** Colores de la app en modo noche y en modo día. */
object Pal {
    class P(
        val night: Boolean,
        val bg: Int,      // fondo
        val bar: Int,     // barra de abajo
        val gold: Int,    // dorado (acento)
        val muted: Int,   // botones apagados
        val text: Int,    // texto principal
        val track: Int,   // líneas finas del ecualizador
        val knobIn: Int   // centro de las perillas del ecualizador
    )

    val NIGHT = P(
        true,
        0xFF0B0B0F.toInt(), 0xFF14141C.toInt(), 0xFFF5C451.toInt(), 0xFF7A7A88.toInt(),
        0xFFFFFFFF.toInt(), 0x33FFFFFF, 0xFF0B0B0F.toInt()
    )

    val DAY = P(
        false,
        0xFFF6F6F9.toInt(), 0xFFFFFFFF.toInt(), 0xFFB07D00.toInt(), 0xFF8A8A98.toInt(),
        0xFF14141C.toInt(), 0x26000000, 0xFFFFFFFF.toInt()
    )

    @JvmStatic
    var cur: P = NIGHT
}
