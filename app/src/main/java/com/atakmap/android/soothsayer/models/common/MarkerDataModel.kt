package com.atakmap.android.soothsayer.models.common

import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import java.io.Serializable

data class MarkerDataModel(
    val markerID: String,
    var markerDetails: TemplateDataModel,
    var coopted_uid: String? = null
):Serializable