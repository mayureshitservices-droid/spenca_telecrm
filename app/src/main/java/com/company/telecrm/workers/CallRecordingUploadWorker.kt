package com.company.telecrm.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.telecrm.api.RetrofitClient
import com.company.telecrm.utils.DeviceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class CallRecordingUploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        DeviceManager.init(applicationContext)
        
        val callId = inputData.getString("CALL_ID") ?: return@withContext Result.failure()
        val recordingUriString = inputData.getString("RECORDING_URI") ?: return@withContext Result.failure()
        
        val deviceId = DeviceManager.getDeviceId()
        val token = DeviceManager.getToken()

        if (deviceId == null || token == null) {
            Log.e("UploadWorker", "Upload failed: Device not registered")
            return@withContext Result.failure()
        }

        var tempFile: File? = null
        try {
            val uri = Uri.parse(recordingUriString)
            tempFile = getFileFromUri(uri)
            
            if (tempFile == null) {
                Log.e("UploadWorker", "Upload failed: Could not convert recording URI to a file for callId: $callId")
                return@withContext Result.failure()
            }

            val requestFile = tempFile.asRequestBody("audio/*".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
            
            val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaTypeOrNull())
            val tokenBody = token.toRequestBody("text/plain".toMediaTypeOrNull())
            val callIdBody = callId.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = RetrofitClient.instance.uploadRecording(
                deviceIdBody, 
                tokenBody, 
                callIdBody, 
                filePart
            ).execute()

            if (response.isSuccessful && response.body()?.success == true) {
                Log.d("UploadWorker", "Recording uploaded successfully for $callId")
                Result.success()
            } else {
                val error = response.errorBody()?.string() ?: "Unknown error"
                Log.e("UploadWorker", "Upload failed with API error: $error for $callId")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("UploadWorker", "General Upload error for $callId", e)
            Result.retry()
        } finally {
            // Delete temporary file always to avoid cache clutter on retry or failure
            tempFile?.let {
                if (it.exists() && it.path.contains(applicationContext.cacheDir.path)) {
                    it.delete()
                }
            }
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = applicationContext.contentResolver.openInputStream(uri) ?: return null
            val file = File(applicationContext.cacheDir, "upload_${System.currentTimeMillis()}.amr")
            val outputStream = FileOutputStream(file)
            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            Log.e("UploadWorker", "Error converting Uri to File", e)
            null
        }
    }
}