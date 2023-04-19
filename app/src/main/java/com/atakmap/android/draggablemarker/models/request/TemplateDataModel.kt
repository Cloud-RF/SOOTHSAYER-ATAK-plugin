package com.atakmap.android.draggablemarker.models

data class TemplateDataModel(
    val antenna: Antenna,
    val coordinates: Int,
    val engine: Int,
    val environment: Environment,
    val feeder: Feeder,
    val model: Model,
    val network: String,
    val output: Output,
    val `receiver`: Receiver,
    val reference: String,
    val site: String,
    val template: Template,
    val transmitter: Transmitter?,
    val version: String
)

data class Antenna(
    val ant: Int,
    val azi: Int,
    val fbr: Double,
    val hbw: Int,
    val mode: String,
    val pol: String,
    val tlt: Int,
    val txg: Double,
    val txl: Int,
    val vbw: Int
)

data class Environment(
    val cll: Int,
    val clm: Int,
    val clt: String
)

data class Feeder(
    val fcc: Int,
    val fll: Int,
    val flt: Int
)

data class Model(
    val ked: Int,
    val pe: Int,
    val pm: Int,
    val rel: Int
)

data class Output(
    val ber: Int,
    val col: String,
    val mod: Int,
    val nf: Int,
    val `out`: Int,
    val rad: Int,
    val res: Int,
    val units: String
)

data class Receiver(
    val alt: Int,
    var lat: Double,
    var lon: Double,
    val rxg: Int,
    val rxs: Int
)

data class Template(
    val bom_value: Int,
    val created_at: String,
    val name: String,
    val owner: Int,
    val service: String
)

data class Transmitter(
    val alt: Int,
    val bwi: Double,
    val frq: Int,
    var lat: Double,
    var lon: Double,
    val powerUnit: String,
    val txw: Int
)