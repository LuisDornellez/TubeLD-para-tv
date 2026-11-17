package com.ld.tubeld

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Script remoto: se descarga de un repositorio PUBLICO de GitHub y se guarda en el equipo.
 * Asi se puede corregir el comportamiento de la pagina editando el archivo en GitHub
 * (desde el navegador), sin recompilar ni reinstalar la app.
 *
 * - Al abrir la app se usa la ultima copia guardada (funciona sin internet).
 * - En segundo plano se baja la version nueva; se aplica al recargar la pagina / reabrir la app.
 * - Si la descarga falla, no pasa nada: se sigue con la copia guardada o con el script interno.
 */
object RemoteScript {
    /** CAMBIA ESTO si tu repositorio publico tiene otro nombre: usuario/repositorio/rama/archivo */
    const val SCRIPT_URL = "https://raw.githubusercontent.com/LuisDornellez/TubeLD-script/main/tubeld.js"

    private const val FILE = "remote_tubeld.js"
    private const val MAX_BYTES = 400_000

    @Volatile
    var code: String = ""
        private set

    fun load(ctx: Context) {
        try {
            val f = File(ctx.filesDir, FILE)
            if (f.exists() && f.length() in 1..MAX_BYTES) code = f.readText()
        } catch (_: Throwable) {
        }
    }

    /** Descarga en un hilo aparte. onNew se llama (hilo secundario) solo si hubo cambios. */
    fun refresh(ctx: Context, onNew: () -> Unit) {
        Thread {
            try {
                val c = URL(SCRIPT_URL + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.useCaches = false
                c.setRequestProperty("Cache-Control", "no-cache")
                if (c.responseCode != 200) return@Thread
                val text = c.inputStream.use { it.readBytes() }
                if (text.isEmpty() || text.size > MAX_BYTES) return@Thread
                val s = String(text, Charsets.UTF_8)
                // Control basico: debe parecer JavaScript, no una pagina de error HTML
                if (s.trimStart().startsWith("<")) return@Thread
                if (s == code) return@Thread
                File(ctx.filesDir, FILE).writeText(s)
                code = s
                onNew()
            } catch (_: Throwable) {
            }
        }.start()
    }

    /** Envuelto en try/catch para que un error en el script remoto nunca rompa el resto. */
    fun wrapped(): String {
        val c = code
        if (c.isBlank()) return ""
        return "try{\n$c\n}catch(e){try{console.log('tubeld remoto: '+e)}catch(_){}}\n"
    }
}
