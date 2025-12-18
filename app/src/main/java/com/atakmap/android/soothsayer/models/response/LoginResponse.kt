package com.cloudrf.android.soothsayer.models.response

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("api_key")
    val apiKey: String?,
    val error : String?
)