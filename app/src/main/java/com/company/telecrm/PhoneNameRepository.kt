package com.company.telecrm

import android.content.Context

class PhoneNameRepository(context: Context) {
    private val prefs = context.getSharedPreferences("phone_names", Context.MODE_PRIVATE)

    fun saveName(phoneNumber: String, name: String) {
        prefs.edit().putString(phoneNumber, name).apply()
    }

    fun getName(phoneNumber: String): String? {
        return prefs.getString(phoneNumber, null)
    }
}
