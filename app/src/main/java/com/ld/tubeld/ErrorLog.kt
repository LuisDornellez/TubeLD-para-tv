package com.ld.tubeld

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de errores de la app y de la página. Se guarda en un archivo (sobrevive a cierres)
 * y se puede ver, copiar o compartir desde la app para pasárselo a una IA.
 */
object ErrorLog {
    private const val FILE = "errores.txt"
    private const val MAX = 120_000
    private const val KEEP = 60_000
    private var file: File? = null
    private var last = ""
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun install(ctx: Context) {
        file = File(ctx.filesDir, FILE)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                add("CRASH", "Hilo ${t.name}: " + android.util.Log.getStackTraceString(e).take(3000))
            } catch (_: Throwable) {
            }
            prev?.uncaughtException(t, e)
        }
    }

    @Synchronized
    fun add(tag: String, msg: String) {
        try {
            val f = file ?: return
            val line = "[$tag] " + msg.replace("\r", "").take(3000)
            if (line == last) return
            last = line
            if (f.exists() && f.length() > MAX) {
                val t = f.readText()
                f.writeText(t.takeLast(KEEP))
            }
            f.appendText(fmt.format(Date()) + " " + line + "\n")
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun clear() {
        try {
            file?.writeText("")
            last = ""
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun dump(ctx: Context): String {
        val sb = StringBuilder()
        sb.append("=== TubeLD · informe de errores ===\n")
        try {
            val v = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            sb.append("App: $v\n")
        } catch (_: Throwable) {
        }
        sb.append("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) · ")
        sb.append("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        try {
            val wv = if (android.os.Build.VERSION.SDK_INT >= 26) android.webkit.WebView.getCurrentWebViewPackage() else null
            if (wv != null) sb.append("WebView: ${wv.packageName} ${wv.versionName}\n")
        } catch (_: Throwable) {
        }
        sb.append("Script remoto: ${RemoteScript.SCRIPT_URL}\n")
        sb.append("Script remoto cargado: ${if (RemoteScript.code.isBlank()) "NO" else "sí (${RemoteScript.code.length} caracteres)"}\n")
        sb.append("Ahora: ${fmt.format(Date())}\n\n")
        try {
            val t = file?.takeIf { it.exists() }?.readText().orEmpty()
            sb.append(if (t.isBlank()) "(sin errores registrados)\n" else t.takeLast(40_000))
        } catch (_: Throwable) {
        }
        return sb.toString()
    }
}
