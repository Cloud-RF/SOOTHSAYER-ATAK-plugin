package com.cloudrf.android.soothsayer.models.response

import com.google.gson.annotations.SerializedName

data class BestSiteResponse(
    @SerializedName("PNG_Mercator")
    val pngMercator: String,
    @SerializedName("PNG_WGS84")
    val pngWGS84: String,
    val area: Double,
    val balance: Int,
    val bounds: List<Double>,
    val coverage: Double,
    val elapsed: Double,
    val html: String,
    val id: Int,
    val json: String,
    val key: List<Key>,
    val kmz: String,
    val shp: String,
    val sid: String,
    val tiff: String,
    @SerializedName("tiff_3857")
    val tiff3857: String,
    @SerializedName("tiff_4326")
    val tiff4326: String,
    val url: String,
    val zip: String
)