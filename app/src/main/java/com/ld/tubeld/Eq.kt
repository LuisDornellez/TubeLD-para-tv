package com.ld.tubeld

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Ajustes del ecualizador y los modos prediseñados. */
object Eq {

    class Cfg(
        var on: Boolean = false,
        var bands: FloatArray = FloatArray(10),
        var bass: Int = 0,
        var wide: Int = 0,
        var clarity: Int = 0,
        var level: Boolean = false,
        var preset: String = "Plano",
        var buf: Int = 1
    ) {
        fun applyPreset(p: Preset) {
            bands = p.bands.copyOf()
            bass = p.bass
            wide = p.wide
            clarity = p.clarity
            level = p.level
            preset = p.name
        }
    }

    class Preset(
        val name: String,
        val bands: FloatArray,
        val bass: Int,
        val wide: Int,
        val clarity: Int,
        val level: Boolean
    )

    val PRESETS = listOf(
        Preset("Plano", FloatArray(10), 0, 0, 0, false),
        Preset("Cine", floatArrayOf(5f, 4f, 2f, 0f, -1f, 0f, 1.5f, 3f, 3.5f, 3f), 40, 70, 30, false),
        Preset("Música", floatArrayOf(3f, 2.5f, 1f, 0f, -0.5f, -0.5f, 0f, 1.5f, 2.5f, 3f), 20, 30, 20, false),
        Preset("Voz", floatArrayOf(-3f, -2f, -1f, 0f, 2f, 3.5f, 3.5f, 2f, 0f, -1f), 0, 0, 50, true),
        Preset("Graves", floatArrayOf(7f, 6f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f), 60, 20, 0, false),
        Preset("Noche", floatArrayOf(2f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -1f), 10, 0, 10, true)
    )

    /** JSON solo con números y booleanos: se pega tal cual dentro del JavaScript de la página. */
    fun toJs(c: Cfg): String {
        val o = JSONObject()
        o.put("on", c.on)
        val a = JSONArray()
        for (b in c.bands) a.put(b.toDouble())
        o.put("bands", a)
        o.put("bass", c.bass)
        o.put("wide", c.wide)
        o.put("clarity", c.clarity)
        o.put("level", c.level)
        o.put("buf", c.buf)
        return o.toString()
    }

    fun save(p: SharedPreferences, c: Cfg) {
        val o = JSONObject(toJs(c))
        o.put("preset", c.preset)
        p.edit().putString("eq", o.toString()).apply()
    }

    fun load(p: SharedPreferences): Cfg {
        val c = Cfg()
        try {
            val s = p.getString("eq", null) ?: return c
            val o = JSONObject(s)
            c.on = o.optBoolean("on", false)
            val a = o.optJSONArray("bands")
            if (a != null && a.length() == 10) {
                c.bands = FloatArray(10) { a.optDouble(it, 0.0).toFloat().coerceIn(-12f, 12f) }
            }
            c.bass = o.optInt("bass", 0).coerceIn(0, 100)
            c.wide = o.optInt("wide", 0).coerceIn(0, 100)
            c.clarity = o.optInt("clarity", 0).coerceIn(0, 100)
            c.level = o.optBoolean("level", false)
            c.preset = o.optString("preset", "Plano")
            c.buf = o.optInt("buf", 1).coerceIn(0, 2)
        } catch (_: Exception) {
        }
        return c
    }
}
