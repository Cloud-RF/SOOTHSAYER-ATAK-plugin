package com.cloudrf.android.soothsayer.network

import com.cloudrf.android.soothsayer.models.linksmodel.LinkRequest
import com.cloudrf.android.soothsayer.models.linksmodel.LinkResponse
import com.cloudrf.android.soothsayer.models.request.MultisiteRequest
import com.cloudrf.android.soothsayer.models.request.TemplateDataModel
import com.cloudrf.android.soothsayer.models.response.LoginResponse
import com.cloudrf.android.soothsayer.models.response.ResponseModel
import com.cloudrf.android.soothsayer.models.response.TemplatesResponse
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*


interface ApiService {

    @POST("/area")
    fun sendSingleSiteDataToServer(
        @Body request: TemplateDataModel? = null
    ): Call<ResponseModel>

    @POST("/multisite")
    fun sendMultiSiteDataToServer(
        @Body request: MultisiteRequest? = null
    ): Call<ResponseModel>

    @Streaming
    @GET
    fun downloadFile(@Url fileUrl: String?): Call<ResponseBody>

    @POST("/points")
    fun getLinks(
        @Body request: LinkRequest? = null
    ): Call<LinkResponse>

    @FormUrlEncoded
    @POST("/auth")
    fun loginUser(
        @Field("user") userName: String?,
        @Field("pass") password: String?
    ): Call<LoginResponse?>?

    @GET("/templates")
    fun getUserTemplates(): Call<TemplatesResponse?>?

    @GET("/template/{id}")
    fun getTemplateDetail(@Path("id") id :Int): Call<TemplateDataModel?>?
}