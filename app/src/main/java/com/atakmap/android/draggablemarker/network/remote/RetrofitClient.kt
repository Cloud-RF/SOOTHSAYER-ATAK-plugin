package com.atakmap.android.draggablemarker.network.remote

import com.atakmap.android.draggablemarker.network.ApiService
import com.atakmap.android.draggablemarker.util.Constant
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


object RetrofitClient {
    private const val mContentType = "Content-type"
    private const val mContentTypeJson = "application/json"
    private const val mCookie = "Cookie"
    private const val mCookieValue = "PHPSESSID=j613liqg80g0b6e8h06ta9h7e2"
    private const val mAuthorizationKey = "key"


    private val BASE_URL = Constant.sServerUrl

    private val OK_HTTP_CLIENT by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthorizationInterceptor())
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
    }

    private val RETROFIT by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(OK_HTTP_CLIENT.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy { RETROFIT.create(ApiService::class.java) }

    private class AuthorizationInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val authRequest = originalRequest.newBuilder()
                .header(mContentType, mContentTypeJson)
                .header(mCookie, mCookieValue)
                .header(
                    mAuthorizationKey, Constant.sAccessToken
                ) // This should be from user input.
                .build()
            return chain.proceed(authRequest)
        }
    }
}