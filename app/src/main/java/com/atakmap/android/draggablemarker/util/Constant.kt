package com.atakmap.android.draggablemarker.util

object Constant {
    var sServerUrl = "https://api.cloudrf.com"
    var sAccessToken = "49166-ea0f41501071b6309e896d98099d69811b5ba10e"
    const val TEMPLATE_FORMAT = ".json"

    object ApiErrorCodes{
        const val sUnAuthorized = 401
        const val sBadRequest = 400
        const val sForbidden= 403
        const val sNotFound= 404
        const val sInternalServerError = 500
    }
}