package com.mwai.overlay

import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val name = AppPrefs.getName(this)
        val tvGreet = findViewById<TextView>(R.id.tv_greeting)
        val isDev = AppPrefs.isDevMode(this)

        // Greeting
        val greet = if (name.isNotEmpty()) AppPrefs.getGreeting(name) else "MW AI"
        tvGreet.text = if (isDev) "⚡ $greet [DEV MODE]" else greet
        tvGreet.setTextColor(if (isDev) getColor(R.color.accent_green) else getColor(R.color.accent_red))

        // Bottom nav
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setBackgroundColor(getColor(R.color.bg_nav))

        // Load default fragment
        loadFragment(HomeFragment())

        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home     -> loadFragment(HomeFragment())
                R.id.nav_settings -> loadFragment(SettingsFragment())
                R.id.nav_dev      -> if (isDev) loadFragment(DevFragment())
                                     else { toast("⛔ Тільки для розробника"); false.also { } }
            }
            true
        }

        // Show/hide dev tab
        nav.menu.findItem(R.id.nav_dev)?.isVisible = isDev

        // Matrix in background
        val matrix = findViewById<MatrixRainView>(R.id.matrix_bg)
        matrix.alpha = 0.07f
    }

    private fun loadFragment(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, f)
            .commit()
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
