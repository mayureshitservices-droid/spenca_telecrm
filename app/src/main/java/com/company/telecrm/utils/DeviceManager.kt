package com.company.telecrm.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.company.telecrm.api.RegisterRequest
import com.company.telecrm.api.RegisterResponse
import com.company.telecrm.api.RetrofitClient
import com.company.telecrm.workers.HeartbeatWorker
import androidx.work.*
import retrofit2.Call
import java.util.concurrent.TimeUnit
import retrofit2.Callback
import retrofit2.Response

object DeviceManager {
    private const val PREF_NAME = "TeleCrmPrefs"
    private const val KEY_IS_REGISTERED = "is_registered"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_TOKEN = "token"

    private var prefs: SharedPreferences? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isInitialized = true
    }

    private fun getPrefs(context: Context? = null): SharedPreferences? {
        if (!isInitialized && context != null) {
            init(context)
        }
        return prefs
    }

    fun isRegistered(): Boolean {
        return prefs?.getBoolean(KEY_IS_REGISTERED, false) ?: false
    }

    fun getDeviceId(): String? {
        return prefs?.getString(KEY_DEVICE_ID, null)
    }

    fun getToken(): String? {
        return prefs?.getString(KEY_TOKEN, null)
    }

    fun registerDevice(callback: (Boolean) -> Unit) {
        if (isRegistered()) {
            Log.d("DeviceManager", "Device already registered")
            callback(true)
            return
        }

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        val request = RegisterRequest(deviceName = deviceName)

        RetrofitClient.instance.register(request).enqueue(object : Callback<RegisterResponse> {
            override fun onResponse(
                call: Call<RegisterResponse>,
                response: Response<RegisterResponse>
            ) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val body = response.body()!!
                    val saved = saveCredentials(body.deviceId, body.token)
                    if (saved) {
                        Log.d("DeviceManager", "Registration successful: ${body.deviceId}")
                        callback(true)
                    } else {
                        Log.e("DeviceManager", "Registration failed: Missing deviceId or token in response")
                        callback(false)
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("DeviceManager", "Registration failed. Code: ${response.code()}, Message: ${response.message()}, Body: $errorBody")
                    callback(false)
                }
            }

            override fun onFailure(call: Call<RegisterResponse>, t: Throwable) {
                Log.e("DeviceManager", "Registration error: ${t.message}", t)
                callback(false)
            }
        })
    }

    fun startHeartbeat(context: Context) {
        if (!isRegistered()) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val heartbeatRequest = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            "TeleCrmHeartbeat",
            ExistingPeriodicWorkPolicy.KEEP,
            heartbeatRequest
        )
        Log.d("DeviceManager", "Heartbeat scheduled")
    }

    private fun saveCredentials(deviceId: String?, token: String?): Boolean {
        if (deviceId == null || token == null) return false
        
        prefs?.edit()
            ?.putBoolean(KEY_IS_REGISTERED, true)
            ?.putString(KEY_DEVICE_ID, deviceId)
            ?.putString(KEY_TOKEN, token)
            ?.apply()
        return true
    }
}
