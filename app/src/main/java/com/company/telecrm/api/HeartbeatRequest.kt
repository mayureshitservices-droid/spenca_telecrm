package com.company.telecrm.api

data class HeartbeatRequest(
    val deviceId: String,
    val token: String
)
