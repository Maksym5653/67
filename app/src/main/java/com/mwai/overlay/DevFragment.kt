package com.mwai.overlay

import android.os.Bundle
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class DevFragment : Fragment(R.layout.fragment_dev) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        val tvUsers  = view.findViewById<TextView>(R.id.tv_users_log)
        val tvStats  = view.findViewById<TextView>(R.id.tv_dev_stats)
        val btnClear = view.findViewById<Button>(R.id.btn_clear_log)

        loadUsers(tvUsers, tvStats)

        btnClear.setOnClickListener {
            ctx.getSharedPreferences("mwai_prefs", android.content.Context.MODE_PRIVATE)
                .edit().remove("user_log").apply()
            loadUsers(tvUsers, tvStats)
            Toast.makeText(ctx, "✓ Лог очищено", Toast.LENGTH_SHORT).show()
        }

        // Animate
        listOf(
            view.findViewById<View>(R.id.card_dev_header),
            view.findViewById<View>(R.id.card_users),
            view.findViewById<View>(R.id.card_dev_actions)
        ).forEachIndexed { i, v ->
            v.alpha = 0f; v.translationY = 50f
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(50L + i * 100).setDuration(380)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun loadUsers(tvUsers: TextView, tvStats: TextView) {
        val users = AppPrefs.getUsers(requireContext())
        val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

        if (users.isEmpty()) {
            tvUsers.text = "> НЕМАЄ ДАНИХ"
        } else {
            tvUsers.text = users.joinToString("\n") { (name, ts) ->
                val time = if (ts > 0) sdf.format(Date(ts)) else "??:??"
                "> [$time] $name"
            }
        }

        tvStats.text = "TOTAL USERS: ${users.size}\nUNIQUE: ${users.map { it.first }.toSet().size}\nDEV BUILD: v3.0"
    }
}
