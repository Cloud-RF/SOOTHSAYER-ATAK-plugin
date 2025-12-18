package com.cloudrf.android.soothsayer.models.common

import com.cloudrf.android.soothsayer.models.request.TemplateDataModel
import java.io.Serializable

data class MarkerDataModel(
    val markerID: String,
    var markerDetails: TemplateDataModel,
    var coopted_uid: String? = null
):Serializable