package com.atakmap.android.soothsayer.network.repository

import android.webkit.URLUtil
import com.atakmap.android.soothsayer.PluginDropDownReceiver
import com.atakmap.android.soothsayer.models.request.MultisiteRequest
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.models.response.ResponseModel
import com.atakmap.android.soothsayer.network.remote.RetrofitClient
import com.atakmap.android.soothsayer.util.Constant
import com.atakmap.coremap.log.Log
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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
            RetrofitClient.apiService?.sendSingleSiteDataToServer(request = request)
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
                                "sendMarkerData onFailed called ${response.code()} ${response.raw()}"
                            )
                            callback?.onFailed(response.message(), response.code())
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
            RetrofitClient.apiService?.sendMultiSiteDataToServer(request = request)
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
                            callback?.onFailed(response.message(), response.code())
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

    interface ApiCallBacks {
        fun onLoading()
        fun onSuccess(response: Any?)
        fun onFailed(error: String?, responseCode: Int? = null)
    }

}