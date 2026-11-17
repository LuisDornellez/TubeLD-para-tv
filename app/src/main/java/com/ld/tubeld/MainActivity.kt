package com.ld.tubeld

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.PendingIntent
import android.app.UiModeManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.animation.ValueAnimator
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.graphics.ColorUtils
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import androidx.webkit.ScriptHandler
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var web: BgWebView
    private lateinit var bar: ProgressBar
    private lateinit var full: FrameLayout
    private lateinit var btnAds: ImageButton
    private lateinit var btnBg: ImageButton
    private lateinit var tvBadge: TextView
    private lateinit var tvLogo: TextView
    private lateinit var btnTheme: ImageButton
    private lateinit var root: FrameLayout
    private lateinit var splash: View
    private lateinit var btnExit: ImageButton
    private var splashStart = 0L
    private var splashDone = false
    private var splashQueued = false
    private val hideSplash = Runnable {
        if (splashDone) return@Runnable
        splashDone = true
        syncBars()
        splash.animate().alpha(0f).setDuration(320).withEndAction { splash.visibility = View.GONE }.start()
        if (isTv) {
            web.requestFocus()
            showCursor()
        }
    }
    private var shownPal: Pal.P? = null
    private var themeAnim: ValueAnimator? = null
    private var pill: TextView? = null
    private val hidePill = Runnable {
        pill?.animate()?.alpha(0f)?.setDuration(240)?.withEndAction { pill?.visibility = View.GONE }?.start()
    }
    private lateinit var bottomBar: View
    private lateinit var divider: View
    private lateinit var topBar: View
    private lateinit var cursor: CursorView

    // ---- Smart TV (control remoto) ----
    private var isTv = false
    private lateinit var btnNav: ImageButton
    private lateinit var btnQuality: ImageButton
    /** true = flechas (se salta de botón en botón de la página); false = ratón (puntero). */
    private var navMode = false
    private var fsPointer = false
    private var okLong = false

    // ---- Descanso visual: tinte cálido (menos luz azul) y atenuado, encima de toda la app ----
    private lateinit var btnRest: ImageButton
    private lateinit var btnErr: ImageButton
    private var updateInfo: Updater.Info? = null
    private val restBg = LayerDrawable(arrayOf(ColorDrawable(0), ColorDrawable(0)))
    private var restOn = false
    private var restBlue = 50
    private var restDim = 20
    /** Modo ligero: TV con poca memoria (1 GB). Web móvil, sin ecualizador, visualizador, notificación ni segundo plano. */
    private var lite = false
    private var edgeBlockedUp = false
    private var edgeBlockedDown = false
    private val fadeCursor = Runnable {
        cursor.animate().alpha(0f).setDuration(300).start()
    }

    private var docScript: ScriptHandler? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null

    @Volatile private var adsOn = true
    private var bgOn = true

    private lateinit var btnTimer: ImageButton
    private lateinit var btnEq: ImageButton
    private var eq = Eq.Cfg()
    private val handler = Handler(Looper.getMainLooper())
    private var timerEnd = 0L
    private val timerTask = Runnable {
        timerEnd = 0
        paint()
        runCmd(Media.PAUSE, 0)
        toast("Temporizador: reproducción pausada")
    }

    private val prefs by lazy { getSharedPreferences("tubeld", MODE_PRIVATE) }

    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            syncService()
            handler.postDelayed({ maybeAskBattery() }, 800)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_TubeLD) // quita el tema de arranque (la pantalla inicial del sistema)
        super.onCreate(savedInstanceState)
        isTv = detectTv()
        lite = detectLite()
        if (isTv) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        web = findViewById(R.id.web)
        bar = findViewById(R.id.bar)
        full = findViewById(R.id.full)
        btnAds = findViewById(R.id.btnAds)
        btnBg = findViewById(R.id.btnBg)
        btnTimer = findViewById(R.id.btnTimer)
        btnEq = findViewById(R.id.btnEq)
        eq = Eq.load(prefs)
        tvBadge = findViewById(R.id.tvBadge)
        tvLogo = findViewById(R.id.tvLogo)
        btnTheme = findViewById(R.id.btnTheme)
        root = findViewById(R.id.root)
        splash = findViewById(R.id.splash)
        btnExit = findViewById(R.id.btnExit)
        btnRest = findViewById(R.id.btnRest)
        btnErr = findViewById(R.id.btnErr)
        btnNav = findViewById(R.id.btnNav)
        btnQuality = findViewById(R.id.btnQuality)
        navMode = false // solo modo ratón
        bottomBar = findViewById(R.id.bottomBar)
        divider = findViewById(R.id.divider)
        topBar = findViewById(R.id.topBar)
        cursor = findViewById(R.id.cursor)

        // Modo día / noche (por defecto, noche como siempre)
        Pal.cur = if (prefs.getBoolean("night", true)) Pal.NIGHT else Pal.DAY
        setPageTheme(Pal.cur.night)

        adsOn = prefs.getBoolean("ads", true)
        bgOn = !lite && prefs.getBoolean("bg", true)
        web.keepAlive = bgOn

        Media.reset()
        Media.onCommand = { cmd, arg -> runOnUiThread { runCmd(cmd, arg) } }
        Media.onDebug = { m -> runOnUiThread { android.widget.Toast.makeText(applicationContext, m, android.widget.Toast.LENGTH_LONG).show() } }

        ErrorLog.install(applicationContext)
        ErrorLog.add("APP", "Inicio de la app")
        RemoteScript.load(applicationContext)
        setupWeb()
        installScripts()
        RemoteScript.refresh(applicationContext) { runOnUiThread { installScripts() } }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (web.canGoBack()) web.goBack()
        }
        findViewById<ImageButton>(R.id.btnHome).setOnClickListener {
            web.loadUrl(homeUrl())
        }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener {
            web.reload()
        }
        btnAds.setOnClickListener {
            adsOn = !adsOn
            prefs.edit().putBoolean("ads", adsOn).apply()
            paint()
            installScripts()
            toast(if (adsOn) "Bloqueo de anuncios activado" else "Bloqueo de anuncios desactivado")
            web.reload()
        }
        btnBg.setOnClickListener {
            bgOn = !bgOn
            prefs.edit().putBoolean("bg", bgOn).apply()
            web.keepAlive = bgOn
            paint()
            installScripts()
            syncService()
            toast(if (bgOn) "Segundo plano activado" else "Segundo plano desactivado")
            web.reload()
        }
        btnTimer.setOnClickListener { showTimerDialog() }
        btnEq.setOnClickListener { showEqDialog() }
        btnTheme.setOnClickListener {
            val night = !Pal.cur.night
            Pal.cur = if (night) Pal.NIGHT else Pal.DAY
            prefs.edit().putBoolean("night", night).apply()
            applyTheme(true)
            setPageTheme(night)
            toast(if (night) "Modo noche" else "Modo día")
            web.reload()
        }
        setupRest()
        if (isTv) setupTv()
        if (lite) {
            // Menos funciones = menos memoria. El nombre tubeLD y el bloqueo de anuncios se quedan.
            findViewById<View>(R.id.viz).visibility = View.GONE
            btnEq.visibility = View.GONE
            btnBg.visibility = View.GONE
        }
        applyTheme()
        val bb = bottomBar as ViewGroup
        for (i in 0 until bb.childCount) pressFx(bb.getChildAt(i))
        pressFx(btnTheme)
        pressFx(btnExit)
        pressFx(btnRest)
        pressFx(btnErr)
        btnErr.setOnClickListener { showErrorsDialog() }
        btnRest.setOnClickListener { showRestDialog() }
        btnQuality.setOnClickListener { showQualityDialog() }
        btnNav.visibility = View.GONE // sin botón: solo modo ratón
        pressFx(btnNav)
        btnNav.setOnClickListener {
            navMode = !navMode
            prefs.edit().putBoolean("navmode", navMode).apply()
            applyNavMode()
            toast(if (navMode) "Modo flechas: ▲▼◀▶ saltan entre botones, OK selecciona" else "Modo ratón: las flechas mueven el puntero")
        }
        applyNavMode()
        btnExit.setOnClickListener { showExitDialog() }
        findViewById<ImageButton>(R.id.btnReload).setOnLongClickListener { showErrorsDialog(); true }
        if (savedInstanceState == null) {
            showSplash()
        } else {
            splashDone = true
            splash.visibility = View.GONE
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hideFull()
                    web.canGoBack() -> web.goBack()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        if (savedInstanceState == null) {
            val target = intent?.data?.toString()
            web.loadUrl(if (target != null && target.startsWith("https://")) target else homeUrl())
        } else {
            web.restoreState(savedInstanceState)
        }

        tvBadge.setOnClickListener { checkUpdate(true) }
        Updater.schedule(applicationContext)
        if (intent?.getBooleanExtra("update", false) == true) {
            handler.postDelayed({ checkUpdate(true) }, 1500)
        } else {
            handler.postDelayed({ checkUpdate(false) }, 5000)
        }

        if (Build.VERSION.SDK_INT >= 33 && !lite &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            syncService()
            if (savedInstanceState == null) handler.postDelayed({ maybeAskBattery() }, 3500)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("update", false)) checkUpdate(true)
        val target = intent.data?.toString()
        if (target != null && target.startsWith("https://")) web.loadUrl(target)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onPause() {
        if (!bgOn) web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Media.onCommand = null
        Media.onDebug = null
        Media.reset()
        stopService(Intent(this, KeepAliveService::class.java))
        full.removeAllViews()
        web.destroy()
        super.onDestroy()
    }

    // ---------- Smart TV: control remoto ----------

    /** Smart TV / Android TV / Google TV / caja sin pantalla táctil. */
    private fun detectTv(): Boolean {
        val um = getSystemService(UI_MODE_SERVICE) as? UiModeManager
        if (um != null && um.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    private fun homeUrl(): String = if (isTv && !lite) HOME_TV else HOME

    /** TV con 1 GB de RAM (o marcada por el sistema como de poca memoria). */
    private fun detectLite(): Boolean {
        if (!isTv) return false
        return try {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            am.isLowRamDevice || mi.totalMem <= 1_600_000_000L
        } catch (_: Exception) {
            false
        }
    }

    private fun tvFocusables(): List<View> {
        val l = ArrayList<View>()
        val bb = bottomBar as ViewGroup
        for (i in 0 until bb.childCount) l.add(bb.getChildAt(i))
        l.add(btnErr)
        l.add(btnRest)
        l.add(btnTheme)
        l.add(btnExit)
        return l
    }

    /** Botones más grandes (se ven desde el sofá) y que se pueden seleccionar con las flechas. */
    private fun setupTv() {
        fun size(v: View, w: Int, h: Int) {
            val lp = v.layoutParams
            if (w > 0) lp.width = w
            lp.height = h
            v.layoutParams = lp
        }
        // En modo ligero las barras son más bajas para dejar más sitio a la página
        size(topBar, 0, dp(if (lite) 56 else 72))
        topBar.setPadding(dp(36), 0, dp(28), 0)
        tvLogo.textSize = if (lite) 24f else 28f
        val bs = if (lite) 44 else 52
        for (b in listOf(btnNav, btnErr, btnRest, btnTheme, btnExit)) {
            size(b, dp(bs), dp(bs))
            b.setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        size(bottomBar, 0, dp(if (lite) 62 else 80))
        bottomBar.setPadding(dp(56), dp(2), dp(56), dp(if (lite) 4 else 8))
        val bb = bottomBar as ViewGroup
        for (i in 0 until bb.childCount) {
            val b = bb.getChildAt(i) as ImageButton
            b.scaleType = ImageView.ScaleType.FIT_CENTER
            b.setPadding(dp(8), dp(if (lite) 16 else 22), dp(8), dp(if (lite) 16 else 22))
        }
        for (v in tvFocusables()) {
            v.isFocusable = true
            v.setOnFocusChangeListener { x, f ->
                val k = if (f) 1.12f else 1f
                x.animate().scaleX(k).scaleY(k).setDuration(120).start()
            }
        }
        web.requestFocus()
    }

    /** Marco dorado alrededor del botón que tiene el foco. */
    private fun applyFocusRings() {
        val p = Pal.cur
        for (v in tvFocusables()) {
            val top = v === btnNav || v === btnErr || v === btnRest || v === btnTheme || v === btnExit
            val ring = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(withA(p.gold, 0x26))
                setStroke(dp(3), p.gold)
            }
            val none = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            val sl = StateListDrawable()
            sl.addState(
                intArrayOf(android.R.attr.state_focused),
                if (top) InsetDrawable(ring, dp(2), dp(2), dp(2), dp(2))
                else InsetDrawable(ring, dp(4), dp(8), dp(4), dp(8))
            )
            sl.addState(intArrayOf(), none)
            v.foreground = sl
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (handleMediaKey(e)) return true
        if (isTv && splashDone && handleTvKey(e)) return true
        return super.dispatchKeyEvent(e)
    }

    /** Botones de reproducción del control remoto (play, pausa, siguiente, adelantar, retroceder). */
    private fun handleMediaKey(e: KeyEvent): Boolean {
        val cmd: String
        var arg = 0L
        when (e.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> cmd = Media.PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> cmd = Media.PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> cmd = "toggle"
            KeyEvent.KEYCODE_MEDIA_NEXT -> cmd = Media.NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> cmd = Media.PREV
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { cmd = "seekrel"; arg = 10_000L }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { cmd = "seekrel"; arg = -10_000L }
            else -> return false
        }
        if (e.action == KeyEvent.ACTION_DOWN && (e.repeatCount == 0 || arg != 0L)) runCmd(cmd, arg)
        return true
    }

    private fun isDescendantOf(v: View?, anc: View): Boolean {
        var x: View? = v
        while (x != null) {
            if (x === anc) return true
            x = x.parent as? View
        }
        return false
    }

    private fun focusInBars(): Boolean {
        val f = currentFocus ?: return false
        return isDescendantOf(f, topBar) || isDescendantOf(f, bottomBar)
    }

    /**
     * Flechas y OK del control remoto:
     * - Página: mueven un puntero; OK hace clic; en el borde de arriba/abajo desplazan la página
     *   y, si ya no hay más, pasan a los botones de la barra.
     * - Barras: se mueve el foco entre botones; OK los activa; subir/bajar vuelve al puntero.
     * - Video a pantalla completa: izquierda/derecha saltan 10 s y OK pausa o reproduce.
     */
    private fun handleTvKey(e: KeyEvent): Boolean {
        val k = e.keyCode
        val isOk = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER ||
            k == KeyEvent.KEYCODE_NUMPAD_ENTER
        val isDir = k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN ||
            k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
        if (!isOk && !isDir) return false
        val down = e.action == KeyEvent.ACTION_DOWN

        val cv = customView
        if (cv != null) {
            // OK corto: pausa/play (o clic si el puntero está activo). OK largo: activa o quita el puntero.
            if (isOk) {
                if (down) {
                    if (e.repeatCount == 1) {
                        okLong = true
                        fsPointer = !fsPointer
                        if (fsPointer) showCursor() else cursor.visibility = View.GONE
                        toast(if (fsPointer) "Puntero activado (OK largo para quitarlo)" else "Puntero desactivado")
                    }
                } else {
                    if (!okLong) {
                        if (fsPointer) clickCursor() else runCmd("toggle", 0L)
                    }
                    okLong = false
                }
                return true
            }
            if (fsPointer) {
                if (down) {
                    showCursor()
                    moveCursor(k, e.repeatCount)
                }
                return true
            }
            if (down) {
                when (k) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (e.repeatCount % 4 == 0) runCmd("seekrel", -10_000L)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (e.repeatCount % 4 == 0) runCmd("seekrel", 10_000L)
                    else -> Unit
                }
                sendHover(cv, cv.width / 2f, cv.height / 2f) // hace aparecer los controles del reproductor
            }
            return true
        }

        if (focusInBars()) {
            if (!down) return false
            val inBottom = isDescendantOf(currentFocus, bottomBar)
            return when (k) {
                KeyEvent.KEYCODE_DPAD_UP -> { if (inBottom) enterCursor(); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { if (!inBottom) enterCursor(); true }
                else -> false
            }
        }

        if (!down) return true
        if (navMode) {
            if (isOk) {
                if (e.repeatCount == 0) navClick()
                return true
            }
            val first = e.repeatCount == 0
            val d = when (k) {
                KeyEvent.KEYCODE_DPAD_UP -> "u"
                KeyEvent.KEYCODE_DPAD_DOWN -> "d"
                KeyEvent.KEYCODE_DPAD_LEFT -> "l"
                else -> "r"
            }
            web.evaluateJavascript("window.__tldNavMove?window.__tldNavMove('$d'):'n'") { r ->
                val v = (r ?: "").trim('"')
                if (first && (v == "e" || v == "n")) {
                    if (d == "u") goToBar(false) else if (d == "d") goToBar(true)
                }
            }
            return true
        }
        showCursor()
        if (isOk) {
            if (e.repeatCount == 0) clickCursor()
            return true
        }
        moveCursor(k, e.repeatCount)
        return true
    }

    /** Cambia entre modo flechas y modo ratón (icono, puntero y marco de selección). */
    private fun applyNavMode() {
        btnNav.setImageResource(if (navMode) R.drawable.ic_arrows else R.drawable.ic_mouse)
        if (!isTv) return
        if (navMode) {
            handler.removeCallbacks(fadeCursor)
            cursor.visibility = View.GONE
        } else {
            web.evaluateJavascript("window.__tldNavClear&&window.__tldNavClear()", null)
            if (!focusInBars()) showCursor()
        }
    }

    /** Modo flechas: OK toca de verdad el elemento marcado (así funciona igual que un toque con el dedo). */
    private fun navClick() {
        web.evaluateJavascript("window.__tldNavPos?window.__tldNavPos():''") { r ->
            val p = (r ?: "").trim('"').split(",")
            if (p.size == 2) {
                val x = p[0].toFloatOrNull()
                val y = p[1].toFloatOrNull()
                if (x != null && y != null) {
                    val d = pxPerCss()
                    sendTap(web, x * d, y * d)
                }
            }
        }
    }

    private fun qualityLabel(q: String): String? = when (q) {
        "hd2880" -> "2880p"
        "hd2160" -> "2160p (4K)"
        "hd1440" -> "1440p"
        "hd1080" -> "1080p"
        "hd720" -> "720p"
        "large" -> "480p"
        "medium" -> "360p"
        "small" -> "240p"
        "tiny" -> "144p"
        else -> null
    }

    /** Lista de calidades del video actual; no hace falta llegar al engranaje del reproductor. */
    private fun showQualityDialog() {
        web.evaluateJavascript("window.__tldQ?window.__tldQ('list'):''") { raw ->
            var cur = ""
            val ids = ArrayList<String>()
            val names = ArrayList<String>()
            ids.add("auto")
            names.add("Automática")
            try {
                val str = org.json.JSONTokener(raw ?: "").nextValue() as? String ?: ""
                if (str.isNotEmpty()) {
                    val o = org.json.JSONObject(str)
                    cur = o.optString("cur")
                    val a = o.optJSONArray("levels")
                    if (a != null) {
                        for (i in 0 until a.length()) {
                            val q = a.optString(i)
                            val n = qualityLabel(q)
                            if (n != null) {
                                ids.add(q)
                                names.add(n)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
            if (ids.size < 2) {
                toast("Abre un video para elegir la calidad")
                return@evaluateJavascript
            }
            val sel = ids.indexOf(cur).let { if (it < 0) 0 else it }
            MaterialAlertDialogBuilder(dlgCtx())
                .setTitle("Calidad del video")
                .setSingleChoiceItems(names.toTypedArray(), sel) { d, i ->
                    web.evaluateJavascript("window.__tldQ&&window.__tldQ('set','${ids[i]}')", null)
                    toast("Calidad: ${names[i]}")
                    d.dismiss()
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }
    }

    private fun enterCursor() {
        web.requestFocus()
        showCursor()
    }

    private fun goToBar(bottom: Boolean) {
        edgeBlockedUp = false
        edgeBlockedDown = false
        handler.removeCallbacks(fadeCursor)
        cursor.visibility = View.GONE
        val target: View = if (bottom) findViewById(R.id.btnHome) else btnTheme
        target.requestFocus()
    }

    /** Zona de la página (sin las barras), en coordenadas del puntero. */
    private fun webBounds(): RectF {
        if (customView != null) return RectF(0f, 0f, root.width.toFloat(), root.height.toFloat())
        val a = IntArray(2)
        val c = IntArray(2)
        web.getLocationOnScreen(a)
        root.getLocationOnScreen(c)
        val l = (a[0] - c[0]).toFloat()
        val t = (a[1] - c[1]).toFloat()
        return RectF(l, t, l + web.width, t + web.height)
    }

    private fun showCursor() {
        if (!isTv || web.width == 0) return
        if (customView != null) {
            if (!fsPointer) return
        } else if (navMode) {
            return
        }
        if (cursor.visibility != View.VISIBLE) {
            cursor.visibility = View.VISIBLE
        }
        run {
            // Si la posicion quedo fuera de la pagina (por ejemplo al salir de pantalla completa), se vuelve a meter.
            val b = webBounds()
            val m = dp(6).toFloat()
            if (cursor.px == 0f && cursor.py == 0f) {
                cursor.moveTo(b.centerX(), b.centerY())
            } else if (cursor.px < b.left + m || cursor.px > b.right - m ||
                cursor.py < b.top + m || cursor.py > b.bottom - m
            ) {
                cursor.moveTo(
                    cursor.px.coerceIn(b.left + m, b.right - m),
                    cursor.py.coerceIn(b.top + m, b.bottom - m)
                )
            }
        }
        cursor.animate().cancel()
        cursor.alpha = 1f
        handler.removeCallbacks(fadeCursor)
        handler.postDelayed(fadeCursor, 5000)
    }

    private fun moveCursor(key: Int, repeat: Int) {
        val b = webBounds()
        val m = dp(6).toFloat()
        val band = dp(72).toFloat()
        val step = dp(14).toFloat() + minOf(repeat, 24) * dp(2).toFloat()
        var dx = 0f
        var dy = 0f
        when (key) {
            KeyEvent.KEYCODE_DPAD_LEFT -> dx = -step
            KeyEvent.KEYCODE_DPAD_RIGHT -> dx = step
            KeyEvent.KEYCODE_DPAD_UP -> dy = -step
            else -> dy = step
        }
        val x = (cursor.px + dx).coerceIn(b.left + m, b.right - m)
        var y = cursor.py
        if (dy != 0f) {
            val goingDown = dy > 0f
            val inBand = if (goingDown) y >= b.bottom - band else y <= b.top + band
            if (inBand && customView == null) {
                val atEnd = if (goingDown) y >= b.bottom - m - 1f else y <= b.top + m + 1f
                val blocked = if (goingDown) edgeBlockedDown else edgeBlockedUp
                if (atEnd && blocked && repeat == 0) {
                    goToBar(goingDown)
                    return
                }
                scrollPage(goingDown, Math.abs(dy), x, y, b)
            } else {
                edgeBlockedUp = false
                edgeBlockedDown = false
            }
            y = (y + dy).coerceIn(b.top + m, b.bottom - m)
        }
        cursor.moveTo(x, y)
        sendHover(customView ?: web, x - b.left, y - b.top)
    }

    /** Desplaza la página y apunta si ya no se pudo bajar/subir más (la próxima vez pasa a la barra). */
    /** Píxeles de pantalla por cada píxel CSS de la página (cambia si se amplía el ancho de la página en TV). */
    private fun pxPerCss(): Float {
        val w = Media.cssW
        return if (w > 0 && web.width > 0) (web.width / w).toFloat() else resources.displayMetrics.density
    }

    private fun scrollPage(goingDown: Boolean, px: Float, x: Float, y: Float, b: RectF) {
        val d = pxPerCss()
        val mag = (px / d * 2.2f).toInt()
        val css = if (goingDown) mag else -mag
        val cx = ((x - b.left) / d).toInt()
        val cy = ((y - b.top) / d).toInt()
        web.evaluateJavascript(
            "window.__tldScroll?window.__tldScroll($css,$cx,$cy):false"
        ) { r ->
            val moved = r == "true"
            if (goingDown) {
                edgeBlockedDown = !moved
                if (moved) edgeBlockedUp = false
            } else {
                edgeBlockedUp = !moved
                if (moved) edgeBlockedDown = false
            }
        }
    }

    private fun clickCursor() {
        val b = webBounds()
        sendTap(customView ?: web, cursor.px - b.left, cursor.py - b.top)
        cursor.animate().cancel()
        cursor.alpha = 0.55f
        cursor.animate().alpha(1f).setDuration(160).start()
    }

    private fun sendTap(target: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val dn = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        dn.source = InputDevice.SOURCE_TOUCHSCREEN
        target.dispatchTouchEvent(dn)
        dn.recycle()
        handler.postDelayed({
            val t2 = SystemClock.uptimeMillis()
            val up = MotionEvent.obtain(t, t2, MotionEvent.ACTION_UP, x, y, 0)
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            target.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    /** Mueve el "ratón" sobre la página (hace falta para que aparezcan los controles del reproductor). */
    private fun sendHover(target: View, x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_HOVER_MOVE, x, y, 0)
        ev.source = InputDevice.SOURCE_MOUSE
        target.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    // ---------- web ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        web.setBackgroundColor(Pal.cur.bg)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.userAgentString = if (isTv && !lite) UA_TV else UA
        if (isTv) s.textZoom = if (lite) TV_TEXT_ZOOM_LITE else TV_TEXT_ZOOM
        if (isTv && !lite) {
            // Página de escritorio más ancha (JS_VIEW): todo se ve más pequeño y entra la columna de videos de la derecha
            s.useWideViewPort = true
            s.loadWithOverviewMode = true
        }
        // Quita la cabecera X-Requested-With (nombre del paquete) que delata al WebView; Google la usa para bloquear el inicio de sesion.
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, emptySet())
            }
        } catch (_: Throwable) {
        }
        // El proceso de la página mantiene prioridad alta aunque la app esté en segundo plano.
        if (!lite) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        web.addJavascriptInterface(WebBridge(), "TLD")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val scheme = u.scheme ?: ""
                if (scheme != "http" && scheme != "https") return true
                val h = u.host ?: ""
                if (isInternal(h)) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                } catch (_: Exception) {
                }
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if (adsOn && isAdRequest(request)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) {
                    ErrorLog.add("RED", "No cargó la página ${request.url}: ${error.description} (código ${error.errorCode})")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                val u = request.url.toString()
                val vital = u.contains("videoplayback") || u.contains("youtubei") || u.contains("/player")
                if (request.isForMainFrame || (errorResponse.statusCode >= 400 && vital)) {
                    ErrorLog.add("HTTP", "${errorResponse.statusCode} en ${u.take(160)}")
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                ErrorLog.add("WEBVIEW", "El proceso de la página se cerró (crash=${detail.didCrash()})")
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                inject()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                inject()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                bar.progress = newProgress
                if (newProgress >= 70) hideSplashSoon()
                bar.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                customCb = callback
                full.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                full.visibility = View.VISIBLE
                fsPointer = false
                cursor.visibility = View.GONE
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                val c = WindowInsetsControllerCompat(window, window.decorView)
                c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(WindowInsetsCompat.Type.systemBars())
            }

            override fun onHideCustomView() {
                hideFull()
            }
        }
    }

    private fun hideFull() {
        val v = customView ?: return
        full.removeView(v)
        full.visibility = View.GONE
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
        fsPointer = false
        cursor.visibility = View.GONE
        requestedOrientation =
            if (isTv) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        if (isTv) {
            web.requestFocus()
            showCursor()
        }
    }

    /** Registra el script para que corra ANTES que los de YouTube. */
    private fun installScripts() {
        try {
            docScript?.remove()
            docScript = null
            val sb = StringBuilder()
            sb.append(JS_LOG)
        sb.append(RemoteScript.wrapped())
            sb.append(JS_QUAL)
            if (lite) sb.append("window.__tldLite=true;")
            if (bgOn) sb.append(JS_VISIBLE)
            if (bgOn || isTv) sb.append(JS_MEDIA)
            if (adsOn) sb.append(JS_ADS)
            sb.append(JS_FIX)
            if (isTv && !lite) sb.append(JS_VIEW)
            if (isTv) sb.append(JS_TV).append(JS_NAV)
            if (!lite) sb.append(eqScript())
            if (sb.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                docScript = WebViewCompat.addDocumentStartJavaScript(
                    web,
                    sb.toString(),
                    setOf("https://m.youtube.com", "https://www.youtube.com")
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun inject() {
        val sb = StringBuilder()
        sb.append(JS_LOG)
        sb.append(RemoteScript.wrapped())
        sb.append(JS_QUAL)
            if (lite) sb.append("window.__tldLite=true;")
            if (bgOn) sb.append(JS_VISIBLE)
            if (bgOn || isTv) sb.append(JS_MEDIA)
        if (adsOn) sb.append(JS_ADS)
        sb.append(JS_FIX)
        if (isTv && !lite) sb.append(JS_VIEW)
        if (isTv) sb.append(JS_TV).append(JS_NAV)
        if (!lite) sb.append(eqScript())
        if (sb.isNotEmpty()) web.evaluateJavascript(sb.toString(), null)
    }

    private fun isInternal(h: String): Boolean =
        h == "youtube.com" || h.endsWith(".youtube.com") ||
            h == "youtu.be" ||
            h == "google.com" || h.endsWith(".google.com")

    private fun isAdRequest(r: WebResourceRequest): Boolean {
        val u = r.url
        val h = u.host ?: return false
        for (d in AD_HOSTS) {
            if (h == d || h.endsWith(".$d")) return true
        }
        val p = u.path ?: ""
        return p.contains("/pagead/") || p.contains("/api/stats/ads") || p.contains("/get_midroll_")
    }

    // ---------- modo día / noche ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun withA(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    /** Pinta toda la app (fondos, barras, logo, botones) con los colores del modo actual. */
    private fun applyTheme(animate: Boolean = false) {
        val to = Pal.cur
        val from = shownPal
        themeAnim?.cancel()
        themeAnim = null

        divider.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(to.gold, to.gold and 0x00FFFFFF)
        )
        bar.progressTintList = ColorStateList.valueOf(to.gold)
        tvBadge.setTextColor(to.gold)
        tvBadge.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(withA(to.gold, 0x14))
            setStroke(dp(1), withA(to.gold, 0x4D))
        }

        val logo = SpannableString("tubeLD")
        logo.setSpan(ForegroundColorSpan(to.text), 0, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        logo.setSpan(ForegroundColorSpan(to.gold), 4, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvLogo.text = logo

        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.isAppearanceLightStatusBars = !to.night
        c.isAppearanceLightNavigationBars = !to.night

        cursor.setColor(to.gold)
        if (isTv) applyFocusRings()

        // En modo noche se muestra el sol (para pasar a día) y en modo día, la luna.
        btnTheme.setImageResource(if (to.night) R.drawable.ic_sun else R.drawable.ic_moon)
        tintIcon(btnTheme, true)
        tintIcon(btnExit, false)
        tintIcon(btnErr, false)
        paint()

        if (animate && !lite && from != null && from !== to) {
            val anim = ValueAnimator.ofFloat(0f, 1f)
            anim.duration = 260
            anim.addUpdateListener { va ->
                val f = va.animatedFraction
                paintSurfaces(
                    ColorUtils.blendARGB(from.bg, to.bg, f),
                    ColorUtils.blendARGB(from.bar, to.bar, f),
                    to
                )
            }
            themeAnim = anim
            anim.start()
        } else {
            paintSurfaces(to.bg, to.bar, to)
        }
        shownPal = to
    }

    /** Una sola vez: ofrece quitar el límite de batería para que el audio no se corte en segundo plano. */
    private fun maybeAskBattery() {
        if (isTv || isFinishing || isDestroyed || Build.VERSION.SDK_INT < 23) return
        if (prefs.getBoolean("battery_asked", false)) return
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        prefs.edit().putBoolean("battery_asked", true).apply()
        MaterialAlertDialogBuilder(dlgCtx())
            .setTitle("Sin límite de batería")
            .setMessage(
                "Para que el audio no se corte con la pantalla apagada, permite que tubeLD " +
                    "funcione sin restricciones de batería."
            )
            .setPositiveButton("Permitir") { _, _ -> openBatterySettings() }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                toast("Abre Ajustes > Apps > tubeLD > Ahorro de batería > Sin restricciones")
            }
        }
    }

    /** Pantalla inicial: siempre oscura y dorada; solo texto, un icono y 3 fundidos (muy liviana). */
    private fun showSplash() {
        val logo = findViewById<TextView>(R.id.splashLogo)
        val s = SpannableString("tubeLD")
        s.setSpan(ForegroundColorSpan(Color.WHITE), 0, 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(Pal.NIGHT.gold), 4, 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        logo.text = s

        // Barras del sistema oscuras mientras se ve la pantalla inicial
        window.statusBarColor = Pal.NIGHT.bg
        window.navigationBarColor = Pal.NIGHT.bg
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.isAppearanceLightStatusBars = false
        c.isAppearanceLightNavigationBars = false

        splashStart = SystemClock.uptimeMillis()
        val mark = findViewById<View>(R.id.splashMark)
        mark.alpha = 0f
        mark.scaleX = 0.88f
        mark.scaleY = 0.88f
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(500).start()
        logo.alpha = 0f
        logo.animate().alpha(1f).setStartDelay(180).setDuration(450).start()
        val tag = findViewById<View>(R.id.splashTag)
        tag.alpha = 0f
        tag.animate().alpha(1f).setStartDelay(420).setDuration(450).start()
        handler.postDelayed(hideSplash, 3500) // por si la página tarda: nunca se queda pegada
    }

    /** Cuando YouTube ya cargó, la pantalla inicial se va (después de verse al menos 1,1 s). */
    private fun hideSplashSoon() {
        if (splashDone || splashQueued) return
        splashQueued = true
        val wait = (1100L - (SystemClock.uptimeMillis() - splashStart)).coerceAtLeast(0L)
        handler.postDelayed(hideSplash, wait)
    }

    private fun syncBars() {
        val p = Pal.cur
        window.statusBarColor = p.bg
        window.navigationBarColor = p.bar
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.isAppearanceLightStatusBars = !p.night
        c.isAppearanceLightNavigationBars = !p.night
    }

    private fun showExitDialog() {
        MaterialAlertDialogBuilder(dlgCtx())
            .setTitle("¿Salir de tubeLD?")
            .setMessage("Se cerrará la app y se detendrá lo que esté sonando.")
            .setPositiveButton("Salir") { _, _ -> runCmd(Media.CLOSE, 0) }
            .setNegativeButton("Seguir viendo", null)
            .setNeutralButton("Ver errores") { _, _ -> showErrorsDialog() }
            .show()
    }

    // ---------- actualizaciones ----------

    /** Busca una versión nueva. manual = el usuario lo pidió (siempre muestra el resultado). */
    private fun checkUpdate(manual: Boolean) {
        if (manual) toast("Buscando actualización…")
        Updater.check(applicationContext) { info ->
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                updateInfo = info
                updateBadge()
                if (info != null) {
                    Updater.notifyNew(applicationContext, info)
                    if (manual || prefs.getLong("upd_skip", 0L) != info.code) showUpdateDialog(info)
                } else if (manual) {
                    toast("Ya tienes la última versión (${Updater.installedName(this)})")
                }
            }
        }
    }

    private fun showUpdateDialog(info: Updater.Info) {
        val msg = "Nueva versión ${info.name} (tienes ${Updater.installedName(this)})." +
            (if (info.notes.isNotBlank()) "\n\n" + info.notes else "")
        MaterialAlertDialogBuilder(dlgCtx())
            .setTitle("Actualización disponible")
            .setMessage(msg)
            .setPositiveButton("Actualizar ahora") { _, _ -> startUpdate(info) }
            .setNegativeButton("Más tarde") { _, _ ->
                prefs.edit().putLong("upd_skip", info.code).apply()
            }
            .show()
    }

    private fun startUpdate(info: Updater.Info) {
        if (!Updater.canInstall(this)) {
            toast("Activa «Permitir de esta fuente» para tubeLD y vuelve a tocar Actualizar")
            Updater.openInstallSettings(this)
            return
        }
        val tv = android.widget.TextView(this).apply {
            text = "Descargando… 0%"
            textSize = 16f
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setTextColor(if (Pal.cur.night) 0xFFEEEEEE.toInt() else 0xFF222222.toInt())
        }
        val d = MaterialAlertDialogBuilder(dlgCtx())
            .setTitle("Actualizando tubeLD")
            .setView(tv)
            .setCancelable(false)
            .show()
        Updater.download(applicationContext, info, { p ->
            runOnUiThread { tv.text = "Descargando… $p%" }
        }) { f ->
            runOnUiThread {
                try {
                    d.dismiss()
                } catch (_: Exception) {
                }
                if (f == null) {
                    toast("No se pudo descargar la actualización (mira el registro de errores 🐞)")
                } else {
                    toast("Toca «Instalar» en la ventana de Android")
                    Updater.install(this, f)
                }
            }
        }
    }

    /** Muestra el registro de errores; se puede copiar o compartir para dárselo a una IA. */
    private fun showErrorsDialog() {
        val txt = ErrorLog.dump(this)
        val intro = "Estos son los errores de mi app Android TubeLD (WebView con YouTube). " +
            "Analízalos, dime la causa y dame el código corregido:\n\n"
        val tv = android.widget.TextView(this).apply {
            text = txt
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setTextColor(if (Pal.cur.night) 0xFFEEEEEE.toInt() else 0xFF222222.toInt())
        }
        val sv = android.widget.ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(dlgCtx())
            .setTitle("Errores y registro")
            .setView(sv)
            .setPositiveButton("Copiar") { _, _ ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("TubeLD errores", intro + txt))
                toast("Copiado: pégalo en tu IA")
            }
            .setNeutralButton("Compartir") { _, _ ->
                try {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, intro + txt)
                    }
                    startActivity(Intent.createChooser(i, "Compartir errores"))
                } catch (_: Exception) {
                    toast("No hay app para compartir")
                }
            }
            .setNegativeButton("Borrar") { _, _ ->
                ErrorLog.clear()
                toast("Registro borrado")
            }
            .show()
        sv.post { sv.fullScroll(View.FOCUS_DOWN) }
    }

    /** Fondos que cambian con suavidad al pasar de día a noche. */
    private fun paintSurfaces(bg: Int, barColor: Int, p: Pal.P) {
        root.setBackgroundColor(bg)
        web.setBackgroundColor(bg)
        val r = dp(24).toFloat()
        bottomBar.background = GradientDrawable().apply {
            setColor(barColor)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setStroke(dp(1), withA(p.gold, 0x2E))
        }
        window.statusBarColor = bg
        window.navigationBarColor = barColor
    }

    /** Al tocar un botón: se achica un poco y vibra suave. */
    @SuppressLint("ClickableViewAccessibility")
    private fun pressFx(v: View) {
        v.setOnTouchListener { x, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start()
                    x.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    x.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
    }

    /**
     * Le dice a YouTube qué tema usar (cookie PREF, campo f6: 0x400 = oscuro, 0x80000 = claro).
     * Hay que recargar la página para que se note.
     */
    private fun setPageTheme(dark: Boolean) {
        try {
            val cm = CookieManager.getInstance()
            val raw = cm.getCookie("https://m.youtube.com") ?: ""
            var pref = ""
            for (part in raw.split(";")) {
                val t = part.trim()
                if (t.startsWith("PREF=")) pref = t.substring(5)
            }
            val map = LinkedHashMap<String, String>()
            for (kv in pref.split("&")) {
                val i = kv.indexOf('=')
                if (i > 0) map[kv.substring(0, i)] = kv.substring(i + 1)
            }
            var f6 = map["f6"]?.toLongOrNull(16) ?: 0L
            f6 = if (dark) (f6 or 0x400L) and 0x80000L.inv() else (f6 or 0x80000L) and 0x400L.inv()
            map["f6"] = java.lang.Long.toHexString(f6)
            val v = map.entries.joinToString("&") { it.key + "=" + it.value }
            cm.setCookie(
                "https://m.youtube.com",
                "PREF=$v; Domain=.youtube.com; Path=/; Max-Age=31536000; Secure"
            )
            cm.flush()
        } catch (_: Exception) {
        }
    }

    /** Contexto con el tema (claro u oscuro) para los diálogos. */
    private fun dlgCtx() = ContextThemeWrapper(
        this,
        if (Pal.cur.night) R.style.Theme_TubeLD else R.style.Theme_TubeLDDay
    )

    // ---------- botones y servicio ----------

    private fun paint() {
        tint(btnAds, adsOn)
        tint(btnBg, bgOn)
        tint(btnTimer, timerEnd > 0)
        tint(btnEq, eq.on)
        tint(btnQuality, false)
        tintIcon(btnNav, true)
        tintIcon(btnRest, restOn)
        updateBadge()
    }

    private fun updateBadge() {
        tvBadge.text = when {
            updateInfo != null -> "Actualización ⬆"
            adsOn -> "Sin anuncios"
            else -> "Anuncios permitidos"
        }
    }

    private fun tintIcon(b: ImageButton, on: Boolean) {
        ImageViewCompat.setImageTintList(
            b,
            ColorStateList.valueOf(if (on) Pal.cur.gold else Pal.cur.muted)
        )
    }

    /** Icono dorado y un resalte suave detrás cuando la función está activa. */
    private fun tint(b: ImageButton, on: Boolean) {
        tintIcon(b, on)
        val p = Pal.cur
        val shape = { col: Int ->
            InsetDrawable(
                GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(col)
                },
                dp(5), dp(10), dp(5), dp(10)
            )
        }
        val ripple = ColorStateList.valueOf(withA(p.gold, 0x33))
        b.background = if (on) RippleDrawable(ripple, shape(withA(p.gold, 0x24)), null)
        else RippleDrawable(ripple, null, shape(Color.BLACK))
    }

    // ---------- descanso visual ----------

    private fun setupRest() {
        restOn = prefs.getBoolean("rest_on", false)
        restBlue = prefs.getInt("rest_blue", 50)
        restDim = prefs.getInt("rest_dim", 20)
        val v = View(this)
        v.background = restBg
        v.isClickable = false
        v.isFocusable = false
        v.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        root.addView(
            v,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        applyRest()
    }

    /** Ámbar encima (quita azul) y negro encima (baja el brillo); nunca llega a oscurecer del todo. */
    private fun applyRest() {
        val a1 = if (restOn) (restBlue * 0.5f * 2.55f).toInt() else 0
        val a2 = if (restOn) (restDim * 0.6f * 2.55f).toInt() else 0
        (restBg.getDrawable(0) as ColorDrawable).color = withA(0xFFFF8C1A.toInt(), a1)
        (restBg.getDrawable(1) as ColorDrawable).color = withA(Color.BLACK, a2)
        restBg.invalidateSelf()
        if (::btnRest.isInitialized) tintIcon(btnRest, restOn)
    }

    @SuppressLint("InflateParams")
    private fun showRestDialog() {
        val ctx = dlgCtx()
        val dlg = BottomSheetDialog(ctx)
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_rest, null)
        val sw = v.findViewById<MaterialSwitch>(R.id.swRest)
        val slB = v.findViewById<Slider>(R.id.slBlue)
        val slD = v.findViewById<Slider>(R.id.slDim)
        val pct = com.google.android.material.slider.LabelFormatter { "${it.toInt()}%" }
        slB.setLabelFormatter(pct)
        slD.setLabelFormatter(pct)
        var busy = false

        fun refresh() {
            busy = true
            sw.isChecked = restOn
            slB.value = restBlue.toFloat()
            slD.value = restDim.toFloat()
            busy = false
        }
        fun save() {
            prefs.edit().putBoolean("rest_on", restOn).putInt("rest_blue", restBlue)
                .putInt("rest_dim", restDim).apply()
            applyRest()
        }
        sw.setOnCheckedChangeListener { _, on ->
            if (busy) return@setOnCheckedChangeListener
            restOn = on
            save()
        }
        slB.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            restBlue = value.toInt()
            restOn = true
            busy = true
            sw.isChecked = true
            busy = false
            save()
        }
        slD.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            restDim = value.toInt()
            restOn = true
            busy = true
            sw.isChecked = true
            busy = false
            save()
        }
        v.findViewById<Button>(R.id.btnRestPreset).setOnClickListener {
            restBlue = 60
            restDim = 25
            restOn = true
            refresh()
            save()
        }
        dlg.setContentView(v)
        dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dlg.behavior.skipCollapsed = true
        refresh()
        dlg.show()
    }

    // ---------- ecualizador ----------

    private fun eqScript(): String = "window.__tldEqCfg=" + Eq.toJs(eq) + ";" + JS_EQ

    /** Guarda y manda los ajustes a la página (ella los aplica en menos de medio segundo). */
    private fun pushEq() {
        Eq.save(prefs, eq)
        web.evaluateJavascript("window.__tldEqCfg=" + Eq.toJs(eq) + ";", null)
        paint()
    }

    @SuppressLint("InflateParams")
    private fun showEqDialog() {
        val ctx = dlgCtx()
        val dlg = BottomSheetDialog(ctx)
        val v = LayoutInflater.from(ctx).inflate(R.layout.dialog_eq, null)
        val swEq = v.findViewById<MaterialSwitch>(R.id.swEq)
        val bands = v.findViewById<EqBandsView>(R.id.eqBands)
        val chips = v.findViewById<ChipGroup>(R.id.eqPresets)
        val slBass = v.findViewById<Slider>(R.id.slBass)
        val slWide = v.findViewById<Slider>(R.id.slWide)
        val slClar = v.findViewById<Slider>(R.id.slClarity)
        val swLevel = v.findViewById<MaterialSwitch>(R.id.swLevel)
        val slBuf = v.findViewById<Slider>(R.id.slBuf)
        slBuf.setLabelFormatter { when (it.toInt()) { 0 -> "Mínimo retraso"; 1 -> "Equilibrado"; else -> "Máx. estabilidad" } }
        var busy = false

        val chipIds = HashMap<String, Int>()
        for (p in Eq.PRESETS) {
            val c = Chip(ctx)
            c.id = View.generateViewId()
            c.text = p.name
            c.isCheckable = true
            val pl = Pal.cur
            val chk = intArrayOf(android.R.attr.state_checked)
            c.chipBackgroundColor = ColorStateList(
                arrayOf(chk, intArrayOf()), intArrayOf(withA(pl.gold, 0x2E), Color.TRANSPARENT)
            )
            c.chipStrokeColor = ColorStateList(
                arrayOf(chk, intArrayOf()), intArrayOf(pl.gold, withA(pl.muted, 0x88))
            )
            c.setTextColor(
                ColorStateList(arrayOf(chk, intArrayOf()), intArrayOf(pl.gold, pl.text))
            )
            c.chipStrokeWidth = dp(1).toFloat()
            c.isCheckedIconVisible = false
            chips.addView(c)
            chipIds[p.name] = c.id
        }
        val pct = com.google.android.material.slider.LabelFormatter { "${it.toInt()}%" }
        slBass.setLabelFormatter(pct)
        slWide.setLabelFormatter(pct)
        slClar.setLabelFormatter(pct)

        fun refresh() {
            busy = true
            swEq.isChecked = eq.on
            bands.setValues(eq.bands)
            slBass.value = eq.bass.toFloat()
            slWide.value = eq.wide.toFloat()
            slClar.value = eq.clarity.toFloat()
            swLevel.isChecked = eq.level
            slBuf.value = eq.buf.toFloat()
            val id = chipIds[eq.preset]
            if (id != null) chips.check(id) else chips.clearCheck()
            busy = false
        }

        /** Cualquier ajuste a mano: pasa a "Personalizado" y enciende el ecualizador. */
        fun custom() {
            eq.preset = "Personalizado"
            eq.on = true
            busy = true
            chips.clearCheck()
            swEq.isChecked = true
            busy = false
            pushEq()
        }

        chips.setOnCheckedStateChangeListener { _, ids ->
            if (busy) return@setOnCheckedStateChangeListener
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val p = Eq.PRESETS.firstOrNull { chipIds[it.name] == id }
                ?: return@setOnCheckedStateChangeListener
            eq.applyPreset(p)
            eq.on = true
            refresh()
            pushEq()
        }
        swEq.setOnCheckedChangeListener { _, on ->
            if (busy) return@setOnCheckedChangeListener
            eq.on = on
            pushEq()
        }
        bands.onChange = {
            eq.bands = bands.values()
            custom()
        }
        slBass.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                eq.bass = value.toInt()
                custom()
            }
        }
        slWide.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                eq.wide = value.toInt()
                custom()
            }
        }
        slClar.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                eq.clarity = value.toInt()
                custom()
            }
        }
        swLevel.setOnCheckedChangeListener { _, on ->
            if (busy) return@setOnCheckedChangeListener
            eq.level = on
            custom()
        }
        // Se aplica 0,7 s después del último cambio (sirve con el dedo y con las flechas del control remoto).
        val applyBuf = Runnable {
            val b = slBuf.value.toInt()
            if (b != eq.buf) {
                eq.buf = b
                pushEq()
                toast(when (b) { 0 -> "Mínimo retraso"; 1 -> "Equilibrado"; else -> "Máxima estabilidad" })
                web.reload() // el búfer de audio se define al crear el ecualizador en la página
            }
        }
        slBuf.addOnChangeListener { _, _, fromUser ->
            if (fromUser) {
                handler.removeCallbacks(applyBuf)
                handler.postDelayed(applyBuf, 700)
            }
        }
        v.findViewById<Button>(R.id.btnEqReset).setOnClickListener {
            eq.applyPreset(Eq.PRESETS[0])
            refresh()
            pushEq()
        }

        dlg.setContentView(v)
        dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dlg.behavior.skipCollapsed = true
        // Al cerrar, se actualiza el script que corre antes que los de YouTube.
        dlg.setOnDismissListener { installScripts() }
        refresh()
        dlg.show()
    }

    // ---------- comandos de la notificación y temporizador ----------

    /** Ejecuta un comando (play, pausa, siguiente…) dentro de la página de YouTube. */
    private fun runCmd(cmd: String, arg: Long) {
        if (cmd == Media.CLOSE) {
            web.evaluateJavascript("window.__tldCmd&&window.__tldCmd('pause',0)", null)
            finishAndRemoveTask()
            return
        }
        web.evaluateJavascript("window.__tldCmd&&window.__tldCmd('$cmd',$arg)", null)
    }

    private fun showTimerDialog() {
        val opts = arrayOf("15 minutos", "30 minutos", "45 minutos", "1 hora", "Cancelar temporizador")
        val mins = intArrayOf(15, 30, 45, 60, 0)
        val left = if (timerEnd > 0) ((timerEnd - System.currentTimeMillis()) / 60000 + 1).toInt() else 0
        MaterialAlertDialogBuilder(dlgCtx())
            .setTitle(if (left > 0) "Se pausa en $left min" else "Pausar la reproducción en…")
            .setItems(opts) { _, i -> setTimer(mins[i]) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun setTimer(min: Int) {
        handler.removeCallbacks(timerTask)
        if (min > 0) {
            timerEnd = System.currentTimeMillis() + min * 60_000L
            handler.postDelayed(timerTask, min * 60_000L)
            toast("Se pausará en $min min")
        } else {
            timerEnd = 0
            toast("Temporizador cancelado")
        }
        paint()
    }

    private fun syncService() {
        val i = Intent(this, KeepAliveService::class.java)
        if (bgOn) {
            try {
                ContextCompat.startForegroundService(this, i)
            } catch (_: Exception) {
            }
        } else {
            stopService(i)
        }
    }

    /** Aviso en una píldora sobre la barra de abajo (en vez del Toast del sistema). */
    private fun toast(t: String) {
        val p = Pal.cur
        val v = pill ?: TextView(this).also {
            it.textSize = 13f
            it.gravity = Gravity.CENTER
            it.elevation = dp(10).toFloat()
            it.visibility = View.GONE
            root.addView(
                it,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply { bottomMargin = dp(88) }
            )
            pill = it
        }
        handler.removeCallbacks(hidePill)
        v.animate().cancel()
        v.text = t
        v.setTextColor(p.text)
        v.setPadding(dp(18), dp(10), dp(18), dp(10))
        v.background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(p.bar)
            setStroke(dp(1), withA(p.gold, 0x55))
        }
        v.alpha = 0f
        v.translationY = dp(8).toFloat()
        v.visibility = View.VISIBLE
        v.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(180).start()
        handler.postDelayed(hidePill, 1700)
    }

    companion object {
        const val HOME = "https://m.youtube.com/"
        const val HOME_TV = "https://www.youtube.com/"
        /** En Smart TV se usa la web de escritorio (pantalla grande, se maneja con el puntero). */
        const val UA_TV =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        /** Agranda un poco el texto para leerlo desde el sofá. */
        const val TV_TEXT_ZOOM = 100
        /** En modo ligero (web móvil) el texto se agranda más. */
        const val TV_TEXT_ZOOM_LITE = 120
        const val UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        val AD_HOSTS = listOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "2mdn.net",
            "moatads.com"
        )

        /** Desplaza la página (o el panel que haya bajo el puntero) y dice si se movió. Solo Smart TV. */
        const val JS_TV = """
(function(){
  if (window.__tldTv) return; window.__tldTv = true;
  function canScroll(el) {
    try {
      var s = getComputedStyle(el);
      return (s.overflowY === 'auto' || s.overflowY === 'scroll') && el.scrollHeight > el.clientHeight + 2;
    } catch (e) { return false; }
  }
  (function addCss() {
    var t = document.head || document.documentElement;
    if (!t) { setTimeout(addCss, 50); return; }
    var st = document.createElement('style');
    st.textContent = '*{scroll-behavior:auto !important;}.ytp-caption-segment,.captions-text,.caption-visual-line{font-size:15px !important;line-height:1.35 !important;}';
    t.appendChild(st);
  })();
  window.__tldScroll = function(dy, x, y) {
    try {
      var el = document.elementFromPoint(x, y);
      while (el && el !== document.body && el !== document.documentElement) {
        if (canScroll(el)) {
          var b0 = el.scrollTop;
          el.scrollTop = b0 + dy;
          if (Math.abs(el.scrollTop - b0) > 0.5) return true;
        }
        el = el.parentElement;
      }
      var se = document.scrollingElement || document.documentElement;
      var b = se.scrollTop;
      se.scrollTop = b + dy;
      if (Math.abs(se.scrollTop - b) > 0.5) return true;
      b = window.pageYOffset;
      window.scrollBy(0, dy);
      if (Math.abs(window.pageYOffset - b) > 0.5) return true;
      if (document.body) {
        b = document.body.scrollTop;
        document.body.scrollTop = b + dy;
        if (Math.abs(document.body.scrollTop - b) > 0.5) return true;
      }
      // Evita repetir el barrido (pesado en TVs viejas) si hace un momento no habia nada que desplazar.
      var now = Date.now(), hh = se.scrollHeight;
      if (window.__tldNoScroll && now - window.__tldNoScroll.t < 1500 && window.__tldNoScroll.h === hh) return false;
      // Algunas paginas desplazan un panel interno grande en vez del documento.
      var ns = document.querySelectorAll('ytd-app,ytd-page-manager,ytd-watch-flexy,main,section,div'), i, n = Math.min(ns.length, 4000);
      for (i = 0; i < n; i++) {
        var c = ns[i];
        if (c.clientHeight < innerHeight * 0.3 || c.scrollHeight <= c.clientHeight + 2) continue;
        var oy = getComputedStyle(c).overflowY;
        if (oy !== 'auto' && oy !== 'scroll' && oy !== 'overlay') continue;
        var t = c.scrollTop;
        c.scrollTop = t + dy;
        if (Math.abs(c.scrollTop - t) > 0.5) return true;
      }
      window.__tldNoScroll = {t: now, h: hh};
      return false;
    } catch (e) { return false; }
  };
})();
"""


        /** Modo flechas (Smart TV): salta entre los botones, enlaces y videos visibles; OK los toca. */
        const val JS_NAV = """
(function(){
  if (window.__tldNavInit) return; window.__tldNavInit = true;
  var cur = null;
  var SEL = 'a[href],button,input,select,textarea,video,[role=button],[role=link],[role=tab],[role=menuitem],[role=checkbox],[role=switch],[role=radio],[onclick],[tabindex]:not([tabindex="-1"]),ytm-rich-item-renderer,ytm-video-with-context-renderer,ytm-compact-video-renderer,ytm-chip-cloud-chip-renderer';
  (function addCss() {
    var t = document.head || document.documentElement;
    if (!t) { setTimeout(addCss, 50); return; }
    var st = document.createElement('style');
    st.textContent = '.tld-f{outline:4px solid #F5C451 !important;outline-offset:-3px !important;border-radius:8px !important;}';
    t.appendChild(st);
  })();
  function shown(el) {
    var r = el.getBoundingClientRect();
    if (r.width < 10 || r.height < 10) return null;
    if (r.bottom <= 0 || r.top >= innerHeight || r.right <= 0 || r.left >= innerWidth) return null;
    var n = el, k = 0;
    while (n && k < 6) {
      var cs = getComputedStyle(n);
      if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0' || cs.pointerEvents === 'none') return null;
      n = n.parentElement; k++;
    }
    return r;
  }
  function list() {
    var out = [], ns = document.querySelectorAll(SEL), i, j;
    for (i = 0; i < ns.length; i++) { var r = shown(ns[i]); if (r) out.push({el: ns[i], r: r}); }
    var res = [];
    for (i = 0; i < out.length; i++) {
      var inner = false;
      for (j = 0; j < out.length; j++) { if (i !== j && out[i].el.contains(out[j].el)) { inner = true; break; } }
      if (!inner) res.push(out[i]);
    }
    return res;
  }
  function ctr(r) { return {x: (r.left + r.right) / 2, y: (r.top + r.bottom) / 2}; }
  function wake() {
    try {
      var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
      if (p && cur && p.contains(cur)) {
        if (p.wakeUpControls) p.wakeUpControls();
        p.dispatchEvent(new MouseEvent('mousemove', {bubbles: true}));
      }
    } catch (e) {}
  }
  function setCur(el) {
    if (cur && cur.classList) cur.classList.remove('tld-f');
    cur = el;
    if (cur) {
      cur.classList.add('tld-f');
      var r = cur.getBoundingClientRect();
      if (r.top < 70 || r.bottom > innerHeight - 70) { try { cur.scrollIntoView({block: 'center'}); } catch (e) {} }
      wake();
    }
  }
  function first(L) {
    var best = null, bs = 1e9;
    for (var i = 0; i < L.length; i++) {
      var c = ctr(L[i].r), s = Math.abs(c.y - innerHeight * 0.35) + Math.abs(c.x - innerWidth / 2) * 0.3;
      if (s < bs) { bs = s; best = L[i].el; }
    }
    return best;
  }
  function scroll(d) {
    var dy = (d === 'd' ? 1 : -1) * Math.round(innerHeight * 0.6);
    var x = innerWidth / 2, y = innerHeight / 2;
    if (cur && document.contains(cur)) {
      var r = cur.getBoundingClientRect();
      x = Math.min(Math.max((r.left + r.right) / 2, 1), innerWidth - 1);
      y = Math.min(Math.max((r.top + r.bottom) / 2, 1), innerHeight - 1);
    }
    return window.__tldScroll ? window.__tldScroll(dy, x, y) : false;
  }
  window.__tldNavMove = function(d) {
    try {
      var L = list();
      if (!L.length) { return scroll(d) ? 's' : 'e'; }
      var curOk = cur && document.contains(cur) && shown(cur);
      if (!curOk) { setCur(first(L)); return 'm'; }
      var c = ctr(cur.getBoundingClientRect()), best = null, bs = 1e9;
      for (var i = 0; i < L.length; i++) {
        var e = L[i].el;
        if (e === cur) continue;
        var q = ctr(L[i].r), dx = q.x - c.x, dy = q.y - c.y, ok, s;
        if (d === 'r') { ok = dx > 4; s = Math.abs(dx) + 2.5 * Math.abs(dy); }
        else if (d === 'l') { ok = dx < -4; s = Math.abs(dx) + 2.5 * Math.abs(dy); }
        else if (d === 'd') { ok = dy > 4; s = Math.abs(dy) + 2.5 * Math.abs(dx); }
        else { ok = dy < -4; s = Math.abs(dy) + 2.5 * Math.abs(dx); }
        if (ok && s < bs) { bs = s; best = e; }
      }
      if (best) { setCur(best); return 'm'; }
      if (d === 'u' || d === 'd') { return scroll(d) ? 's' : 'e'; }
      return 'x';
    } catch (e) { return 'n'; }
  };
  window.__tldNavPos = function() {
    try {
      if (!cur || !document.contains(cur) || !shown(cur)) { setCur(first(list())); return ''; }
      var c = ctr(cur.getBoundingClientRect());
      wake();
      return Math.round(c.x) + ',' + Math.round(c.y);
    } catch (e) { return ''; }
  };
  window.__tldNavClear = function() {
    try { if (cur) cur.classList.remove('tld-f'); cur = null; } catch (e) {}
  };
})();
"""

        /** Lista y cambia la calidad del video desde la app (sin usar el engranaje del reproductor). */
        const val JS_QUAL = """
(function(){
  if (window.__tldQual) return; window.__tldQual = true;
  function pl() { return document.getElementById('movie_player') || document.querySelector('.html5-video-player'); }
  window.__tldQ = function(act, q) {
    try {
      var p = pl();
      if (!p) return '';
      if (act === 'list') {
        var lv = p.getAvailableQualityLevels ? p.getAvailableQualityLevels() : [];
        var cur = p.getPlaybackQuality ? p.getPlaybackQuality() : '';
        return JSON.stringify({cur: cur, levels: lv});
      }
      if (act === 'set') {
        if (q === 'auto') {
          if (p.setPlaybackQualityRange) p.setPlaybackQualityRange('tiny', 'highres');
        } else {
          if (p.setPlaybackQualityRange) p.setPlaybackQualityRange(q, q);
          if (p.setPlaybackQuality) p.setPlaybackQuality(q);
        }
        return 'ok';
      }
    } catch (e) {}
    return '';
  };
})();
"""

        /** Hace creer a la página que siempre está visible, para que no pause el audio. */
        const val JS_VISIBLE = """
(function(){
  if (window.__tldVis) return; window.__tldVis = true;
  try {
    Object.defineProperty(document, 'hidden', {configurable: true, get: function(){ return false; }});
    Object.defineProperty(document, 'visibilityState', {configurable: true, get: function(){ return 'visible'; }});
  } catch (e) {}
  document.addEventListener('visibilitychange', function(e){ e.stopImmediatePropagation(); }, true);
  try { document.hasFocus = function(){ return true; }; } catch (e) {}
  ['blur', 'webkitvisibilitychange'].forEach(function(n) {
    var h = function(e) { if (e.target === window || e.target === document) e.stopImmediatePropagation(); };
    window.addEventListener(n, h, true);
    document.addEventListener(n, h, true);
  });
})();
"""

        /**
         * Motor del ecualizador (Web Audio): 10 bandas, graves, envolvente (ensancha el estéreo),
         * claridad, nivelador de volumen y limitador final para que no distorsione.
         * Solo se engancha al audio cuando el ecualizador está encendido.
         */
        const val JS_EQ = """
(function(){
  if (window.__tldEqInit) return; window.__tldEqInit = true;
  var FREQS = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
  var DEF = {on: false, bands: [0,0,0,0,0,0,0,0,0,0], bass: 0, wide: 0, clarity: 0, level: false};
  function C() { return window.__tldEqCfg || DEF; }
  var ctx = null, src = null, n = null, el = null, last = '', routed = null;

  function build(v) {
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return;
    var c;
    var bf = C().buf;
    var hint = bf === 0 ? 'interactive' : (bf === 2 ? 0.4 : 0.12);
    try { c = new AC({latencyHint: hint}); } catch (e) { c = new AC(); }
    var s = c.createMediaElementSource(v);
    s.connect(c.destination);
    ctx = c; src = s; routed = 'direct';
    var x = {};
    x.up = c.createGain();
    x.up.channelCount = 2; x.up.channelCountMode = 'explicit'; x.up.channelInterpretation = 'speakers';
    x.pre = c.createGain();
    x.bands = [];
    var prev = x.pre;
    x.up.connect(x.pre);
    for (var i = 0; i < FREQS.length; i++) {
      var b = c.createBiquadFilter();
      b.type = 'peaking'; b.frequency.value = FREQS[i]; b.Q.value = 1.1; b.gain.value = 0;
      prev.connect(b); prev = b; x.bands.push(b);
    }
    x.bass = c.createBiquadFilter(); x.bass.type = 'lowshelf'; x.bass.frequency.value = 100; x.bass.gain.value = 0;
    x.clar = c.createBiquadFilter(); x.clar.type = 'highshelf'; x.clar.frequency.value = 3500; x.clar.gain.value = 0;
    prev.connect(x.bass); x.bass.connect(x.clar);
    // Envolvente: L' = L + w*(L-R)/2 ; R' = R - w*(L-R)/2
    x.split = c.createChannelSplitter(2);
    x.merge = c.createChannelMerger(2);
    x.invR = c.createGain(); x.invR.gain.value = -1;
    x.side = c.createGain(); x.side.gain.value = 0;
    x.negSide = c.createGain(); x.negSide.gain.value = -1;
    x.clar.connect(x.split);
    x.split.connect(x.merge, 0, 0);
    x.split.connect(x.merge, 1, 1);
    x.split.connect(x.side, 0);
    x.split.connect(x.invR, 1);
    x.invR.connect(x.side);
    x.side.connect(x.merge, 0, 0);
    x.side.connect(x.negSide);
    x.negSide.connect(x.merge, 0, 1);
    // Nivelador + limitador
    x.comp = c.createDynamicsCompressor();
    x.comp.threshold.value = 0; x.comp.knee.value = 20; x.comp.ratio.value = 1;
    x.comp.attack.value = 0.01; x.comp.release.value = 0.25;
    x.makeup = c.createGain();
    x.lim = c.createDynamicsCompressor();
    x.lim.threshold.value = -2; x.lim.knee.value = 0; x.lim.ratio.value = 20;
    x.lim.attack.value = 0.003; x.lim.release.value = 0.1;
    // El nivelador (compresor) solo se conecta cuando "Nivelar volumen" está activo: así gasta menos procesador.
    x.merge.connect(x.makeup); x.comp.connect(x.makeup); x.makeup.connect(x.lim); x.lim.connect(c.destination);
    // Analizador: solo mide el nivel para el visualizador (no cambia el sonido). Escucha la señal antes del ecualizador.
    x.an = c.createAnalyser(); x.an.fftSize = 256; x.an.smoothingTimeConstant = 0.6;
    s.connect(x.an);
    x.lv = false;
    n = x;
  }

  function apply() {
    if (!n) return;
    var c = C(), t = ctx.currentTime, maxB = 0, i;
    for (i = 0; i < 10; i++) {
      var g = c.bands[i] || 0;
      n.bands[i].gain.setTargetAtTime(g, t, 0.03);
      if (g > maxB) maxB = g;
    }
    var bassDb = (c.bass || 0) * 0.12, clarDb = (c.clarity || 0) * 0.08;
    if (bassDb > maxB) maxB = bassDb;
    n.bass.gain.setTargetAtTime(bassDb, t, 0.03);
    n.clar.gain.setTargetAtTime(clarDb, t, 0.03);
    n.pre.gain.setTargetAtTime(Math.pow(10, -(maxB * 0.6) / 20), t, 0.05);
    n.side.gain.setTargetAtTime((c.wide || 0) / 100 * 1.2 / 2, t, 0.05);
    var lv = !!c.level;
    if (lv) {
      n.comp.threshold.value = -32; n.comp.ratio.value = 4;
      n.makeup.gain.setTargetAtTime(Math.pow(10, 5 / 20), t, 0.05);
    } else {
      n.makeup.gain.setTargetAtTime(1, t, 0.05);
    }
    if (lv !== n.lv) {
      try { n.merge.disconnect(); } catch (e) {}
      n.merge.connect(lv ? n.comp : n.makeup);
      n.lv = lv;
    }
    var want = c.on ? 'fx' : 'direct';
    if (want !== routed) {
      try { src.disconnect(); } catch (e) {}
      src.connect(c.on ? n.up : ctx.destination);
      try { src.connect(n.an); } catch (e) {}
      routed = want;
    }
  }

  function tick() {
    try {
      var c = C(), v = document.querySelector('video');
      if (c.on && v && v !== el) {
        if (ctx) { try { ctx.close(); } catch (e) {} ctx = null; src = null; n = null; routed = null; }
        el = v;
        try { build(v); } catch (e) { n = null; }
        last = '';
      }
      var j = JSON.stringify(c);
      if (n && j !== last) { last = j; apply(); }
      if (ctx && ctx.state !== 'running') { ctx.resume(); }
    } catch (e) {}
  }
  window.__tldEqResume = function() { try { if (ctx && ctx.state !== 'running') ctx.resume(); } catch (e) {} };
  window.__tldEqState = function() { return ctx ? ctx.state : 'sin eq'; };
  setInterval(tick, 300);
  var vctx = null, van = null, vsrc = null, vel = null, vbins = null, vpk = 0.3, vtry = 0;
  var RNG = [[0,2],[2,4],[4,8],[8,16],[16,32],[32,80]];
  var GAIN = [1, 1.15, 1.4, 1.8, 2.4, 3.4];
  function viz(v) {
    if (n && n.an && ctx && ctx.state === 'running') return n.an;
    if (!v || !v.captureStream) return null;
    if (vel !== v) {
      if (vctx) { try { vctx.close(); } catch (e) {} vctx = null; van = null; vsrc = null; }
      vel = null;
    }
    if (!van) {
      if (Date.now() - vtry < 1000) return null;
      vtry = Date.now();
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return null;
      var st = v.captureStream(), tr = st.getAudioTracks();
      if (!tr.length) return null;
      vctx = new AC();
      vsrc = vctx.createMediaStreamSource(new MediaStream(tr));
      van = vctx.createAnalyser(); van.fftSize = 256; van.smoothingTimeConstant = 0.6;
      vsrc.connect(van);
      vel = v;
    }
    if (vctx.state !== 'running') { try { vctx.resume(); } catch (e) {} }
    return vctx.state === 'running' ? van : null;
  }
  setInterval(function() {
    try {
      if (!window.TLD || !window.TLD.lv) return;
      var v = document.querySelector('video');
      if (!v || v.paused || v.ended) return;
      var an = viz(v);
      if (!an) return;
      if (!vbins || vbins.length !== an.frequencyBinCount) vbins = new Uint8Array(an.frequencyBinCount);
      an.getByteFrequencyData(vbins);
      var raw = [], i, j, mx = 0;
      for (i = 0; i < RNG.length; i++) {
        var sum = 0, cnt = 0;
        for (j = RNG[i][0]; j < RNG[i][1] && j < vbins.length; j++) { sum += vbins[j]; cnt++; }
        var r = (cnt ? sum / cnt / 255 : 0) * GAIN[i];
        raw.push(r);
        if (r > mx) mx = r;
      }
      vpk = Math.max(mx, vpk * 0.995, 0.25);
      var o = [];
      for (i = 0; i < raw.length; i++) { o.push(Math.min(1, Math.pow(raw[i] / vpk, 1.15)).toFixed(2)); }
      window.TLD.lv(o.join(','));
    } catch (e) {}
  }, 80);
})();
"""

        /** Informa qué suena (título, carátula, posición) y obedece los botones de la notificación. */
        const val JS_MEDIA = """
(function(){
  if (window.__tldMedia) return; window.__tldMedia = true;
  function vid() { return document.querySelector('video'); }
  function txt(sel) {
    try { var e = document.querySelector(sel); return e ? (e.textContent || '').trim() : ''; } catch (x) { return ''; }
  }
  function report() {
    try {
      var v = vid();
      if (!v || !window.TLD) return;
      var t = '', a = '', art = '';
      var m = navigator.mediaSession && navigator.mediaSession.metadata;
      if (m) {
        t = m.title || ''; a = m.artist || '';
        var aw = m.artwork;
        if (aw && aw.length) { art = aw[aw.length - 1].src || ''; }
      }
      if (!t) { t = txt('.slim-video-information-title, h2.slim-video-metadata-title, ytm-slim-video-metadata-section-renderer h2, .ytp-title-link, .ytp-title-text'); }
      if (!t) { t = (document.title || '').replace(/ - YouTube${'$'}/, ''); }
      if (t === 'YouTube') { t = ''; }
      if (!a) { a = txt('ytm-slim-owner-renderer .yt-core-attributed-string, .slim-owner-channel-name, ytm-slim-owner-renderer a, .ytp-title-channel-name'); }
      if (!art) {
        var id = '';
        try { id = new URLSearchParams(location.search).get('v') || ''; } catch (e) {}
        if (/^[A-Za-z0-9_-]{6,15}${'$'}/.test(id)) { art = 'https://i.ytimg.com/vi/' + id + '/hqdefault.jpg'; }
      }
      var dur = isFinite(v.duration) ? v.duration * 1000 : 0;
      window.TLD.update(t, a, art, !v.paused && !v.ended, (v.currentTime || 0) * 1000, dur);
    } catch (e) {}
  }
  if (!window.__tldLite) setInterval(report, 1000);

  function find(cls, re) {
    var b = document.querySelector(cls);
    if (b) return b;
    var scope = document.querySelector('#movie_player, .html5-video-player, #player-container-id');
    if (!scope) return null;
    var bs = scope.querySelectorAll('button, [role=button]');
    for (var i = 0; i < bs.length; i++) {
      var l = bs[i].getAttribute('aria-label') || '';
      if (re.test(l)) return bs[i];
    }
    return null;
  }

  function ytp() { return document.getElementById('movie_player') || document.querySelector('.html5-video-player'); }
  // Reproduce usando primero el reproductor de YouTube y, si no, el video; al final prueba su botón.
  function dismissIdle() {
    try {
      var ds = document.querySelectorAll('ytm-confirm-dialog-renderer, yt-confirm-dialog-renderer, ytm-dialog, tp-yt-paper-dialog');
      for (var i = 0; i < ds.length; i++) {
        var t = ds[i].textContent || '';
        if (/(sigues ah|still watching|continuar viendo|continue watching|seguir viendo)/i.test(t)) {
          var b = ds[i].querySelector('#confirm-button button, #confirm-button, button');
          if (b) { b.click(); return true; }
        }
      }
    } catch (e) {}
    return false;
  }

  // Si algo de la página vuelve a pausar el video justo después de dar play, se insiste unos segundos.
  window.__tldWant = 0;
  document.addEventListener('pause', function(e) {
    try {
      if (Date.now() < window.__tldWant && e.target && e.target.tagName === 'VIDEO') {
        var t = e.target;
        setTimeout(function() {
          try {
            if (Date.now() < window.__tldWant && t.paused) {
              var p = ytp();
              if (p && p.playVideo) p.playVideo();
              var r = t.play();
              if (r && r.catch) r.catch(function(){});
            }
          } catch (x) {}
        }, 60);
      }
    } catch (x) {}
  }, true);

  function playIt() {
    var v = vid(), p = ytp(), err = '';
    window.__tldWant = Date.now() + 4000;
    dismissIdle();
    try { if (window.__tldEqResume) window.__tldEqResume(); } catch (e) {}
    try { if (v && v.ended) v.currentTime = 0; } catch (e) {}
    try { if (p && p.playVideo) p.playVideo(); } catch (e) { err = 'api ' + e; }
    try {
      if (v) {
        var r = v.play();
        if (r && r.catch) r.catch(function(e) { err = (e && e.name) || String(e); });
      }
    } catch (e) { err = String(e); }
    setTimeout(function() {
      try {
        dismissIdle();
        var v2 = vid();
        if (v2 && v2.paused) {
          var b = document.querySelector('.ytp-play-button, .player-control-play-pause-icon, .ytp-large-play-button');
          if (b) b.click();
        }
      } catch (e) {}
    }, 400);
    setTimeout(function() {
      try {
        var v3 = vid();
        if (window.TLD && window.TLD.dbg && (!v3 || v3.paused)) {
          var ps = '?';
          try { if (p && p.getPlayerState) ps = p.getPlayerState(); } catch (e) {}
          window.TLD.dbg('Play sin efecto. ' + (!v3 ? 'No hay video. ' : 'ready=' + v3.readyState + ' ended=' + v3.ended + ' ') +
            'yt=' + ps + ' eq=' + (window.__tldEqState ? window.__tldEqState() : '?') + ' foco=' + document.hasFocus() + (err ? ' error=' + err : ''));
        }
      } catch (e) {}
    }, 2500);
  }
  function pauseIt() {
    window.__tldWant = 0;
    var v = vid(), p = ytp();
    try { if (p && p.pauseVideo) p.pauseVideo(); } catch (e) {}
    try { if (v) v.pause(); } catch (e) {}
  }

  /** Pasa al siguiente video: botón del reproductor y, si no sirve, el primer video sugerido (móvil o escritorio). */
  function goNext() {
    var n = find('.ytp-next-button', /(siguiente|next)/i);
    if (n && n.offsetParent !== null && n.getAttribute('aria-disabled') !== 'true') { n.click(); return true; }
    var a = document.querySelector('ytm-compact-video-renderer a[href*="/watch"], ytm-video-with-context-renderer a[href*="/watch"], ' +
      'ytd-compact-video-renderer a[href*="/watch"], ytd-watch-next-secondary-results-renderer a[href*="/watch"], ' +
      '.ytp-endscreen-content a.ytp-videowall-still');
    if (a) { a.click(); return true; }
    return false;
  }

  /** Si al terminar el video la página no pasó sola al siguiente (autoplay de YouTube), lo hace la app. */
  document.addEventListener('ended', function(e) {
    try {
      if (!e.target || e.target.tagName !== 'VIDEO') return;
      var ref = location.href;
      setTimeout(function() {
        try {
          var v = vid();
          if (!v || !v.ended || location.href !== ref) return;
          var pl = document.querySelector('#movie_player, .html5-video-player');
          if (pl && /ad-showing|ad-interrupting/.test(pl.className)) return;
          if (window.TLD && window.TLD.log) window.TLD.log('Fin del video: pasando al siguiente');
          goNext();
        } catch (x) {}
      }, 9500);
    } catch (x) {}
  }, true);

  window.__tldCmd = function(c, arg) {
    try {
      var v = vid();
      if (c === 'play') { playIt(); return; }
      if (c === 'pause') { pauseIt(); return; }
      if (c === 'toggle') { if (v && !v.paused && !v.ended) pauseIt(); else playIt(); return; }
      if (c === 'seek') { if (v) v.currentTime = arg / 1000; return; }
      if (c === 'seekrel') {
        if (v) {
          var d = isFinite(v.duration) ? v.duration : 1e9;
          v.currentTime = Math.max(0, Math.min(d, (v.currentTime || 0) + arg / 1000));
        }
        return;
      }
      if (c === 'next') { goNext(); return; }
      if (c === 'prev') {
        if (v && v.currentTime > 3) { v.currentTime = 0; return; }
        var p = find('.ytp-prev-button', /(anterior|previous)/i);
        if (p) { p.click(); return; }
        if (v) v.currentTime = 0;
      }
    } catch (e) {}
  };
})();
"""

        /** Quita los anuncios de los datos de YouTube antes de que el reproductor los vea. */
        const val JS_ADS = """
(function(){
  if (window.__tldAds) return; window.__tldAds = true;
  var KEYS = ['adPlacements', 'playerAds', 'adSlots', 'adBreakHeartbeatParams'];
  function clean(r) {
    try {
      if (!r || typeof r !== 'object') return r;
      if (Array.isArray(r)) { for (var i = 0; i < r.length; i++) clean(r[i]); return r; }
      for (var k = 0; k < KEYS.length; k++) {
        if (KEYS[k] in r) { try { delete r[KEYS[k]]; } catch (e) {} }
      }
      if (r.playerResponse) clean(r.playerResponse);
    } catch (e) {}
    return r;
  }
  try {
    var _parse = JSON.parse;
    JSON.parse = function() { return clean(_parse.apply(this, arguments)); };
  } catch (e) {}
  try {
    var _json = Response.prototype.json;
    Response.prototype.json = function() {
      return _json.apply(this, arguments).then(function(r) { return clean(r); });
    };
  } catch (e) {}
  try {
    var _ipr = window.ytInitialPlayerResponse;
    Object.defineProperty(window, 'ytInitialPlayerResponse', {
      configurable: true,
      get: function() { return _ipr; },
      set: function(v) { _ipr = clean(v); }
    });
  } catch (e) {}

  var css = 'ytd-ad-slot-renderer, ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer, ' +
    'ytm-companion-ad-renderer, ytm-display-ad-renderer, ytd-display-ad-renderer, ' +
    'ytd-promoted-sparkles-web-renderer, ytd-banner-promo-renderer, ytm-statement-banner-renderer, ' +
    'ytm-brand-video-singleton-renderer, ytm-mealbar-promo-renderer, ytm-ad-slot-renderer, ad-slot-renderer, ' +
    'ytm-promoted-sparkles-text-search-renderer, ytm-in-feed-ad-layout-renderer, ytm-banner-promo-renderer, ' +
    '#masthead-ad, #player-ads, .ytp-ad-overlay-container, .ytp-ad-image-overlay, .ytp-ad-text-overlay ' +
    '{ display: none !important; }';
  function addCss() {
    var t = document.head || document.documentElement;
    if (!t) { setTimeout(addCss, 50); return; }
    var st = document.createElement('style');
    st.textContent = css;
    t.appendChild(st);
  }
  addCss();

  var skipSel = ['.ytp-ad-skip-button', '.ytp-ad-skip-button-modern', '.ytp-skip-ad-button',
    '.ytp-ad-overlay-close-button'];

  function tick() {
    try {
      var v = document.querySelector('video');
      var pl = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
      var ad = pl && (pl.classList.contains('ad-showing') || pl.classList.contains('ad-interrupting'));
      if (ad && v) {
        if (!v.__tldAd) { v.__tldAd = true; v.__tldMuted = v.muted; v.__tldRate = v.playbackRate; }
        v.muted = true;
        v.playbackRate = 16;
        if (isFinite(v.duration) && v.duration > 0) { v.currentTime = v.duration; }
      } else if (v && v.__tldAd) {
        v.__tldAd = false;
        v.muted = v.__tldMuted;
        v.playbackRate = v.__tldRate || 1;
      }
      for (var i = 0; i < skipSel.length; i++) {
        var b = document.querySelector(skipSel[i]);
        if (b) { b.click(); }
      }
    } catch (e) {}
  }
  setInterval(tick, 400);
})();
"""

        /** Recupera el video cuando YouTube muestra "Algo salio mal / recarga la pagina" o el aviso anti-bloqueador. */
        const val JS_FIX = """
(function(){
  if (window.__tldFix) return; window.__tldFix = true;
  var tries = [], lastKey = '';
  function vid() {
    try { var m = location.search.match(/[?&]v=([\w-]{6,15})/); if (m) return m[1]; } catch (e) {}
    try { var s = location.pathname.match(/\/(?:shorts|embed)\/([\w-]{6,15})/); if (s) return s[1]; } catch (e) {}
    return '';
  }
  function player() { return document.querySelector('#movie_player') || document.querySelector('.html5-video-player'); }
  function shown(el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    var st = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && st.display !== 'none' && st.visibility !== 'hidden';
  }
  function errorShown() {
    try {
      var pl = player();
      if (pl && pl.classList.contains('ytp-error')) return true;
      var e = document.querySelector('.ytp-error, .ytp-error-content-wrap, ytm-player-error-view-model');
      if (shown(e)) return true;
      var host = pl || document.querySelector('#player') || document.querySelector('#player-container');
      if (host && shown(host) && /(se produjo un error|actualiza la p[aá]gina|algo sali[oó] mal|an error occurred|something went wrong|refresh the page)/i.test(host.innerText || '')) return true;
      var t = document.querySelector('yt-playability-error-supported-renderers, ytd-playability-error-supported-renderers');
      if (t && shown(t) && /(salió mal|went wrong|recarg|refresh|reload)/i.test(t.textContent || '')) return true;
    } catch (e) {}
    return false;
  }
  function killNag() {
    try {
      var sel = 'ytd-enforcement-message-view-model, tp-yt-paper-dialog.ytd-popup-container, ' +
        'ytm-enforcement-message-view-model, #dialog.ytd-popup-container';
      var nodes = document.querySelectorAll(sel), hit = false;
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i];
        if (/(bloqueador|ad blocker|anuncios|allow ads|enforcement)/i.test((n.textContent || '') + n.tagName)) {
          n.remove(); hit = true;
        }
      }
      if (hit) {
        var bd = document.querySelectorAll('tp-yt-iron-overlay-backdrop, .ytd-popup-container.opened');
        for (var j = 0; j < bd.length; j++) { try { bd[j].remove(); } catch (e) {} }
        var v = document.querySelector('video');
        if (v && v.paused) { try { v.play(); } catch (e) {} }
      }
    } catch (e) {}
  }
  function recover() {
    var id = vid(); if (!id) return;
    var now = Date.now();
    tries = tries.filter(function(t) { return now - t < 90000; });
    var v = document.querySelector('video');
    var t = 0;
    try { t = (v && isFinite(v.currentTime)) ? Math.floor(v.currentTime) : 0; } catch (e) {}
    try { if (window.TLD && window.TLD.dbg) window.TLD.log('Reparando video, intento ' + (tries.length + 1) + ' · ' + location.href.slice(0, 100)); } catch (e) {}
    tries.push(now);
    try { var hh = player(); window.TLD.log('Error de reproductor: ' + ((hh && hh.innerText) || '').replace(/\s+/g, ' ').slice(0, 160)); } catch (e) {}
    var pl = player();
    if (tries.length <= 2 && pl && typeof pl.loadVideoById === 'function') {
      // 1.o y 2.o intento: recarga solo el reproductor, sin recargar la pagina
      try { pl.loadVideoById(id, t); return; } catch (e) {}
    }
    if (tries.length <= 4) {
      // 3.o y 4.o intento: recarga la pagina volviendo al mismo segundo
      var u = location.origin + '/watch?v=' + id + (t > 3 ? '&t=' + t + 's' : '');
      location.replace(u);
    }
  }
  var bad = 0;
  setInterval(function() {
    killNag();
    if (errorShown()) {
      bad++;
      if (bad >= 2) { bad = 0; recover(); }   // espera ~1,6 s para no confundirse con un parpadeo
    } else { bad = 0; }
  }, 800);
})();
"""

        /** Envía los errores de JavaScript de la página al registro de errores de la app. */
        const val JS_LOG = """
(function(){
  if (window.__tldLog) return; window.__tldLog = true;
  var n = 0;
  function send(m) { try { if (n++ < 150 && window.TLD && window.TLD.log) window.TLD.log(String(m).slice(0, 600)); } catch (e) {} }
  window.addEventListener('error', function(e) {
    var t = e && e.target;
    if (t && t !== window && t.tagName) return;
    send('JS error: ' + (e.message || '?') + ' @' + String(e.filename || '').split('/').pop() + ':' + (e.lineno || 0));
  }, true);
  window.addEventListener('unhandledrejection', function(e) {
    var r = e && e.reason;
    send('Promesa rechazada: ' + ((r && (r.stack || r.message)) || r));
  });
  try {
    var oe = console.error;
    console.error = function() {
      try {
        send('console.error: ' + Array.prototype.slice.call(arguments).map(function(a) {
          try { return typeof a === 'string' ? a : JSON.stringify(a); } catch (x) { return String(a); }
        }).join(' '));
      } catch (x) {}
      return oe.apply(console, arguments);
    };
  } catch (e) {}
  send('Página: ' + location.href.slice(0, 120));
})();
"""

        /**
         * En TV: la página de escritorio se maqueta con un ancho mayor (por defecto 1600 px CSS), así el
         * reproductor deja de ser enorme y se ve bien la columna de videos de la derecha.
         * Se puede cambiar desde tubeld.js con: window.__tldWidth = 1800;  (más grande = todo más pequeño)
         */
        const val JS_VIEW = """
(function(){
  if (window.__tldView) return; window.__tldView = true;
  var W = Number(window.__tldWidth) || 1600;
  function apply() {
    try {
      var h = document.head || document.documentElement;
      if (!h) return false;
      var m = document.querySelector('meta[name=viewport]');
      if (!m) { m = document.createElement('meta'); m.setAttribute('name', 'viewport'); h.appendChild(m); }
      var v = 'width=' + W;
      if (m.getAttribute('content') !== v) m.setAttribute('content', v);
      return true;
    } catch (e) { return false; }
  }
  function report() { try { if (window.TLD && window.TLD.vw) window.TLD.vw(window.innerWidth); } catch (e) {} }
  if (!apply()) {
    var n = 0, t = setInterval(function() { if (apply() || ++n > 100) clearInterval(t); }, 30);
  }
  try {
    new MutationObserver(function() { apply(); }).observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}
  report();
  window.addEventListener('resize', report);
  window.addEventListener('load', function() { apply(); report(); });
  setInterval(report, 2000);
})();
"""
    }
}
