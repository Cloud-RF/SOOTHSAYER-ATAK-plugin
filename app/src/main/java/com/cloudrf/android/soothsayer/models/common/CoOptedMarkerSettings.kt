package com.cloudrf.android.soothsayer.models.common

import com.cloudrf.android.soothsayer.models.request.TemplateDataModel

/**
 * This comment describes the purpose of the CoOptedMarkerSettings data class.
 * This class holds the configuration for a marker that has been "co-opted".
 * It stores the selected radio template and the specific refresh conditions.
 *
 * @param uid The unique ID of the co-opted marker.
 * @param template The radio template assigned to this marker.
 * @param refreshDistanceMeters The distance in meters to trigger a refresh, if enabled.
 * @param refreshIntervalSeconds The time in seconds to trigger a refresh, if enabled.
 */
data class CoOptedMarkerSettings(
    val uid: String,
    val template: TemplateDataModel,
    val refreshDistanceMeters: Double?,
    val refreshIntervalSeconds: Long?
) 