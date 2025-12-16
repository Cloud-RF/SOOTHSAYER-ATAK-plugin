package com.atakmap.android.soothsayer.models.linksmodel

import com.google.gson.annotations.SerializedName

data class LinkResponse(
    @SerializedName("Atmospheric bending constant")
    val atmosphericBendingConstant: Int,
    @SerializedName("Earth conductivity")
    val earthConductivity: Double,
    @SerializedName("Earth dielectric constant")
    val earthDielectricConstant: Int,
    @SerializedName("Engine")
    val engine: String,
    @SerializedName("Fraction of situations")
    val fractionOfSituations: Int,
    @SerializedName("Fraction of time")
    val fractionOfTime: Int,
    @SerializedName("Frequency MHz")
    val frequencyMHz: Double,
    @SerializedName("Propagation model")
    val propagationModel: String,
    @SerializedName("Radio climate")
    val radioClimate: String,
    @SerializedName("Receiver")
    val receiver: List<Receiver>,
    @SerializedName("Transmitters")
    val transmitters: List<Transmitter>,
    @SerializedName("calculation_adjusted")
    val calculationAdjusted: List<Any>,
    val elapsed: Double,
    val json: String,
    val kmz: String
)

data class Receiver(
    @SerializedName("Antenna height m")
    val antennaHeight: Double,
    @SerializedName("Ground elevation m")
    val groundElevation: Int,
    @SerializedName("Latitude")
    val latitude: Double,
    @SerializedName("Longitude")
    val longitude: Double,
    @SerializedName("Receiver gain dBd")
    val receiverGainDBd: Double,
    @SerializedName("Receiver gain dBi")
    val receiverGainDBi: Double
)

data class Transmitter(
    @SerializedName("Antenna gain dBd")
    val antennaGainDBd: Double,
    @SerializedName("Antenna gain dBi")
    val antennaGainDBi: Double,
    @SerializedName("Antenna height m")
    val antennaHeight: Double,
    @SerializedName("Azimuth to receiver deg")
    val azimuthToReceiverDeg: Double,
    @SerializedName("Bandwidth MHz")
    val bandwidthMHz: Double,
    @SerializedName("Channel noise dBm")
    val channelNoiseDBm: Double,
    @SerializedName("Computed path loss dB")
    val computedPathLossDB: Double,
    @SerializedName("Distance")
    val distance: List<Double>,
    @SerializedName("Distance to receiver km")
    val distanceToReceiverKm: Double,
    @SerializedName("Downtilt angle deg")
    val downtiltAngleDeg: Double,
    @SerializedName("EIRP W")
    val eirpW: Double,
    @SerializedName("EIRP dBm")
    val eirpDBm: Double,
    @SerializedName("ERP W")
    val eRPW: Double,
    @SerializedName("ERP dBm")
    val eRPDBm: Double,
    @SerializedName("Field strength at receiver dBuV/m")
    val fieldStrengthAtReceiverDBuV: Double,
    @SerializedName("Free space path loss dB")
    val freeSpacePathLossDB: Double,
    @SerializedName("Fresnel")
    val fresnel: List<Double>,
    @SerializedName("Ground elevation m")
    val groundElevation: Int,
    @SerializedName("Johnson Nyquist noise dB")
    val johnsonNyquistNoiseDB: Double,
    @SerializedName("Landcover codes")
    val landcoverCodes: List<Int>,
    @SerializedName("Landcover distance")
    val landcoverDistance: List<Double>,
    @SerializedName("Landcover heights")
    val landcoverHeights: List<Int>,
    @SerializedName("Latitude")
    val latitude: Double,
    @SerializedName("Longitude")
    val longitude: Double,
    @SerializedName("Model attenuation dB")
    val modelAttenuationDB: Double,
    @SerializedName("Noise floor dBm")
    val noiseFloorDBm: Int,
    @SerializedName("Obstructions")
    val obstructions: List<Any>,
    @SerializedName("Polarisation")
    val polarisation: String,
    @SerializedName("Power W")
    val powerW: Double,
    @SerializedName("Power dBm")
    val powerDBm: Double,
    @SerializedName("RX voltage 50 ohm dipole dBuV")
    val rXVoltage50OhmDipoleDBuV: Int,
    @SerializedName("RX voltage 50 ohm dipole uV")
    val rXVoltage50OhmDipoleUV: Int,
    @SerializedName("RX voltage 75 ohm dipole dBuV")
    val rXVoltage75OhmDipoleDBuV: Int,
    @SerializedName("RX voltage 75 ohm dipole uV")
    val rXVoltage75OhmDipoleUV: Int,
    @SerializedName("Raise RX antenna for LOS")
    val raiseRxAntennaForLOS: Double,
    @SerializedName("Raise RX antenna for fresnel 60%")
    val raiseRxAntennaForFresnel60 : Double,
    @SerializedName("Raise RX antenna for full fresnel")
    val raiseRxAntennaForFullFresnel: Double,
    @SerializedName("Signal power at receiver dBm")
    val signalPowerAtReceiverDBm: Double,
    @SerializedName("Signal to Noise Ratio dB")
    val signalToNoiseRatioDB: Double,
    @SerializedName("Terrain")
    val terrain: List<Int>,
    @SerializedName("Terrain_AMSL")
    val terrainAMSL: List<Int>,
    val dB: List<Int>,
    val dBm: List<Int>,
    var markerId: String?
)