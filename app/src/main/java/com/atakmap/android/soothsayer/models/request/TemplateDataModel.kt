package com.atakmap.android.soothsayer.models.request

import com.google.gson.annotations.SerializedName
import java.io.Serializable

// API reference https://cloudrf.com/documentation/developer

data class TemplateDataModel(
    val antenna: Antenna,
    val coordinates: Int,
    val engine: Int,
    val environment: Environment,
    val feeder: Feeder,
    val model: Model,
    val network: String,
    var output: Output,
    var `receiver`: Receiver,
    val reference: String,
    val site: String,
    val template: Template,
    var transmitter: Transmitter?,
    val version: String,
    @SerializedName("custom_icon")
    var bounds: Bounds?,
    val customIcon: String?=null,
): Serializable

data class Antenna(
    var ant: Int,
    var azi: String, // azimuth can be 0 or "0,90,180,270"
    val fbr: Double,
    val hbw: Int,
    val mode: String,
    val pol: String,
    val tlt: Int,
    val txg: Double,
    val txl: Double,
    val vbw: Int
): Serializable

data class Environment(
    val clt: String,
    val buildings: Int,
    val elevation: Int,
    val landcover: Int,
    val obstacles: Int
): Serializable

data class Feeder(
    val fcc: Int,
    val fll: Int,
    val flt: Int
): Serializable

data class Model(
    val ked: Int,
    val pe: Int,
    val pm: Int, // can be 1,2...or "auto"
    val rel: Int
): Serializable

data class Output(
        val col: String,
        val mod: Int,
        var nf: String, // noise floor
        val `out`: Int,
        var rad: Double,
        var res: Double,
        val units: String,
        var bounds: Bounds?
): Serializable

data class Bounds (
        @SerializedName("north") var north : Double,
        @SerializedName("east") var east : Double,
        @SerializedName("south") var south : Double,
        @SerializedName("west") var west : Double
): Serializable

data class Receiver(
    var alt: Double,
    var lat: Double,
    var lon: Double,
    var rxg: Double,
    var rxs: Int
): Serializable

data class Template(
    val bom_value: Int,
    val created_at: String,
    val name: String,
    val owner: Int,
    val service: String
): Serializable

data class Transmitter(
    var alt: Double, // height
    var bwi: Double, // bandwidth
    var frq: Double, // frequency
    var lat: Double,
    var lon: Double,
    val powerUnit: String,
    var txw: Double // power
): Serializable