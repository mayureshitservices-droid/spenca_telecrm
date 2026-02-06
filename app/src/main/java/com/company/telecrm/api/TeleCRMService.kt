package com.company.telecrm.api

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface TeleCRMService {
    @POST("api/telecrm/register")
    fun registerDevice(@Body request: DeviceRegistrationRequest): Call<DeviceRegistrationResponse>
}
