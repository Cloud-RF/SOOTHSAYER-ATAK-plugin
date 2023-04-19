package com.atakmap.android.draggablemarker.network

import com.atakmap.android.draggablemarker.models.TemplateDataModel
import com.atakmap.android.draggablemarker.models.response.AreaResponseModel
import com.atakmap.android.draggablemarker.util.Constant
import retrofit2.Call
import retrofit2.http.Body

import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {

    //    @POST("/area")
    @POST
    fun sendMarkerDataToServer(
        @Url url: String? = Constant.sServerUrl,
        @Body request: TemplateDataModel? = null
    ): Call<Any>
}