package com.xanderscannell.startinggundetector.device

import android.content.Context

class UserPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var latencyOffsetMs: Int
        get() = prefs.getInt(KEY_LATENCY_OFFSET, 0)
        set(value) { prefs.edit().putInt(KEY_LATENCY_OFFSET, value).apply() }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) { prefs.edit().putString(KEY_USERNAME, value).apply() }

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, DEFAULT_SENSITIVITY)
        set(value) { prefs.edit().putFloat(KEY_SENSITIVITY, value).apply() }

    companion object {
        const val DEFAULT_SENSITIVITY: Float = 7f

        private const val PREFS_NAME = "gun_detector_prefs"
        private const val KEY_LATENCY_OFFSET = "latency_offset_ms"
        private const val KEY_USERNAME = "username"
        private const val KEY_SENSITIVITY = "sensitivity"
    }
}
