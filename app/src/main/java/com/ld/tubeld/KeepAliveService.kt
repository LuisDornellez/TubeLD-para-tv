package com.ld.tubeld

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.min

/**
 * Mantiene viva la app con la pantalla apagada y muestra la notificación de música
 * (anterior, pausa/play, siguiente, cerrar, barra de progreso y carátula).
 */
class KeepAliveService : Service() {

    private var lock: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var session: MediaSessionCompat? = null
    private val main = Handler(Looper.getMainLooper())

    private var art: Bitmap? = null
    private var artBase: Bitmap? = null   // carátula reducida sobre la que se dibuja el visualizador
    private var shown: Bitmap? = null     // lo que se muestra en la notificación (carátula + barras)
    private var vizOn = false
    private val meter = RadialMeter(24)
    private val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        letterSpacing = -0.02f
    }
    private val shadePaint = Paint()
    private var artUrl = ""
    private var lastMeta = ""
    private var lastPlaying = false
    private var lastPos = 0L
    private var lastSync = 0L

    /** Si se desconectan los auriculares, se pausa. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            Media.send(Media.PAUSE)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val s = MediaSessionCompat(this, "tubeLD")
        s.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = Media.send(Media.PLAY)
            override fun onPause() = Media.send(Media.PAUSE)
            override fun onSkipToNext() = Media.send(Media.NEXT)
            override fun onSkipToPrevious() = Media.send(Media.PREV)
            override fun onSeekTo(pos: Long) = Media.send(Media.SEEK, pos)
            override fun onStop() = Media.send(Media.CLOSE)
        })
        s.isActive = true
        session = s
        pushPlayback(Media.state)
        try {
            ContextCompat.registerReceiver(
                this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
        }
        Media.onState = { st -> main.post { onState(st) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAYPAUSE -> {
                Media.send(if (Media.state.playing) Media.PAUSE else Media.PLAY)
                return START_NOT_STICKY
            }
            ACTION_NEXT -> {
                Media.send(Media.NEXT)
                return START_NOT_STICKY
            }
            ACTION_PREV -> {
                Media.send(Media.PREV)
                return START_NOT_STICKY
            }
            ACTION_CLOSE -> {
                if (Media.onCommand == null) stopSelf() else Media.send(Media.CLOSE)
                return START_NOT_STICKY
            }
        }
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        if (lock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tubeld:bg")
            lock?.acquire()
        }
        if (wifi == null) {
            // Con la pantalla apagada el Wi-Fi entra en ahorro y el audio se entrecorta: se mantiene activo.
            try {
                val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val mode = if (Build.VERSION.SDK_INT >= 34) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifi = wm.createWifiLock(mode, "tubeld:wifi").also {
                    it.setReferenceCounted(false)
                    it.acquire()
                }
            } catch (_: Exception) {
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        vizOn = false
        main.removeCallbacks(vizTick)
        Media.onState = null
        try {
            unregisterReceiver(noisy)
        } catch (_: Exception) {
        }
        try {
            session?.isActive = false
            session?.release()
        } catch (_: Exception) {
        }
        session = null
        try {
            lock?.release()
        } catch (_: Exception) {
        }
        lock = null
        try {
            if (wifi?.isHeld == true) wifi?.release()
        } catch (_: Exception) {
        }
        wifi = null
        super.onDestroy()
    }

    // ---------- estado que llega de la página ----------

    private fun onState(st: Media.State) {
        val metaKey = st.title + "|" + st.artist + "|" + st.artUrl + "|" + (st.durMs / 1000)
        val metaChanged = metaKey != lastMeta
        val playChanged = st.playing != lastPlaying
        val now = System.currentTimeMillis()
        val expected = if (lastPlaying) lastPos + (now - lastSync) else lastPos
        val drift = abs(st.posMs - expected)

        if (metaChanged) {
            lastMeta = metaKey
            if (st.artUrl != artUrl) loadArt(st.artUrl)
            pushMeta(st)
        }
        if (metaChanged || playChanged || drift > 2500) {
            pushPlayback(st)
            lastPos = st.posMs
            lastSync = now
            lastPlaying = st.playing
        }
        if (playChanged) {
            if (st.playing) startViz() else stopViz()
        }
        if (metaChanged || playChanged) notifyNow()
    }

    // ---------- visualizador en la carátula de la notificación ----------

    private val vizTick = object : Runnable {
        override fun run() {
            if (!vizOn || session == null) return
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            // Con la pantalla apagada nadie lo ve: no se gasta batería dibujando.
            if (Media.state.playing && pm.isInteractive) {
                try {
                    renderViz()
                    pushMeta(Media.state)
                    notifyNow()
                } catch (_: Exception) {
                }
            }
            main.postDelayed(this, 250)
        }
    }

    private fun startViz() {
        if (vizOn) return
        vizOn = true
        meter.reset()
        main.removeCallbacks(vizTick)
        main.post(vizTick)
    }

    private fun stopViz() {
        vizOn = false
        main.removeCallbacks(vizTick)
        shown = art
        pushMeta(Media.state)
    }

    /**
     * Dibuja abajo en la carátula (zona central cuadrada, que es la que se ve) el nombre
     * "tubeLD" y, a su lado, el visualizador radial de colores.
     */
    private fun renderViz() {
        val base = artBase ?: Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawColor(0xFF14141C.toInt())
            artBase = it
        }
        val bmp = base.copy(Bitmap.Config.ARGB_8888, true) ?: return
        val cv = Canvas(bmp)
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val side = min(w, h)
        val left = (w - side) / 2f
        val top = (h - side) / 2f

        // Velo oscuro abajo para que se lea sobre cualquier carátula
        shadePaint.shader = LinearGradient(
            0f, top + side * 0.50f, 0f, top + side,
            0x00000000, 0xCC000000.toInt(), Shader.TileMode.CLAMP
        )
        cv.drawRect(left, top + side * 0.50f, left + side, top + side, shadePaint)

        meter.update(true)
        val pad = side * 0.07f
        val dia = side * 0.27f
        txtPaint.textSize = side * 0.17f
        val tube = "tube"
        val ld = "LD"
        val wTube = txtPaint.measureText(tube)
        val wLd = txtPaint.measureText(ld)
        val gap = side * 0.04f
        val total = wTube + wLd + gap + dia
        val x0 = left + (side - total) / 2f
        val cy = top + side - pad - dia / 2f
        val fm = txtPaint.fontMetrics
        val baseY = cy - (fm.ascent + fm.descent) / 2f
        txtPaint.color = 0xFFFFFFFF.toInt()
        cv.drawText(tube, x0, baseY, txtPaint)
        txtPaint.color = Pal.NIGHT.gold
        cv.drawText(ld, x0 + wTube, baseY, txtPaint)
        val r = dia / 2f
        meter.draw(cv, x0 + wTube + wLd + gap + r, cy, r * 0.36f, r, side * 0.022f)
        shown = bmp
    }

    private fun pushMeta(st: Media.State) {
        val b = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, st.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, st.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, st.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, st.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, st.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, st.durMs)
        (shown ?: art)?.let {
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            b.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
        }
        session?.setMetadata(b.build())
    }

    private fun pushPlayback(st: Media.State) {
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP
        val pb = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                if (st.playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                st.posMs,
                if (st.playing) 1f else 0f
            )
            .build()
        session?.setPlaybackState(pb)
    }

    /** Baja la carátula en segundo plano y actualiza la notificación cuando llega. */
    private fun loadArt(url: String) {
        artUrl = url
        art = null
        artBase = null
        shown = null
        if (!url.startsWith("https://")) return
        Thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val raw = c.inputStream.use { BitmapFactory.decodeStream(it) } ?: return@Thread
                val max = 512
                val bmp = if (raw.width > max || raw.height > max) {
                    val k = max.toFloat() / maxOf(raw.width, raw.height)
                    Bitmap.createScaledBitmap(raw, (raw.width * k).toInt(), (raw.height * k).toInt(), true)
                } else raw
                main.post {
                    if (url == artUrl && session != null) {
                        art = bmp
                        artBase = if (bmp.width > 320 || bmp.height > 320) {
                            val k = 320f / maxOf(bmp.width, bmp.height)
                            Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
                        } else bmp
                        if (!vizOn) shown = bmp
                        pushMeta(Media.state)
                        notifyNow()
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    // ---------- notificación ----------

    private fun act(code: Int, action: String): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, KeepAliveService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun buildNotification(): android.app.Notification {
        val st = Media.state
        val has = st.title.isNotEmpty()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        session?.sessionToken?.let { style.setMediaSession(it) }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_headphones)
            .setContentTitle(if (has) st.title else "tubeLD")
            .setContentText(if (has) st.artist else "Reproducción en segundo plano activa")
            .setLargeIcon(shown ?: art)
            .setContentIntent(open)
            .addAction(R.drawable.ic_prev, "Anterior", act(11, ACTION_PREV))
            .addAction(
                if (st.playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (st.playing) "Pausar" else "Reproducir",
                act(12, ACTION_PLAYPAUSE)
            )
            .addAction(R.drawable.ic_next, "Siguiente", act(13, ACTION_NEXT))
            .addAction(R.drawable.ic_close, "Cerrar", act(14, ACTION_CLOSE))
            .setStyle(style)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyNow() {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Reproducción", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val CHANNEL = "tubeld_media"
        const val NOTIF_ID = 1
        const val ACTION_PLAYPAUSE = "com.ld.tubeld.PLAYPAUSE"
        const val ACTION_NEXT = "com.ld.tubeld.NEXT"
        const val ACTION_PREV = "com.ld.tubeld.PREV"
        const val ACTION_CLOSE = "com.ld.tubeld.CLOSE"
    }
}
