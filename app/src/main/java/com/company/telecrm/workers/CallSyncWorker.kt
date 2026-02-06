package com.company.telecrm.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.telecrm.api.CallLogRequest
import com.company.telecrm.api.RetrofitClient
import com.company.telecrm.utils.DeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        DeviceManager.init(applicationContext)
        val phoneNumber = inputData.getString("PHONE_NUMBER") ?: return@withContext Result.failure()
        val status = inputData.getString("STATUS") ?: return@withContext Result.failure()
        val callId = inputData.getString("CALL_ID") ?: return@withContext Result.failure()
        val duration = inputData.getLong("DURATION", 0)
        val timestamp = inputData.getLong("TIMESTAMP", 0)

        val deviceId = DeviceManager.getDeviceId()
        val token = DeviceManager.getToken()

        if (deviceId == null || token == null) {
            Log.e("CallSyncWorker", "Sync failed: Device not registered")
            return@withContext Result.failure()
        }

        val request = CallLogRequest(
            deviceId = deviceId,
            token = token,
            callId = callId,
            phoneNumber = phoneNumber,
            callStatus = status,
            duration = duration,
            timestamp = timestamp,
            recordingUrl = null // Explicitly null as per instructions
        )

        return@withContext try {
            val response = RetrofitClient.instance.syncCallLog(request).execute()
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("CallSyncWorker", "Call log synced successfully for $phoneNumber")
                Result.success()
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Log.e("CallSyncWorker", "Call log sync failed: $error")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("CallSyncWorker", "Call log sync error", e)
            Result.retry()
        }
    }
}
