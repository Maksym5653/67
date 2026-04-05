package com.mwai.overlay

import android.content.Context

object AppPrefs {
    private const val FILE = "mwai_prefs"
    const val DEV_CODE = "mak00066891"
    // Obfuscated key storage — stored as reversed+shifted
    private const val KEY_API = "xqs_lzj"
    private const val KEY_NAME = "user_name"
    private const val KEY_DEV = "dev_mode"
    private const val KEY_TAP_MODE = "tap_mode"  // "single" or "double"
    private const val KEY_USERS = "user_log"
    private const val DEFAULT_KEY = "AIzaSyCdSIIIT25FqueEJTU7FX0qOHwaZNFFsjQ"

    fun getApiKey(ctx: Context): String {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_API, null) ?: return DEFAULT_KEY
        return decode(raw)
    }

    fun setApiKey(ctx: Context, key: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_API, encode(key)).apply()
    }

    fun getName(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_NAME, "") ?: ""

    fun setName(ctx: Context, name: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, name).apply()

    fun isDevMode(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_DEV, false)

    fun setDevMode(ctx: Context, on: Boolean) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_DEV, on).apply()

    fun getTapMode(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TAP_MODE, "double") ?: "double"

    fun setTapMode(ctx: Context, mode: String) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_TAP_MODE, mode).apply()

    fun logUser(ctx: Context, name: String) {
        val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_USERS, "") ?: ""
        val ts = System.currentTimeMillis()
        val entry = "$name|$ts\n"
        prefs.edit().putString(KEY_USERS, existing + entry).apply()
    }

    fun getUsers(ctx: Context): List<Pair<String, Long>> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_USERS, "") ?: ""
        return raw.trim().lines().filter { it.contains("|") }.map {
            val parts = it.split("|")
            Pair(parts[0], parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        }.sortedByDescending { it.second }
    }

    // Simple obfuscation — XOR with key
    private fun encode(s: String): String =
        s.map { (it.code xor 0x5A).toChar() }.joinToString("")

    private fun decode(s: String): String =
        s.map { (it.code xor 0x5A).toChar() }.joinToString("")

    fun getGreeting(name: String): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val time = when {
            hour in 5..11  -> "Доброго ранку"
            hour in 12..17 -> "Добрий день"
            hour in 18..22 -> "Добрий вечір"
            else           -> "Доброї ночі"
        }
        return "$time, $name!"
    }
}
