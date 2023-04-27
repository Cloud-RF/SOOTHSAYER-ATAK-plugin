package com.atakmap.android.draggablemarker.models.request

import java.io.Serializable

data class MultisiteRequest(
    val site: String,
    val network: String,
    val transmitters: List<MultiSiteTransmitter?>,
    val `receiver`: Receiver,
    val model: Model,
    val environment: Environment,
    val output: Output,
): Serializable

data class MultiSiteTransmitter(
    val alt: Int,
    val bwi: Double,
    val frq: Int,
    var lat: Double,
    var lon: Double,
    val powerUnit: String,
    val txw: Int,
    val antenna: Antenna
): Serializable