package com.cloudrf.android.soothsayer.util

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import com.atakmap.android.drawing.mapItems.DrawingShape
import com.atakmap.android.maps.MapView
import com.atakmap.android.maps.PointMapItem
import com.atakmap.android.preference.AtakPreferences
import com.cloudrf.android.soothsayer.CustomPolygonTool
import com.cloudrf.android.soothsayer.GeoImageMasker
import com.cloudrf.android.soothsayer.PluginDropDownReceiver
import com.cloudrf.android.soothsayer.PluginDropDownReceiver.Companion.TAG
import com.cloudrf.android.soothsayer.models.common.CoOptedMarkerSettings
import com.cloudrf.android.soothsayer.models.common.MarkerDataModel
import com.cloudrf.android.soothsayer.models.request.Bounds
import com.cloudrf.android.soothsayer.models.request.MultiSiteTransmitter
import com.cloudrf.android.soothsayer.models.request.MultisiteRequest
import com.cloudrf.android.soothsayer.plugin.R
import com.atakmap.coremap.maps.coords.GeoPoint
import com.atakmap.map.elevation.ElevationData
import com.atakmap.map.elevation.ElevationManager
import kotlin.collections.component2
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

class CalculationManager(
    private val pluginContext: Context,
    private val sharedPrefs: AtakPreferences?,
    private val mapView: MapView?,
    private val markersList: List<MarkerDataModel>,
    private val pluginDropDownReceiver: PluginDropDownReceiver
) {
    private val lastKnownLocations = HashMap<String, GeoPoint>()

    fun setLastKnownLocation(uid: String, latitude: Double, longitude: Double) {
        lastKnownLocations[uid] = GeoPoint(latitude, longitude)
        Log.d(TAG, "Tracking updated for $uid at ($latitude, $longitude)")
    }

    // A 1000x1000px image is 1MP and can be considered average for a 2024 phone
    // You can load a 16MP image on a phone but this may crash an older model with an OOM error
    // In the early days of CloudRF's Android app (2012), the resolution was limited by device mem at 0.09MP (300x300px)
    private var megapixels = 1.0

    fun setMegaPixel(value: Double){
        megapixels = value
    }

    fun calculate(item: MarkerDataModel?) {
        val config = readConfig()
        if (!validatePreconditions(config)) return

        configureMarker(item, config)

        if (config.showLinks && markersList.size > 1) {
            pluginDropDownReceiver.updateLinkLines(item)
        } else {
            mapView?.removeLinkLinesFromMap(pluginContext, item)
        }

        applyPolygonBounds(item)

        // A Multisite API call simulates many towers at once and uses a GPU
        if (config.multisiteMode) {
            doMultisiteCall(item, config.showCoverage)
        } else {
            doSingleSiteCall(item, config.showCoverage)
        }
    }

    private data class CalcConfig(
        val showLinks: Boolean,
        val showCoverage: Boolean,
        val multisiteMode: Boolean
    )

    private fun readConfig(): CalcConfig = CalcConfig(
        showLinks = sharedPrefs?.get(Constant.PreferenceKey.sLinkLinesVisibility, true) ?: true,
        showCoverage = sharedPrefs?.get(Constant.PreferenceKey.sKmzVisibility, true) ?: true,
        multisiteMode = sharedPrefs?.get(Constant.PreferenceKey.sCalculationMode, true) ?: true
    )

    private fun validatePreconditions(cfg: CalcConfig): Boolean {
        if (!cfg.showLinks && !cfg.showCoverage) {
            pluginContext.toast("You need links or coverage layers enabled")
            return false
        }
        if (cfg.showLinks && !cfg.showCoverage && markersList.size == 1) {
            pluginContext.toast("You should enable the coverage layer")
            return false
        }
        if (markersList.isEmpty()) {
            pluginContext.toast("You need to add a radio first")
            return false
        }
        return true
    }

    private fun configureMarker(item: MarkerDataModel?, cfg: CalcConfig) {
        val radius = item?.markerDetails!!.output.rad
        item.markerDetails.output.res = megapixelCalculator(radius, megapixels)
        // Force engine. 1=GPU, 2=CPU
        item.markerDetails.engine = if (cfg.multisiteMode) 1 else 2
    }

    private fun applyPolygonBounds(item: MarkerDataModel?) {
        val polygon = CustomPolygonTool.getMaskingPolygon()
        if (polygon != null) {
            val area = polygon.area
            item?.markerDetails?.output?.res = min(10.0, ceil(sqrt(area) / 1000.0) + 1.0)// adjust resolution based upon polygon size
            adjustRadiusForPolygon(item, polygon)
            item?.markerDetails?.output?.bounds = polygon.toBounds()
        } else {
            item?.markerDetails?.output?.bounds = null
        }
    }

    private fun adjustRadiusForPolygon(item: MarkerDataModel?, polygon: DrawingShape) {
        val txlat = item?.markerDetails?.transmitter?.lat
        val txlon = item?.markerDetails?.transmitter?.lon
        if (txlat != null && txlon != null) {
            val bounds = GeoImageMasker.getBounds(polygon.points)
            val polyLon = (bounds.east + bounds.west) / 2
            val polyLat = (bounds.north + bounds.south) / 2
            val haversineDistance = Pair(txlat, txlon).haversine(polyLat, polyLon)
            if (haversineDistance > item.markerDetails.output.rad) {
                item.markerDetails.output.rad = haversineDistance * 1.1
            }
        }
    }

    private fun DrawingShape.toBounds(): Bounds {
        val b = GeoImageMasker.getBounds(points)
        return Bounds(b.north, b.east, b.south, b.west)
    }

    private fun doMultisiteCall(item: MarkerDataModel?, showCoverage: Boolean) {
        item?.markerDetails?.let { template ->
            // in a multisite call, each transmitter can have its own location, frequency, altitude and antenna
            val txlist = markersList.mapNotNull { marker ->
                marker.markerDetails.transmitter?.run {
                    MultiSiteTransmitter(
                        alt, bwi, frq, lat, lon, powerUnit, txw,
                        marker.markerDetails.antenna, false
                    )
                }
            }
            val request = MultisiteRequest(
                template.site, template.network, txlist,
                template.receiver, template.model,
                template.environment, template.output
            )
            if (showCoverage) {
                pluginDropDownReceiver.showHidePlayBtn()
                pluginDropDownReceiver.sendMultiSiteDataToServer(request)
            }
        }
    }

    private fun doSingleSiteCall(item: MarkerDataModel?, showCoverage: Boolean) {
        if (item != null && showCoverage) {
            pluginDropDownReceiver.showHidePlayBtn()
            pluginDropDownReceiver.sendSingleSiteDataToServer(item.markerDetails)
        }
    }

    fun checkDistanceAndRecalculate(coOptedMarkers: HashMap<String, CoOptedMarkerSettings>,
                                  templateView: View, updateAdapter:(Int) ->Unit) {
        val refreshDistance = sharedPrefs?.get(Constant.PreferenceKey.sCoOptDistanceRefreshThreshold, 100.0) ?: 100.0
        var needsRecalculation = false
        var lastUpdatedMarker: MarkerDataModel? = null

        for ((uid, settings) in coOptedMarkers) {
            val currentMarker = mapView?.rootGroup?.deepFindItem("uid", uid) as? PointMapItem ?: continue
            val lastLocation = lastKnownLocations[uid]

            if (lastLocation == null) {
                // This should not happen if we initialized properly, but just in case
                lastKnownLocations[uid] = GeoPoint(currentMarker.point.latitude, currentMarker.point.longitude)
                Log.d(TAG, "Late initialization of tracking for marker $uid")
                continue
            }

            val distanceMoved = lastLocation.distanceTo(currentMarker.point)
            if (distanceMoved >= refreshDistance) {
                Log.d(TAG, "Marker $uid moved ${distanceMoved}m (threshold: ${refreshDistance}m)")
                val markerInList = markersList.find { it.coopted_uid == uid } ?: continue
                updateMarkerLocationAndAltitude(markerInList, currentMarker)

                // NOTE: If an aircraft causes a switch to AMSL, all other markers will be AMSL
                // Users can override altitude by clicking the marker to open the edit form.
                val index = markersList.indexOf(markerInList)
                if (index != -1){
                    updateAdapter(index)
                }

                needsRecalculation = true
                lastUpdatedMarker = markerInList
                Log.d(TAG, "Marker $uid triggered distance recalculation (moved ${distanceMoved}m)")
                lastKnownLocations[uid] = GeoPoint(currentMarker.point.latitude, currentMarker.point.longitude)
            }
        }

        if (needsRecalculation && templateView.findViewById<ProgressBar>(R.id.progressBar).visibility == View.GONE) {
            calculate(lastUpdatedMarker) // you already have your calculate() here
        }
    }

    private fun updateMarkerLocationAndAltitude(marker: MarkerDataModel, currentMarker: PointMapItem) {
        marker.markerDetails.transmitter?.lat = Math.round(currentMarker.point.latitude * 1e5).toDouble() / 1e5
        marker.markerDetails.transmitter?.lon = Math.round(currentMarker.point.longitude * 1e5).toDouble() / 1e5

        val altitude = Math.round(currentMarker.point.altitude)
        val dtmFilter = ElevationManager.QueryParameters().apply {
            elevationModel = ElevationData.MODEL_TERRAIN
        }
        val terrain = ElevationManager.getElevation(currentMarker.point.latitude, currentMarker.point.longitude, dtmFilter)

        // If Height AGL is > 120m / 400ft, this is probably flying so we switch units to meters AMSL and use GPS altitude
        if (altitude - terrain > 120.0) {
            marker.markerDetails.transmitter?.alt = altitude.toDouble()
            marker.markerDetails.receiver.alt = terrain + 1
            marker.markerDetails.output.units = "m_amsl"
        } else {
            marker.markerDetails.output.units = "m"
        }
    }

    // used to adjust radius for polygons far away
    private fun Pair<Double, Double>.haversine(toLat: Double, toLon: Double): Double {
        val (fromLat, fromLon) = this
        val dLat = Math.toRadians(toLat - fromLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val originLat = Math.toRadians(fromLat)
        val destinationLat = Math.toRadians(toLat)

        val a = Math.pow(Math.sin(dLat / 2), 2.0) + Math.pow(Math.sin(dLon / 2), 2.0) * Math.cos(originLat) * Math.cos(destinationLat)
        val c = 2 * Math.asin(sqrt(a))
        return 6372.8 * c
    }
}
