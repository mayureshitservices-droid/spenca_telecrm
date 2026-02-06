package com.company.telecrm.utils

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TeleCRM_Prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_IS_REGISTERED = "is_registered"
    }

    fun saveDeviceCredentials(deviceId: String, token: String) {
        prefs.edit().apply {
            putString(KEY_DEVICE_ID, deviceId)
            putString(KEY_TOKEN, token)
            putBoolean(KEY_IS_REGISTERED, true)
            apply()
        }
    }

    fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)
    fun isRegistered(): Boolean = prefs.getBoolean(KEY_IS_REGISTERED, false)
}
