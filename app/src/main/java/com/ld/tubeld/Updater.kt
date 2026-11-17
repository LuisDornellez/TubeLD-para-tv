package com.ld.tubeld

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Actualizaciones dentro de la app.
 * - Lee version.json del repositorio público (INFO_URL).
 * - Si hay versión nueva: avisa dentro de la app y con una notificación.
 * - Descarga el APK y abre el instalador de Android (Android siempre pide confirmar con "Instalar").
 */
object Updater {
    /** Lo genera solo el workflow al compilar y lo sube a la release apk-latest (el repositorio debe ser público). */
    const val INFO_URL = "https://github.com/LuisDornellez/TubeLD/releases/download/apk-latest/version.json"

    private const val CHANNEL = "tubeld_update"
    private const val JOB_ID = 4711
    private const val MAX_APK = 250_000_000L

    data class Info(val code: Long, val name: String, val apk: String, val notes: String)

    @Suppress("DEPRECATION")
    private fun codeOf(pi: android.content.pm.PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()

    fun installedCode(ctx: Context): Long = try {
        codeOf(ctx.packageManager.getPackageInfo(ctx.packageName, 0))
    } catch (_: Throwable) {
        0L
    }

    fun installedName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Throwable) {
        "?"
    }

    private fun trusted(u: String): Boolean = try {
        val x = Uri.parse(u)
        val h = x.host ?: ""
        x.scheme == "https" && (h == "github.com" || h.endsWith(".github.com") ||
            h == "githubusercontent.com" || h.endsWith(".githubusercontent.com"))
    } catch (_: Throwable) {
        false
    }

    /** Bloqueante: llamar desde un hilo. Devuelve la info si hay versión MÁS NUEVA, si no null. */
    fun fetchNewer(ctx: Context): Info? {
        return try {
            val c = URL(INFO_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.useCaches = false
            c.setRequestProperty("Cache-Control", "no-cache")
            if (c.responseCode != 200) {
                ErrorLog.add("ACTUALIZAR", "version.json: HTTP ${c.responseCode} (el repositorio debe ser público y la release apk-latest debe incluir version.json)")
                return null
            }
            val j = JSONObject(c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
            val info = Info(
                j.optLong("versionCode", 0),
                j.optString("versionName", "").take(30),
                j.optString("apkUrl", ""),
                j.optString("notes", "").take(500)
            )
            if (info.code <= installedCode(ctx)) return null
            if (!trusted(info.apk)) {
                ErrorLog.add("ACTUALIZAR", "apkUrl no permitida: ${info.apk.take(80)}")
                return null
            }
            info
        } catch (e: Throwable) {
            ErrorLog.add("ACTUALIZAR", "No se pudo buscar actualización: $e")
            null
        }
    }

    fun check(ctx: Context, done: (Info?) -> Unit) {
        Thread { done(fetchNewer(ctx)) }.start()
    }

    // ---------- notificación ----------

    fun notifyNew(ctx: Context, info: Info) {
        try {
            val sp = ctx.getSharedPreferences("upd", Context.MODE_PRIVATE)
            if (sp.getLong("notified", 0) == info.code) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Actualizaciones", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            if (!nm.areNotificationsEnabled()) return
            val open = Intent(ctx, MainActivity::class.java)
                .putExtra("update", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(
                ctx, 77, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_headphones)
                .setContentTitle("Actualización de tubeLD disponible")
                .setContentText("Versión ${info.name}. Toca para actualizar.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(JOB_ID, n)
            sp.edit().putLong("notified", info.code).apply()
        } catch (e: Throwable) {
            ErrorLog.add("ACTUALIZAR", "Notificación: $e")
        }
    }

    /** Revisa en segundo plano cada 6 horas aunque la app esté cerrada. */
    fun schedule(ctx: Context) {
        try {
            val js = ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            if (js.getPendingJob(JOB_ID) != null) return
            val b = JobInfo.Builder(JOB_ID, ComponentName(ctx, UpdateJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(6L * 60 * 60 * 1000)
                .setPersisted(true)
            js.schedule(b.build())
        } catch (e: Throwable) {
            ErrorLog.add("ACTUALIZAR", "Programar revisión: $e")
        }
    }

    // ---------- descarga e instalación ----------

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(a: Activity) {
        try {
            a.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.packageName))
            )
        } catch (_: Throwable) {
        }
    }

    /** Descarga el APK en un hilo aparte y verifica que sea de tubeLD y más nuevo. */
    fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit, done: (File?) -> Unit) {
        Thread {
            var f: File? = null
            try {
                val dir = File(ctx.cacheDir, "updates")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                f = File(dir, "tubeLD-update.apk")
                val c = URL(info.apk).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 20000
                if (c.responseCode != 200) {
                    ErrorLog.add("ACTUALIZAR", "Descarga APK: HTTP ${c.responseCode}")
                    done(null)
                    return@Thread
                }
                val total = c.contentLengthLong
                if (total > MAX_APK) {
                    ErrorLog.add("ACTUALIZAR", "APK demasiado grande: $total")
                    done(null)
                    return@Thread
                }
                var read = 0L
                var lastPct = -1
                c.inputStream.use { ins ->
                    f.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            os.write(buf, 0, n)
                            read += n
                            if (read > MAX_APK) throw java.io.IOException("APK demasiado grande")
                            if (total > 0) {
                                val p = (read * 100 / total).toInt()
                                if (p != lastPct) {
                                    lastPct = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
                val pi = ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0)
                if (pi == null || pi.packageName != ctx.packageName || codeOf(pi) <= installedCode(ctx)) {
                    ErrorLog.add("ACTUALIZAR", "El APK descargado no es válido o no es más nuevo (paquete=${pi?.packageName})")
                    f.delete()
                    done(null)
                    return@Thread
                }
                done(f)
            } catch (e: Throwable) {
                ErrorLog.add("ACTUALIZAR", "Descarga fallida: $e")
                try {
                    f?.delete()
                } catch (_: Throwable) {
                }
                done(null)
            }
        }.start()
    }

    /** Abre el instalador de Android con el APK descargado. */
    fun install(a: Activity, f: File) {
        try {
            val uri = FileProvider.getUriForFile(a, a.packageName + ".update", f)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            a.startActivity(i)
        } catch (e: Throwable) {
            ErrorLog.add("ACTUALIZAR", "No se pudo abrir el instalador: $e")
        }
    }
}
