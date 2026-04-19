package com.xanderscannell.startinggundetector.device

import android.content.Context
import java.util.UUID

object DeviceIdProvider {
    private const val PREFS_NAME = "gun_detector_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    fun getDeviceId(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    fun shortId(deviceId: String): String = deviceId.takeLast(4).uppercase()
}
