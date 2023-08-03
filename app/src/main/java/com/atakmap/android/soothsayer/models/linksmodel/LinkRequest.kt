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

//
//data class Environment(
//    val buildings: Int,
//    val clt: String,
//    val elevation: Int,
//    val landcover: Int,
//    val obstacles: Int
//)

//data class Model(
//    val cli: Int,
//    val ked: Int,
//    val pe: Int,
//    val pm: Int,
//    val rel: Int,
//    val ter: Int
//)

data class Point(
    val markerId: String,
    val alt: Int?,
    val lat: Double?,
    val lon: Double?
)

