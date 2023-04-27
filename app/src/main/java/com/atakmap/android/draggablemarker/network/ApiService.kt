package com.atakmap.android.draggablemarker.network

import com.atakmap.android.draggablemarker.models.request.MultisiteRequest
import com.atakmap.android.draggablemarker.models.request.TemplateDataModel
import com.atakmap.android.draggablemarker.models.response.ResponseModel
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("/area")
    fun sendSingleSiteDataToServer(
//        @Url url: String? = Constant.sServerUrl.substringAfter(RetrofitClient.BASE_URL),
        @Body request: TemplateDataModel? = null
    ): Call<Any>

    @POST("/multisite")
    fun sendMultiSiteDataToServer(
        @Body request: MultisiteRequest? = null
    ): Call<ResponseModel>
}