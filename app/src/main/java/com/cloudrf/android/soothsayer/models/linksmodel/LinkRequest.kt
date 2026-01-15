package com.cloudrf.android.soothsayer.models.linksmodel

import com.cloudrf.android.soothsayer.models.request.*
import com.cloudrf.android.soothsayer.models.request.Receiver
import com.cloudrf.android.soothsayer.models.request.Transmitter

data class LinkRequest(
    val ui: Int = 5,
    val fast: Int = 1,
    val antenna: Antenna?,
    val environment: Environment?,
    val model: Model?,
    val network: String?,
    val output: Output?,
    val points: List<Point>,
    val `receiver`: Receiver?,
    val site: String?,
    val transmitter: Transmitter?
)

data class Point(
    val markerId: String,
    val alt: Double?,
    val lat: Double?,
    val lon: Double?
)

