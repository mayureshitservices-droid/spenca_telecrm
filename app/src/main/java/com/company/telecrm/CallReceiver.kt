package com.company.telecrm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class CallReceiver : BroadcastReceiver() {

    private val TAG = "CallReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called with action: ${intent.action}")
        if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            Log.d(TAG, "Outgoing call detected to: $phoneNumber")
            val serviceIntent = Intent(context, CallRecordingService::class.java)
            serviceIntent.putExtra("PHONE_NUMBER", phoneNumber)
            ContextCompat.startForegroundService(context, serviceIntent)
            Log.d(TAG, "CallRecordingService requested to start.")
        }
    }
}