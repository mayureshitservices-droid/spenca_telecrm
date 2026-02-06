package com.company.telecrm.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.telecrm.api.CallOutcomeRequest
import com.company.telecrm.api.RetrofitClient
import com.company.telecrm.utils.DeviceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallOutcomeWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        DeviceManager.init(applicationContext)
        
        val callId = inputData.getString("CALL_ID") ?: return@withContext Result.failure()
        val customerName = inputData.getString("CUSTOMER_NAME")
        val outcome = inputData.getString("OUTCOME")
        val remarks = inputData.getString("REMARKS")
        val followUpDate = inputData.getLong("FOLLOW_UP_DATE", 0)
        val productQuantitiesJson = inputData.getString("PRODUCT_QUANTITIES")
        val needBranding = inputData.getBoolean("NEED_BRANDING", false)
        val reasonForLoss = inputData.getString("REASON_FOR_LOSS")
        val distributor = inputData.getString("DISTRIBUTOR")

        val productQuantities: Map<String, Int>? = productQuantitiesJson?.let {
            val type = object : TypeToken<Map<String, Int>>() {}.type
            Gson().fromJson(it, type)
        }

        val deviceId = DeviceManager.getDeviceId()
        val token = DeviceManager.getToken()

        if (deviceId == null || token == null) {
            Log.e("CallOutcomeWorker", "Sync failed: Device not registered")
            return@withContext Result.failure()
        }

        val request = CallOutcomeRequest(
            deviceId = deviceId,
            token = token,
            callId = callId,
            customerName = customerName,
            outcome = outcome,
            remarks = remarks,
            followUpDate = if (followUpDate == 0L) null else followUpDate,
            productQuantities = productQuantities,
            needBranding = needBranding,
            reasonForLoss = reasonForLoss,
            distributor = distributor
        )

        return@withContext try {
            val response = RetrofitClient.instance.syncCallOutcome(request).execute()
            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("CallOutcomeWorker", "Call outcome synced successfully for $callId")
                Result.success()
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Log.e("CallOutcomeWorker", "Call outcome sync failed: $error")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("CallOutcomeWorker", "Call outcome sync error", e)
            Result.retry()
        }
    }
}