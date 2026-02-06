package com.company.telecrm.api

data class CallOutcomeRequest(
    val deviceId: String,
    val token: String,
    val callId: String,
    val customerName: String?,
    val outcome: String?,
    val remarks: String?,
    val followUpDate: Long?,
    val productQuantities: Map<String, Int>?,
    val needBranding: Boolean,
    val reasonForLoss: String?,
    val distributor: String?
)
