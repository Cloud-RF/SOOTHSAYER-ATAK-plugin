package com.atakmap.android.soothsayer.models.linksmodel

import com.atakmap.android.soothsayer.models.request.*
import com.atakmap.android.soothsayer.models.request.Receiver
import com.atakmap.android.soothsayer.models.request.Transmitter

data class LinkRequest(
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

