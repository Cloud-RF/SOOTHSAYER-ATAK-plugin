package com.atakmap.android.draggablemarker.network.repository

import android.webkit.URLUtil
import com.atakmap.android.draggablemarker.PluginDropDownReceiver
import com.atakmap.android.draggablemarker.models.request.TemplateDataModel
import com.atakmap.android.draggablemarker.network.remote.RetrofitClient
import com.atakmap.android.draggablemarker.util.Constant
import com.atakmap.coremap.log.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PluginRepository {

    companion object {
        private var INSTANCE: PluginRepository? = null

        fun getInstance() = INSTANCE ?: synchronized(PluginRepository::class.java) {
            INSTANCE ?: PluginRepository().also { INSTANCE = it }
        }
    }


    fun sendMarkerData(request: TemplateDataModel, callback: ApiCallBacks? = null) {
        callback?.onLoading()
        if (URLUtil.isValidUrl(RetrofitClient.BASE_URL)) {
            RetrofitClient.apiService.sendMarkerDataToServer(request = request)
                .enqueue(object : Callback<Any> {
                    override fun onResponse(
                        call: Call<Any>, response: Response<Any>
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

                    override fun onFailure(call: Call<Any>, t: Throwable) {
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