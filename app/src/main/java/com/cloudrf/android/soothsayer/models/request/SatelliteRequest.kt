package com.cloudrf.android.soothsayer.models.request

import java.io.Serializable

data class SatelliteRequest (
    val receiver: Receiver,
    val satellite: SatelliteModel,
    val output: Output
) : Serializable