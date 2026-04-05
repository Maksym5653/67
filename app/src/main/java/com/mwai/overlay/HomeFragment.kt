package com.mwai.overlay

import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.fragment.app.Fragment

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val OVERLAY_CODE = 1001
    private val handler = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnLaunch   = view.findViewById<Button>(R.id.btn_launch)
        val btnStop     = view.findViewById<Button>(R.id.btn_stop)
        val tvStatus    = view.findViewById<TextView>(R.id.tv_status)
        val statusDot   = view.findViewById<View>(R.id.status_dot)
        val tvStats     = view.findViewById<TextView>(R.id.tv_stats)

        updateStatus(tvStatus, statusDot, btnLaunch)
        updateStats(tvStats)
        startStatusPulse(statusDot)

        // Animate cards in
        listOf(
            view.findViewById<View>(R.id.card_status),
            view.findViewById<View>(R.id.card_actions),
            view.findViewById<View>(R.id.card_stats)
        ).forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 60f
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(80L + i * 100).setDuration(400)
                .setInterpolator(DecelerateInterpolator()).start()
        }

        btnLaunch.setOnClickListener {
            popAnim(it)
            handler.postDelayed({
                if (Settings.canDrawOverlays(requireContext())) startService()
                else requestOverlay()
            }, 120)
        }

        btnStop.setOnClickListener {
            popAnim(it)
            requireContext().stopService(Intent(requireContext(), OverlayService::class.java))
            handler.postDelayed({ updateStatus(tvStatus, statusDot, btnLaunch) }, 300)
        }
    }

    private fun updateStats(tv: TextView) {
        val ctx = requireContext()
        val users = AppPrefs.getUsers(ctx)
        val launches = users.size
        tv.text = "Запусків: $launches  ·  Версія: 3.0  ·  Engine: Gemini 2.0"
    }

    private fun startService() {
        startActivity(Intent(requireContext(), ScreenCaptureActivity::class.java))
        Toast.makeText(requireContext(), "⚡ MW AI запускається...", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlay() {
        Toast.makeText(requireContext(), "Дозволь відображення поверх інших додатків", Toast.LENGTH_LONG).show()
        startActivityForResult(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}")), OVERLAY_CODE)
    }

    private fun updateStatus(tv: TextView, dot: View, btn: Button) {
        val running = OverlayService.isRunning
        tv.text = if (running) "● АКТИВНО" else "○ НЕАКТИВНО"
        tv.setTextColor(requireContext().getColor(
            if (running) R.color.accent_green else R.color.text_secondary))
        dot.setBackgroundResource(
            if (running) R.drawable.bg_dot_green else R.drawable.bg_dot_inactive)
        btn.text = if (running) "⟳  ПЕРЕЗАПУСТИТИ" else "▶  ЗАПУСТИТИ MW AI"
    }

    private fun startStatusPulse(dot: View) {
        val anim = ScaleAnimation(1f, 1.6f, 1f, 1.6f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f).apply {
            duration = 800; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        dot.startAnimation(anim)
    }

    private fun popAnim(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                .setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            updateStatus(
                it.findViewById(R.id.tv_status),
                it.findViewById(R.id.status_dot),
                it.findViewById(R.id.btn_launch)
            )
        }
    }
}
