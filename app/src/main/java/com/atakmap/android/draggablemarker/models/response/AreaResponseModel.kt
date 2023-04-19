package com.atakmap.android.draggablemarker.models.response

data class AreaResponseModel(
    val PNG_Mercator: String,
    val PNG_WGS84: String,
    val area: Int,
    val balance: Int,
    val bounds: List<Int>,
    val calculation_adjusted: List<String>,
    val coverage: String,
    val elapsed: Int,
    val id: String,
    val key: List<Key>,
    val kmz: String,
    val sid: String
)

data class Key(
    val b: Int,
    val g: Int,
    val l: String,
    val r: Int
)