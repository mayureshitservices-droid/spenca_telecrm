package com.company.telecrm

data class CallHistory(
    val callId: String = "",
    val phoneNumber: String,
    val callDuration: String,
    val recordingPath: String,
    val telecallerEmail: String,
    val timestamp: Long,
    val status: String,
    val customerName: String? = null,
    val outcome: String? = null, // Ordered, Call Later, Other Concerns, Lost
    val remarks: String? = null,
    val followUpDate: Long? = null,
    val productQuantities: Map<String, Int>? = null,
    val needBranding: Boolean = false,
    val reasonForLoss: String? = null,
    val distributor: String? = null
)