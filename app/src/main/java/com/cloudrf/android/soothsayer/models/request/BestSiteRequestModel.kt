package com.cloudrf.android.soothsayer.models.request

import java.io.Serializable

data class BestSiteRequestModel(
    val antenna: Antenna?,
    val engine: String,
    val environment: Environment,
    val model: Model,
    val network: String,
    val output: Output?,
    val `receiver`: Receiver?,
    val site: String,
    val transmitter: Transmitter?,
    val ui: Int
): Serializable