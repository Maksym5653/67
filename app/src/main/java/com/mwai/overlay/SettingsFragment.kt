package com.mwai.overlay

import android.os.Bundle
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        val etKey       = view.findViewById<EditText>(R.id.et_api_key)
        val btnSaveKey  = view.findViewById<Button>(R.id.btn_save_key)
        val tvKeyStatus = view.findViewById<TextView>(R.id.tv_key_status)
        val rgTapMode   = view.findViewById<RadioGroup>(R.id.rg_tap_mode)
        val rbSingle    = view.findViewById<RadioButton>(R.id.rb_single)
        val rbDouble    = view.findViewById<RadioButton>(R.id.rb_double)
        val switchReset = view.findViewById<Switch>(R.id.switch_reset_name)
        val tvName      = view.findViewById<TextView>(R.id.tv_current_name)

        // Show masked key
        val savedKey = AppPrefs.getApiKey(ctx)
        etKey.setText(savedKey)
        updateKeyStatus(tvKeyStatus, savedKey)

        // Tap mode
        if (AppPrefs.getTapMode(ctx) == "single") rbSingle.isChecked = true
        else rbDouble.isChecked = true

        rgTapMode.setOnCheckedChangeListener { _, id ->
            AppPrefs.setTapMode(ctx, if (id == R.id.rb_single) "single" else "double")
            Toast.makeText(ctx, "✓ Збережено", Toast.LENGTH_SHORT).show()
        }

        // Current name
        val name = AppPrefs.getName(ctx)
        tvName.text = "Поточне ім'я: ${if (name.isNotEmpty()) name else "не задано"}"

        btnSaveKey.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.length > 10) {
                AppPrefs.setApiKey(ctx, key)
                updateKeyStatus(tvKeyStatus, key)
                Toast.makeText(ctx, "✓ Ключ збережено", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "Введи коректний ключ!", Toast.LENGTH_SHORT).show()
            }
        }

        switchReset.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                AppPrefs.setName(ctx, "")
                AppPrefs.setDevMode(ctx, false)
                Toast.makeText(ctx, "Ім'я скинуто. Перезапусти додаток.", Toast.LENGTH_LONG).show()
            }
        }

        // Animate
        listOf(
            view.findViewById<View>(R.id.card_key),
            view.findViewById<View>(R.id.card_tap),
            view.findViewById<View>(R.id.card_account)
        ).forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 50f
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(60L + i * 90).setDuration(380)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun updateKeyStatus(tv: TextView, key: String) {
        if (key.length > 10) {
            val masked = key.take(8) + "••••••••" + key.takeLast(4)
            tv.text = "🔑 $masked"
            tv.setTextColor(requireContext().getColor(R.color.accent_green))
        } else {
            tv.text = "⚠ Ключ не налаштовано"
            tv.setTextColor(requireContext().getColor(R.color.accent_red))
        }
    }
}
