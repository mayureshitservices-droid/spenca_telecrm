package com.company.telecrm.api

data class RegisterResponse(
    val success: Boolean,
    val deviceId: String?,
    val token: String?
)
