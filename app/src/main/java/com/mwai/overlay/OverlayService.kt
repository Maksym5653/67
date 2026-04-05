package com.mwai.overlay

import android.app.*
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "mwai_ch"
        const val GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    }

    private lateinit var wm: WindowManager
    private var fabView: View? = null
    private var panelView: View? = null
    private var ghostView: View? = null

    private var fabSavedX = 24
    private var fabSavedY = 0
    private var isPanelVisible = false
    private var lastFabTap = 0L
    private var lastGhostTap = 0L
    private val DBL = 380L

    // MediaProjection — stored as fields, recreated on each capture
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projResultCode = -1
    private var projData: Intent? = null

    private val handler = Handler(Looper.getMainLooper())
    private var screenW = 0
    private var screenH = 0

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val m = resources.displayMetrics
        screenW = m.widthPixels; screenH = m.heightPixels
        fabSavedY = screenH / 3
        createNotificationChannel()
        startForeground(1, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }

        projResultCode = intent?.getIntExtra("result_code", -1) ?: -1
        projData = intent?.getParcelableExtra("data")

        if (projResultCode != -1 && projData != null) {
            initProjection()
        }
        showFab()
        return START_NOT_STICKY
    }

    private fun initProjection() {
        try {
            // Clean up previous
            try { virtualDisplay?.release() } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            try { mediaProjection?.stop() } catch (_: Exception) {}

            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(projResultCode, projData!!)

            imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "MwAI_cap", screenW, screenH,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )
        } catch (e: Exception) {
            handler.post { showToast("MediaProjection помилка: ${e.message}") }
            mediaProjection = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        safeRemove(fabView); safeRemove(panelView); safeRemove(ghostView)
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?) = null

    // ─── FAB ──────────────────────────────────────────────────────

    private fun showFab() {
        if (fabView != null) return
        fabView = LayoutInflater.from(this).inflate(R.layout.view_fab, null)

        val lp = makeFabParams()
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var drag = false

        fabView!!.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = lp.x; iy = lp.y; tx = e.rawX; ty = e.rawY; drag = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX-tx) > 10 || Math.abs(e.rawY-ty) > 10) {
                        drag = true
                        lp.x = (ix-(e.rawX-tx)).toInt(); lp.y = (iy+(e.rawY-ty)).toInt()
                        try { wm.updateViewLayout(fabView, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!drag) {
                        fabSavedX = lp.x; fabSavedY = lp.y
                        val now = System.currentTimeMillis()
                        val tapMode = AppPrefs.getTapMode(this)
                        if (tapMode == "single") {
                            // Single tap = analyze, long tap = hide
                            if (now - lastFabTap < 200) {
                                animFabOut { showGhost() }
                            } else {
                                spawnRipple(lp.x, lp.y)
                                if (isPanelVisible) hidePanel() else captureAndAnalyze()
                            }
                        } else {
                            // Double tap = hide
                            if (now - lastFabTap < DBL) {
                                animFabOut { showGhost() }
                            } else {
                                handler.postDelayed({
                                    if (System.currentTimeMillis() - lastFabTap >= DBL) {
                                        spawnRipple(lp.x, lp.y)
                                        if (isPanelVisible) hidePanel() else captureAndAnalyze()
                                    }
                                }, DBL)
                            }
                        }
                        lastFabTap = now
                    } else {
                        fabSavedX = lp.x; fabSavedY = lp.y
                    }
                    true
                }
                else -> false
            }
        }

        wm.addView(fabView, lp)
        fabView!!.scaleX = 0f; fabView!!.scaleY = 0f; fabView!!.alpha = 0f
        fabView!!.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450)
            .setInterpolator(OvershootInterpolator(2f)).start()
        startGlowPulse()
    }

    private fun makeFabParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.END; x = fabSavedX; y = fabSavedY }

    private fun animFabOut(done: () -> Unit) {
        fabView?.animate()?.scaleX(0f)?.scaleY(0f)?.alpha(0f)?.setDuration(220)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction { safeRemove(fabView); fabView = null; done() }?.start()
    }

    private fun startGlowPulse() {
        val glow = fabView?.findViewById<View>(R.id.fab_glow) ?: return
        handler.post(object : Runnable {
            override fun run() {
                if (fabView == null) return
                glow.animate().alpha(0.1f).scaleX(1.8f).scaleY(1.8f).setDuration(1000)
                    .withEndAction {
                        glow.animate().alpha(0.8f).scaleX(1f).scaleY(1f).setDuration(1000)
                            .withEndAction { if (fabView != null) handler.post(this) }.start()
                    }.start()
            }
        })
    }

    // ─── GHOST ────────────────────────────────────────────────────

    private fun showGhost() {
        safeRemove(ghostView)
        ghostView = View(this)
        val lp = WindowManager.LayoutParams(90, 90,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = fabSavedX; y = fabSavedY }

        ghostView!!.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) {
                val now = System.currentTimeMillis()
                val tapMode = AppPrefs.getTapMode(this)
                val threshold = if (tapMode == "single") 400L else DBL
                if (now - lastGhostTap < threshold) {
                    safeRemove(ghostView); ghostView = null; showFab()
                }
                lastGhostTap = now
            }
            true
        }
        wm.addView(ghostView, lp)
    }

    // ─── RIPPLE ───────────────────────────────────────────────────

    private fun spawnRipple(x: Int, y: Int) {
        try {
            val rv = View(this)
            rv.setBackgroundResource(R.drawable.bg_ripple)
            val sz = 130
            val lp = WindowManager.LayoutParams(sz, sz,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.END; this.x = x - sz/4; this.y = y - sz/4 }
            wm.addView(rv, lp)
            rv.scaleX = 0f; rv.scaleY = 0f; rv.alpha = 0.9f
            rv.animate().scaleX(2.2f).scaleY(2.2f).alpha(0f).setDuration(500)
                .withEndAction { safeRemove(rv) }.start()
        } catch (_: Exception) {}
    }

    // ─── CAPTURE ──────────────────────────────────────────────────

    private fun captureAndAnalyze() {
        if (mediaProjection == null) {
            // Try reinit
            if (projResultCode != -1 && projData != null) {
                initProjection()
                handler.postDelayed({
                    if (mediaProjection != null) captureAndAnalyze()
                    else showPanel(loading = false,
                        text = "❌ Не вдалося захопити екран.\n\n" +
                            "Зупини MW AI і запусти знову.\n" +
                            "При запиті натисни ► 'Почати запис'")
                }, 800)
                showPanel(loading = true)
            } else {
                showPanel(loading = false,
                    text = "❌ Немає дозволу на захоплення екрану.\n\nЗупини і запусти MW AI знову.")
            }
            return
        }
        showPanel(loading = true)
        Thread {
            Thread.sleep(400) // wait for panel to draw, then capture clean screen
            val bmp = captureScreen()
            if (bmp == null) {
                handler.post { updatePanel("❌ Скріншот не вдався.\n\nСпробуй ще раз або перезапусти MW AI.") }
                return@Thread
            }
            callGemini(toBase64(bmp))
        }.start()
    }

    private fun captureScreen(): Bitmap? {
        return try {
            var attempts = 0
            var img = imageReader?.acquireLatestImage()
            while (img == null && attempts < 5) {
                Thread.sleep(100); attempts++
                img = imageReader?.acquireLatestImage()
            }
            img ?: return null
            val p = img.planes[0]
            val rowPad = p.rowStride - p.pixelStride * screenW
            val bmp = Bitmap.createBitmap(screenW + rowPad / p.pixelStride, screenH, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(p.buffer)
            img.close()
            Bitmap.createBitmap(bmp, 0, 0, screenW, screenH)
        } catch (e: Exception) { null }
    }

    private fun toBase64(bmp: Bitmap): String {
        val w = minOf(1080, bmp.width)
        val h = (w.toFloat() * bmp.height / bmp.width).toInt()
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ─── GEMINI ───────────────────────────────────────────────────

    private fun callGemini(b64: String) {
        val key = AppPrefs.getApiKey(this)
        val isDev = AppPrefs.isDevMode(this)
        val name = AppPrefs.getName(this)

        val prompt = buildString {
            append("Ти — MW AI, вбудований AI-асистент для Android")
            if (name.isNotEmpty()) append(", ти допомагаєш користувачу на ім'я $name")
            append(".\n\n")
            append("Уважно проаналізуй скріншот і:\n")
            append("• Питання/тест → точні відповіді\n")
            append("• Гра → стратегія і наступний крок\n")
            append("• Форма → допомога із заповненням\n")
            append("• Математика → покажи рішення\n")
            append("• Текст → стисне резюме\n")
            append("• Переклад → перекладай\n")
            if (isDev) append("• [DEV] Також вкажи технічні деталі скріншоту\n")
            append("\nВідповідай українською. Коротко, структуровано. Макс 300 слів.")
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg"); put("data", b64)
                        })
                    })
                    put(JSONObject().apply { put("text", prompt) })
                })
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3); put("maxOutputTokens", 1024)
            })
        }

        val req = Request.Builder().url("$GEMINI_URL?key=$key")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { updatePanel("❌ Немає інтернету:\n${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                val rb = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    try {
                        val txt = JSONObject(rb).getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0).getString("text")
                        handler.post { updatePanel(txt) }
                    } catch (e: Exception) {
                        handler.post { updatePanel("❌ Помилка розбору:\n$rb") }
                    }
                } else {
                    val err = try { JSONObject(rb).getJSONObject("error").getString("message") }
                              catch (_: Exception) { "HTTP ${response.code}" }
                    handler.post { updatePanel("❌ Gemini: $err") }
                }
            }
        })
    }

    // ─── PANEL ────────────────────────────────────────────────────

    private fun showPanel(loading: Boolean, text: String = "") {
        if (panelView != null) { updatePanel(if (loading) null else text); return }
        panelView = LayoutInflater.from(this).inflate(R.layout.view_panel, null)

        val lp = WindowManager.LayoutParams(
            (screenW * 0.93).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 80 }

        val pb     = panelView!!.findViewById<ProgressBar>(R.id.progress_bar)
        val tv     = panelView!!.findViewById<TextView>(R.id.tv_content)
        val btnX   = panelView!!.findViewById<ImageButton>(R.id.btn_close)
        val btnR   = panelView!!.findViewById<ImageButton>(R.id.btn_refresh)
        val tvName = panelView!!.findViewById<TextView>(R.id.tv_panel_name)

        val name = AppPrefs.getName(this)
        tvName.text = if (name.isNotEmpty()) "MW AI  ·  $name" else "MW AI"

        if (loading) { pb.visibility = View.VISIBLE; tv.text = "⚡ Аналізую екран..." }
        else if (text.isNotEmpty()) { pb.visibility = View.GONE; tv.text = text }

        var closeTap = 0L
        btnX.setOnClickListener {
            val now = System.currentTimeMillis()
            popAnim(btnX)
            if (now - closeTap < DBL) { hidePanel(); animFabOut {}; showGhost() }
            else hidePanel()
            closeTap = now
        }

        btnR.setOnClickListener {
            popAnim(btnR)
            updatePanel(null)
            Thread {
                Thread.sleep(300)
                val bmp = captureScreen()
                if (bmp != null) callGemini(toBase64(bmp))
                else handler.post { updatePanel("❌ Не вдалося зробити скріншот") }
            }.start()
        }

        var iy = 0; var ty2 = 0f
        panelView!!.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { iy = lp.y; ty2 = e.rawY; false }
                MotionEvent.ACTION_MOVE -> {
                    lp.y = (iy - (e.rawY - ty2)).toInt().coerceAtLeast(0)
                    try { wm.updateViewLayout(panelView, lp) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        wm.addView(panelView, lp)
        panelView!!.translationY = 600f; panelView!!.alpha = 0f
        panelView!!.animate().translationY(0f).alpha(1f).setDuration(380)
            .setInterpolator(DecelerateInterpolator(1.8f)).start()
        isPanelVisible = true
    }

    private fun updatePanel(text: String?) {
        if (panelView == null) { showPanel(text == null); return }
        val pb = panelView!!.findViewById<ProgressBar>(R.id.progress_bar)
        val tv = panelView!!.findViewById<TextView>(R.id.tv_content)
        if (text == null) { pb.visibility = View.VISIBLE; tv.text = "⚡ Аналізую..." }
        else { pb.visibility = View.GONE; tv.alpha = 0f; tv.text = text; tv.animate().alpha(1f).setDuration(350).start() }
    }

    private fun hidePanel() {
        panelView?.animate()?.translationY(600f)?.alpha(0f)?.setDuration(300)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction { safeRemove(panelView); panelView = null; isPanelVisible = false }?.start()
    }

    private fun popAnim(v: View) {
        v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(70).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    // ─── HELPERS ──────────────────────────────────────────────────

    private fun safeRemove(v: View?) { try { v?.let { wm.removeView(it) } } catch (_: Exception) {} }
    private fun showToast(msg: String) { handler.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() } }

    private fun createNotificationChannel() {
        NotificationChannel(CHANNEL_ID, "MW AI", NotificationManager.IMPORTANCE_LOW)
            .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, OverlayService::class.java).apply { action = "STOP" }, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MW AI активний ⚡")
            .setContentText("Натисни MW AI для аналізу екрану")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_delete, "Стоп", stop)
            .setOngoing(true).build()
    }
}
