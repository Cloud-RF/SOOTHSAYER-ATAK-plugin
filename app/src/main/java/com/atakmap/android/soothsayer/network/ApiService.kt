package com.atakmap.android.soothsayer.network

import com.atakmap.android.soothsayer.models.request.MultisiteRequest
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.models.response.ResponseModel
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("/area")
    fun sendSingleSiteDataToServer(
        @Body request: TemplateDataModel? = null
    ): Call<ResponseModel>

    @POST("/multisite")
    fun sendMultiSiteDataToServer(
        @Body request: MultisiteRequest? = null
    ): Call<ResponseModel>
}