package com.company.telecrm.api

data class DeviceRegistrationResponse(
    val success: Boolean,
    val message: String,
    val deviceId: String,
    val token: String
)
