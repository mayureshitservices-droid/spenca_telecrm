package com.company.telecrm.api

data class CallLogRequest(
    val deviceId: String,
    val token: String,
    val callId: String,
    val phoneNumber: String,
    val callStatus: String,
    val duration: Long,
    val timestamp: Long,
    val recordingUrl: String?
)
