package com.atakmap.android.soothsayer.models.request

import java.io.Serializable

data class TemplateDataModel(
    val antenna: Antenna,
    val coordinates: Int,
    val engine: Int,
    val environment: Environment,
    val feeder: Feeder,
    val model: Model,
    val network: String,
    val output: Output,
    var `receiver`: Receiver,
    val reference: String,
    val site: String,
    val template: Template,
    var transmitter: Transmitter?,
    val version: String
): Serializable

data class Antenna(
    var ant: Int,
    var azi: Int, // azimuth
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
    val cll: Int,
    val clm: Int,
    val clt: String,
    val buildings: Int?,
    val elevation: Int?,
    val landcover: Int?,
    val obstacles: Int?
): Serializable

data class Feeder(
    val fcc: Int,
    val fll: Int,
    val flt: Int
): Serializable

data class Model(
    val ked: Int,
    val pe: Int,
    val pm: Int,
    val rel: Int,
    val cli: Int?,
    val ter: Int?
): Serializable

data class Output(
    val ber: Int,
    val col: String,
    val mod: Int,
    var nf: Int, // noise floor
    val `out`: Int,
    val rad: Double,
    val res: Double,
    val units: String
): Serializable

data class Receiver(
    var alt: Int,
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
    var alt: Int, // height
    var bwi: Double, // bandwidth
    var frq: Double, // frequency
    var lat: Double,
    var lon: Double,
    val powerUnit: String,
    var txw: Double // power
): Serializable