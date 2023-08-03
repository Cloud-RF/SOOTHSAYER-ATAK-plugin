package com.atakmap.android.soothsayer.util

import com.atakmap.android.soothsayer.network.remote.RetrofitClient.DEFAULT_URL

object Constant {
    var sServerUrl = DEFAULT_URL
    var sAccessToken = ""
    const val TEMPLATE_FORMAT = ".json"

    object ApiErrorCodes{
        const val sUnAuthorized = 401
        const val sBadRequest = 400
        const val sForbidden= 403
        const val sNotFound= 404
        const val sInternalServerError = 500
    }

    object PreferenceKey{
        const val sServerUrl = "Server url"
        const val sApiKey = "Api key"
        const val sMarkerList = "Marker list"
        const val sCalculationMode = "Calculation Mode"
        const val sKmzVisibility = "KMZ Visibility"
        const val sLinkLinesVisibility = "Link Lines Visibility"
    }
}