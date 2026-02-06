package com.company.telecrm.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.telecrm.api.HeartbeatRequest
import com.company.telecrm.api.RetrofitClient
import com.company.telecrm.utils.DeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HeartbeatWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        DeviceManager.init(applicationContext)
        val deviceId = DeviceManager.getDeviceId()
        val token = DeviceManager.getToken()

        if (deviceId == null || token == null) {
            Log.e("HeartbeatWorker", "Heartbeat failed: Device not registered")
            return@withContext Result.failure()
        }

        val request = HeartbeatRequest(deviceId = deviceId, token = token)

        return@withContext try {
            val response = RetrofitClient.instance.heartbeat(request).execute()
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("HeartbeatWorker", "Heartbeat sent successfully")
                Result.success()
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Log.e("HeartbeatWorker", "Heartbeat failed: $error")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("HeartbeatWorker", "Heartbeat error", e)
            Result.retry()
        }
    }
}
