package com.company.telecrm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.company.telecrm.api.CallLogRequest
import com.company.telecrm.api.GeneralResponse
import com.company.telecrm.api.RetrofitClient
import com.company.telecrm.utils.DeviceManager
import com.company.telecrm.workers.CallSyncWorker
import com.company.telecrm.workers.CallRecordingUploadWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@Suppress("DEPRECATION")
class CallRecordingService : Service() {

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null

    private var callStartTime: Long = 0  // When dialing starts
    private var phoneNumber: String = ""
    private var currentCallId: String = ""

    private val tag = "CallRecordingService"
    private val channelId = "CallRecordingServiceChannel"
    private val notificationId = 1
    private val executor = Executors.newSingleThreadExecutor()

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "CallRecordingService onCreate called.")

        createNotificationChannel()
        val notification = createNotification("Service running, waiting for call...")
        startForeground(notificationId, notification)

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, incomingNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // Ignoring incoming calls as per requirements
                        Log.d(tag, "CALL_STATE_RINGING: Incoming call ignored.")
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // For outgoing calls, OFFHOOK fires immediately on dial, not on answer
                        // We now use Call Log to determine actual answer status
                        Log.d(tag, "CALL_STATE_OFFHOOK: Call in progress")
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        Log.d(tag, "CALL_STATE_IDLE.")
                        if (phoneNumber.isNotEmpty()) {
                            val callEndTime = System.currentTimeMillis()
                            val captureNumber = phoneNumber
                            val callId = currentCallId
                            val startTime = callStartTime
                            
                            // Move everything to background thread to avoid blocking UI/Main thread
                            executor.execute {
                                var processedStatus = "Missed"
                                var processedDuration: Long = 0

                                // Wait 1 second to ensure Call Log is updated
                                Thread.sleep(1000)
                                
                                try {
                                    val projection = arrayOf(
                                        android.provider.CallLog.Calls.TYPE,
                                        android.provider.CallLog.Calls.DURATION,
                                        android.provider.CallLog.Calls.DATE
                                    )
                                    // Expand time window: 10 seconds before start to 5 seconds after end
                                    val selection = "${android.provider.CallLog.Calls.NUMBER} = ? AND ${android.provider.CallLog.Calls.DATE} >= ? AND ${android.provider.CallLog.Calls.DATE} <= ?"
                                    val selectionArgs = arrayOf(
                                        captureNumber, 
                                        (startTime - 10000).toString(),
                                        (callEndTime + 5000).toString()
                                    )
                                    
                                    Log.d(tag, "Querying Call Log for $captureNumber between ${startTime - 10000} and ${callEndTime + 5000}")
                                    
                                    val cursor = contentResolver.query(
                                        android.provider.CallLog.Calls.CONTENT_URI,
                                        projection,
                                        selection,
                                        selectionArgs,
                                        "${android.provider.CallLog.Calls.DATE} DESC"
                                    )
                                    
                                    cursor?.use {
                                        if (it.moveToFirst()) {
                                            val callType = it.getInt(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.TYPE))
                                            val durationSeconds = it.getLong(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.DURATION))
                                            val callDate = it.getLong(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.DATE))
                                            processedDuration = durationSeconds * 1000 // Convert to milliseconds
                                            
                                            processedStatus = when (callType) {
                                                android.provider.CallLog.Calls.OUTGOING_TYPE -> {
                                                    if (durationSeconds >= 2) "Answered" else "Missed"
                                                }
                                                else -> "Missed"
                                            }
                                            
                                            Log.d(tag, "Call Log Found: number=$captureNumber, type=$callType, duration=${durationSeconds}s, date=$callDate, status=$processedStatus")
                                        } else {
                                            Log.w(tag, "No call log entry found for $captureNumber in time window")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(tag, "Error reading call log", e)
                                }

                                // 1. Log and Sync IMMEDIATELY (without recording first)
                                logAndSyncCall(callId, captureNumber, startTime, callEndTime, processedDuration, processedStatus, null)

                                // 2. Start polling for recording in background to update local path
                                // Note: Already on background thread from outer executor.execute
                                // Polling for Realme/ODialer: check every 2 seconds up to 5 times (total 10s)
                                for (i in 1..5) {
                                    Log.d(tag, "Polling for recording (Attempt $i)...")
                                    Thread.sleep(2000)
                                    val recordingUri = findRecordingUri(captureNumber, startTime, callEndTime)
                                    if (recordingUri != null) {
                                        Log.d(tag, "Recording found on attempt $i: $recordingUri")
                                        updateLocalRecordingPath(callId, captureNumber, startTime, callEndTime, processedDuration, processedStatus, recordingUri.toString())
                                        break
                                    }
                                }
                            }
                        }
                        phoneNumber = "" // Reset for next call
                        updateNotification("TeleCRM Service Active")
                    }
                }
            }
        }
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("PHONE_NUMBER") == true) {
            phoneNumber = intent.getStringExtra("PHONE_NUMBER") ?: ""
            if (phoneNumber.isNotEmpty()) {
                // Initialize tracking variables immediately when call is initiated
                callStartTime = System.currentTimeMillis()
                currentCallId = java.util.UUID.randomUUID().toString()
                Log.d(tag, "Outgoing number received: $phoneNumber, callId: $currentCallId")
            }
        }
        return START_STICKY
    }

    private fun logAndSyncCall(callId: String, number: String, startTime: Long, endTime: Long, duration: Long, status: String, recordingPath: String?) {
        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val email = sharedPref.getString("USER_EMAIL", "") ?: ""
        
        val durationStr = String.format(Locale.getDefault(), "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration),
            TimeUnit.MILLISECONDS.toSeconds(duration) % 60)

        val repository = CallHistoryRepository(this)
        val call = CallHistory(callId, number, durationStr, recordingPath ?: "", email, endTime, status)
        repository.saveCallHistory(call)
        Log.d(tag, "Call logged locally: $number, status: $status, callId: $callId")

        // Notify UI
        sendBroadcast(Intent("com.company.telecrm.UPDATE_CALL_LOG"))

        // Sync to Server
        syncCallToServer(callId, number, status, duration, endTime)
    }

    private fun updateLocalRecordingPath(callId: String, number: String, startTime: Long, endTime: Long, duration: Long, status: String, recordingPath: String) {
        val sharedPref = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val email = sharedPref.getString("USER_EMAIL", "") ?: ""
        
        val durationStr = String.format(Locale.getDefault(), "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration),
            TimeUnit.MILLISECONDS.toSeconds(duration) % 60)

        val repository = CallHistoryRepository(this)
        val call = CallHistory(callId, number, durationStr, recordingPath, email, endTime, status)
        repository.saveCallHistory(call)
        Log.d(tag, "Local recording path updated: $recordingPath for callId: $callId")
        
        // Notify UI again to show play button
        sendBroadcast(Intent("com.company.telecrm.UPDATE_CALL_LOG"))

        // Trigger Upload to Server
        enqueueUploadTask(callId, recordingPath)
    }

    private fun findRecordingUri(number: String, startTime: Long, endTime: Long): Uri? {
        val selection = "${MediaStore.Audio.Media.DATE_ADDED} >= ? AND ${MediaStore.Audio.Media.DATE_ADDED} <= ?"
        val selectionArgs = arrayOf(
            (startTime / 1000 - 10).toString(),
            (endTime / 1000 + 10).toString() // Adjusted buffer to 10s as requested
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        var recordingUri: Uri? = null
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME),
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                recordingUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return recordingUri
    }

    private fun syncCallToServer(callId: String, phoneNumber: String, status: String, duration: Long, timestamp: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncData = Data.Builder()
            .putString("CALL_ID", callId)
            .putString("PHONE_NUMBER", phoneNumber)
            .putString("STATUS", status)
            .putLong("DURATION", duration / 1000) // Seconds
            .putLong("TIMESTAMP", timestamp)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<CallSyncWorker>()
            .setConstraints(constraints)
            .setInputData(syncData)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(syncRequest)
        Log.d(tag, "Enqueued CallSyncWorker for $phoneNumber with callId $callId")
    }

    private fun enqueueUploadTask(callId: String, recordingUri: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadData = Data.Builder()
            .putString("CALL_ID", callId)
            .putString("RECORDING_URI", recordingUri)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<CallRecordingUploadWorker>()
            .setConstraints(constraints)
            .setInputData(uploadData)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(uploadRequest)
        Log.d(tag, "Enqueued CallRecordingUploadWorker for callId $callId")
    }


    override fun onDestroy() {
        super.onDestroy()
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Call Monitor", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("TeleCRM")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_logo)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
