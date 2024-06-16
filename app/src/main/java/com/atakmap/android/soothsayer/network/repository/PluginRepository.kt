package com.atakmap.android.soothsayer.network.repository

import android.util.Log
import android.webkit.URLUtil
import com.atakmap.android.soothsayer.PluginDropDownReceiver
import com.atakmap.android.soothsayer.models.linksmodel.LinkRequest
import com.atakmap.android.soothsayer.models.linksmodel.LinkResponse
import com.atakmap.android.soothsayer.models.request.MultisiteRequest
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.models.response.LoginResponse
import com.atakmap.android.soothsayer.models.response.ResponseModel
import com.atakmap.android.soothsayer.models.response.TemplatesResponse
import com.atakmap.android.soothsayer.models.response.TemplatesResponseItem
import com.atakmap.android.soothsayer.network.remote.RetrofitClient
import com.atakmap.android.soothsayer.util.Constant
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


class PluginRepository {

    companion object {
        private var INSTANCE: PluginRepository? = null

        fun getInstance() = INSTANCE ?: synchronized(PluginRepository::class.java) {
            INSTANCE ?: PluginRepository().also { INSTANCE = it }
        }
    }


    fun sendSingleSiteMarkerData(request: TemplateDataModel, callback: ApiCallBacks? = null) {
        callback?.onLoading()
        if (URLUtil.isValidUrl(RetrofitClient.BASE_URL)) {
            RetrofitClient.apiService()?.sendSingleSiteDataToServer(request = request)
                ?.enqueue(object : Callback<ResponseModel> {
                    override fun onResponse(
                        call: Call<ResponseModel>, response: Response<ResponseModel>
                    ) {
                        if (response.isSuccessful) {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendMarkerData success :${response.raw()}"
                            )
                            callback?.onSuccess(response.body())
                        } else {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendMarkerData onFailed called: ${response.code()} ${response.raw()}"
                            )
                            callback?.onFailed(response.errorBody()?.string(), response.code())
                        }
                    }

                    override fun onFailure(call: Call<ResponseModel>, t: Throwable) {
                        try {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendMarkerData override fun onFailure called Request ${call.request()}  \n Error: ${t.localizedMessage}"
                            )
                            callback?.onFailed(t.message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            callback?.onFailed(e.printStackTrace().toString())
                        }
                    }
                })
        } else {
            callback?.onFailed("", Constant.ApiErrorCodes.sForbidden)
        }

    }

    // DANGER!
    fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        }

        val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
            val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory

        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier(HostnameVerifier { _, _ -> true })
        return this
    }
    fun downloadFile(url:String, downloadFolder :String,fileName :String, listener: (Boolean, String) -> Unit){
        val client = RetrofitClient.getUnsafeOkHttpClient().build()
        val request = Request.Builder()
                .url(url)
                .build()
        val call = client.newCall(request)
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                // Handle failure
                listener(false, e.printStackTrace().toString())
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val inputStream = response.body?.byteStream()
//                val path = FOLDER_PATH
                // folder doesn't exists.
                val folder = File(downloadFolder)
                if (!folder.exists()) {
                    Log.d(PluginDropDownReceiver.TAG, "downloadFile creating  new folder....")
                    folder.mkdirs()
                }
                val file = File(downloadFolder, fileName)
                val outputStream = FileOutputStream(file)
                inputStream?.copyTo(outputStream)
                outputStream.close()
                inputStream?.close()
                Log.d(PluginDropDownReceiver.TAG, "downloadFile: close file: path:${file.path}")
                listener(file.exists(), file.path)
            }
        })
    }

    fun sendMultiSiteMarkerData(request: MultisiteRequest, callback: ApiCallBacks? = null) {
        callback?.onLoading()
        if (URLUtil.isValidUrl(RetrofitClient.BASE_URL)) {
            Log.d(
                PluginDropDownReceiver.TAG,
                "sendMultiSiteMarkerData request ${Gson().toJson(request)}"
            )
            RetrofitClient.apiService()?.sendMultiSiteDataToServer(request = request)
                ?.enqueue(object : Callback<ResponseModel> {
                    override fun onResponse(
                        call: Call<ResponseModel>, response: Response<ResponseModel>
                    ) {
                        if (response.isSuccessful) {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendMultiSiteMarkerData success :${response.raw()}"
                            )
                            callback?.onSuccess(response.body())
                        } else {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendMultiSiteMarkerData onFailed called ${response.code()} ${response.raw()}"
                            )
                            callback?.onFailed(response.errorBody()?.string(), response.code())
                        }
                    }

                    override fun onFailure(call: Call<ResponseModel>, t: Throwable) {
                        try {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendMarkerData override fun onFailure called Request ${call.request()}  \n Error: ${t.localizedMessage}"
                            )
                            callback?.onFailed(t.message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            callback?.onFailed(e.printStackTrace().toString())
                        }
                    }
                })
        } else {
            callback?.onFailed("", Constant.ApiErrorCodes.sForbidden)
        }

    }

    fun getLinks(request: LinkRequest, callback: ApiCallBacks? = null){
        callback?.onLoading()
        if (URLUtil.isValidUrl(RetrofitClient.BASE_URL)) {
            Log.d(PluginDropDownReceiver.TAG, "sendLinks Request :${Gson().toJson(request)}")
            RetrofitClient.apiService()?.getLinks(request = request)
                ?.enqueue(object : Callback<LinkResponse> {
                    override fun onResponse(
                        call: Call<LinkResponse>, response: Response<LinkResponse>
                    ) {
                        if (response.isSuccessful) {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendLinks success :${response.raw()} \nbody: ${response.body()}"
                            )
                            // below part is setting markerId in response object's transmitter so that we can use that id in link creation.
                            response.body()?.transmitters?.let { transmitters ->
                                for(transmitter in transmitters){
                                    for( point in request.points){
                                        if(point.lat == transmitter.latitude && point.lon == transmitter.longitude){
                                            transmitter.markerId = point.markerId
                                        }
                                    }
                                }
                            }
                            callback?.onSuccess(response.body())
                        } else {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendLinks onFailed called ${response.code()} ${response.raw()}"
                            )
                            callback?.onFailed(response.errorBody()?.string(), response.code())
                        }
                    }

                    override fun onFailure(call: Call<LinkResponse>, t: Throwable) {
                        try {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "sendLinks onFailed called ${call.request()}  \n Error: ${t.localizedMessage}"
                            )
                            callback?.onFailed(t.message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            callback?.onFailed(e.printStackTrace().toString())
                        }
                    }
                })
        } else {
            Log.d(
                PluginDropDownReceiver.TAG,
                "forbidden"
            )
            callback?.onFailed("", Constant.ApiErrorCodes.sForbidden)
        }
    }

    fun loginUser(username: String, password :String, callback: ApiCallBacks? = null) {
        callback?.onLoading()
        if (URLUtil.isValidUrl(RetrofitClient.BASE_URL)) {
            RetrofitClient.apiService()?.loginUser(username, password)
                ?.enqueue(object : Callback<LoginResponse?> {
                    override fun onResponse(
                        call: Call<LoginResponse?>, response: Response<LoginResponse?>
                    ) {
                        if (response.isSuccessful) {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "loginUser success :${response.raw()}"
                            )
                            callback?.onSuccess(response.body())
                        } else {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "loginUser onFailed called ${response.code()} ${response.raw()} error: ${response.body()} "
                            )
                            try {
                                val errorObject = JSONObject(response.errorBody()!!.string())
                                val errorMessage = errorObject.getString("error")
                                Log.d(
                                    PluginDropDownReceiver.TAG,
                                    "loginUser onFailed called error: $errorMessage"
                                )
                                callback?.onFailed(errorMessage, response.code())
                            } catch (e: java.lang.Exception) {
                                e.printStackTrace()
                                callback?.onFailed(response.message(), response.code())
                            }

//                            callback?.onFailed(response.message(), response.code())
                        }
                    }

                    override fun onFailure(call: Call<LoginResponse?>, t: Throwable) {
                        try {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "loginUser override fun onFailure called Request ${call.request()}  \n Error: ${t.localizedMessage}"
                            )
                            callback?.onFailed(t.message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            callback?.onFailed(e.printStackTrace().toString())
                        }
                    }
                })
        } else {
            callback?.onFailed("", Constant.ApiErrorCodes.sForbidden)
        }

    }

    fun downloadTemplates(callback: ApiCallBacks? = null) {
        callback?.onLoading()
        Log.d("PluginDropDownReceiver", " BASE_URL: ${RetrofitClient.BASE_URL}")
        if (URLUtil.isValidUrl(RetrofitClient.BASE_URL)) {
            RetrofitClient.apiService()?.getUserTemplates()
                ?.enqueue(object : Callback<TemplatesResponse?> {
                    override fun onResponse(
                        call: Call<TemplatesResponse?>, response: Response<TemplatesResponse?>
                    ) {
                        if (response.isSuccessful) {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "downloadTemplate success :${response.raw()}"
                            )
                            callback?.onSuccess(response.body())
                        } else {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "downloadTemplate onFailed called ${response.code()} ${response.raw()} error: ${response.body()} "
                            )
                            try {
                                val errorObject = JSONObject(response.errorBody()!!.string())
                                val errorMessage = errorObject.getString("error")
                                Log.d(
                                    PluginDropDownReceiver.TAG,
                                    "downloadTemplate onFailed called error: $errorMessage"
                                )
                                callback?.onFailed(errorMessage, response.code())
                            } catch (e: java.lang.Exception) {
                                e.printStackTrace()
                                callback?.onFailed(response.message(), response.code())
                            }
                        }
                    }

                    override fun onFailure(call: Call<TemplatesResponse?>, t: Throwable) {
                        try {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "downloadTemplate override fun onFailure called Request ${call.request()}  \n Error: ${t.localizedMessage}"
                            )
                            callback?.onFailed(t.message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            callback?.onFailed(e.printStackTrace().toString())
                        }
                    }
                })
        } else {
            callback?.onFailed("", Constant.ApiErrorCodes.sForbidden)
        }

    }

    fun downloadTemplateDetail(id:Int, callback: ApiCallBacks? = null) {
        callback?.onLoading()
//        Log.d("PluginDropDownReceiver", "intercept: $url BASE_URL: ${RetrofitClient.BASE_URL}")
        if (URLUtil.isValidUrl(RetrofitClient.BASE_URL)) {
            RetrofitClient.apiService()?.getTemplateDetail(id)
                ?.enqueue(object : Callback<TemplateDataModel?> {
                    override fun onResponse(
                        call: Call<TemplateDataModel?>, response: Response<TemplateDataModel?>
                    ) {
                        if (response.isSuccessful) {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "downloadTemplateDetail success :${response.raw()}"
                            )
                            callback?.onSuccess(response.body())
                        } else {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "downloadTemplateDetail onFailed called ${response.code()} ${response.raw()} error: ${response.body()} "
                            )
                            try {
                                val errorObject = JSONObject(response.errorBody()!!.string())
                                val errorMessage = errorObject.getString("error")
                                Log.d(
                                    PluginDropDownReceiver.TAG,
                                    "downloadTemplateDetail onFailed called error: $errorMessage"
                                )
                                callback?.onFailed(errorMessage, response.code())
                            } catch (e: java.lang.Exception) {
                                e.printStackTrace()
                                callback?.onFailed(response.message(), response.code())
                            }
                        }
                    }

                    override fun onFailure(call: Call<TemplateDataModel?>, t: Throwable) {
                        try {
                            Log.d(
                                PluginDropDownReceiver.TAG,
                                "downloadTemplateDetail override fun onFailure called Request ${call.request()}  \n Error: ${t.localizedMessage}"
                            )
                            callback?.onFailed(t.message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            callback?.onFailed(e.printStackTrace().toString())
                        }
                    }
                })
        } else {
            callback?.onFailed("", Constant.ApiErrorCodes.sForbidden)
        }

    }

    interface ApiCallBacks {
        fun onLoading()
        fun onSuccess(response: Any?)
        fun onFailed(error: String?, responseCode: Int? = null)
    }

}

