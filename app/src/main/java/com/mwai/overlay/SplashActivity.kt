package com.mwai.overlay

import android.content.Intent
import android.graphics.Typeface
import android.os.*
import android.view.*
import android.view.animation.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val h = Handler(Looper.getMainLooper())
    private var typingIndex = 0
    private val bootLines = listOf(
        "> INITIALIZING MW_AI_SYSTEM...",
        "> LOADING NEURAL NETWORKS......",
        "> CONNECTING TO GEMINI API.....",
        "> BYPASSING SECURITY LAYERS....",
        "> SYSTEM READY. IDENTIFY USER.."
    )
    private var currentLine = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val matrix      = findViewById<MatrixRainView>(R.id.matrix_rain)
        val tvBoot      = findViewById<TextView>(R.id.tv_boot_text)
        val layoutBoot  = findViewById<View>(R.id.layout_boot)
        val layoutLogin = findViewById<View>(R.id.layout_login)
        val etName      = findViewById<EditText>(R.id.et_name)
        val btnEnter    = findViewById<Button>(R.id.btn_enter)
        val tvWarning   = findViewById<TextView>(R.id.tv_warning)
        val tvDevBadge  = findViewById<TextView>(R.id.tv_dev_badge)

        // Matrix starts immediately
        matrix.startRain()

        // If already has name — skip to main
        val savedName = AppPrefs.getName(this)
        if (savedName.isNotEmpty()) {
            h.postDelayed({ goToMain() }, 800)
            return
        }

        // Animate boot screen
        layoutLogin.visibility = View.GONE
        layoutBoot.visibility = View.VISIBLE
        tvBoot.typeface = Typeface.MONOSPACE

        startBootSequence(tvBoot) {
            // Boot done → show login
            layoutBoot.animate().alpha(0f).setDuration(400).withEndAction {
                layoutBoot.visibility = View.GONE
                layoutLogin.visibility = View.VISIBLE
                layoutLogin.alpha = 0f
                layoutLogin.animate().alpha(1f).setDuration(500).start()
                etName.requestFocus()

                // Blink cursor on hint
                startBlinkEffect(btnEnter)
            }.start()
        }

        btnEnter.setOnClickListener { processName(etName, tvWarning, tvDevBadge) }
        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                processName(etName, tvWarning, tvDevBadge); true
            } else false
        }
    }

    private fun startBootSequence(tv: TextView, onDone: () -> Unit) {
        if (currentLine >= bootLines.size) { h.postDelayed(onDone, 300); return }
        typingIndex = 0
        typeNextChar(tv, bootLines[currentLine]) {
            h.postDelayed({
                currentLine++
                startBootSequence(tv, onDone)
            }, 200)
        }
    }

    private fun typeNextChar(tv: TextView, line: String, onDone: () -> Unit) {
        if (typingIndex > line.length) { onDone(); return }
        val displayed = bootLines.take(currentLine).joinToString("\n") +
                if (currentLine > 0) "\n" else "" +
                line.substring(0, typingIndex) + "█"
        tv.text = displayed
        typingIndex++
        h.postDelayed({ typeNextChar(tv, line, onDone) }, 35)
    }

    private fun processName(et: EditText, tvWarning: TextView, tvDevBadge: TextView) {
        val input = et.text.toString().trim()
        if (input.isEmpty()) {
            shakeView(et)
            tvWarning.text = "> ПОМИЛКА: ВВЕДИ ІМ'Я"
            tvWarning.visibility = View.VISIBLE
            return
        }

        if (input == AppPrefs.DEV_CODE) {
            // DEV MODE
            AppPrefs.setDevMode(this, true)
            AppPrefs.setName(this, "Developer")
            AppPrefs.logUser(this, "Developer[DEV]")
            tvDevBadge.visibility = View.VISIBLE
            tvWarning.text = "> ROOT ACCESS GRANTED ✓"
            tvWarning.setTextColor(getColor(R.color.accent_green))
            tvWarning.visibility = View.VISIBLE
            h.postDelayed({ glitchTransition { goToMain() } }, 1000)
        } else {
            AppPrefs.setDevMode(this, false)
            AppPrefs.setName(this, input)
            AppPrefs.logUser(this, input)
            tvWarning.text = "> ІДЕНТИФІКАЦІЯ УСПІШНА ✓"
            tvWarning.setTextColor(getColor(R.color.accent_green))
            tvWarning.visibility = View.VISIBLE
            h.postDelayed({ glitchTransition { goToMain() } }, 800)
        }
    }

    private fun glitchTransition(onDone: () -> Unit) {
        val root = findViewById<View>(android.R.id.content)
        var count = 0
        val glitch = object : Runnable {
            override fun run() {
                root.translationX = if (count % 2 == 0) 8f else -8f
                count++
                if (count < 6) h.postDelayed(this, 50)
                else {
                    root.translationX = 0f
                    root.animate().alpha(0f).setDuration(300).withEndAction { onDone() }.start()
                }
            }
        }
        h.post(glitch)
    }

    private fun shakeView(v: View) {
        val shake = TranslateAnimation(-15f, 15f, 0f, 0f).apply {
            duration = 60; repeatCount = 5; repeatMode = Animation.REVERSE
        }
        v.startAnimation(shake)
    }

    private fun startBlinkEffect(btn: Button) {
        h.post(object : Runnable {
            var visible = true
            override fun run() {
                btn.alpha = if (visible) 1f else 0.4f
                visible = !visible
                h.postDelayed(this, 600)
            }
        })
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
