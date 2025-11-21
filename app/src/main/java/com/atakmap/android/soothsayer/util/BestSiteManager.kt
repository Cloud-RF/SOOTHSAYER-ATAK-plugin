package com.atakmap.android.soothsayer.util

import android.content.Context
import android.location.Location
import android.util.Log
import com.atakmap.android.drawing.mapItems.DrawingShape
import com.atakmap.android.soothsayer.CustomPolygonTool
import com.atakmap.android.soothsayer.PluginDropDownReceiver
import com.atakmap.android.soothsayer.models.request.Antenna
import com.atakmap.android.soothsayer.models.request.BestSiteRequestModel
import com.atakmap.android.soothsayer.models.request.Model
import com.atakmap.android.soothsayer.models.request.Output
import com.atakmap.android.soothsayer.models.request.Receiver
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.models.request.Transmitter
import com.atakmap.android.soothsayer.models.response.BestSiteResponse
import com.atakmap.android.soothsayer.network.repository.PluginRepository
import com.atakmap.android.soothsayer.plugin.R
import com.google.gson.Gson

class BestSiteManager(
    private val pluginContext: Context,
    private val repository: PluginRepository,
    private val pluginDropDownReceiver: PluginDropDownReceiver
) {

    fun performBestSiteAnalysis(selectedMarkerType: TemplateDataModel?){
        val polygon = CustomPolygonTool.getMaskingPolygon()
        if(polygon == null){
            pluginContext.shortToast("You need to add the polygon first.")
            return
        }
        pluginDropDownReceiver.showHidePlayBtn()
        val request = getBestSiteRequest(selectedMarkerType, polygon)
        repository.performBestSiteAnalysis(request, object : PluginRepository.ApiCallBacks {
            override fun onLoading() {
                pluginContext.shortToast("Downloading grey scale image.......")
            }

            override fun onSuccess(response: Any?) {
                pluginDropDownReceiver.showHidePlayBtn()
                if (response is BestSiteResponse) {
                    val greyScaleImage = response.pngWGS84
                    // Fetch the PNG image from the JSON response and create a layer using the bounds metadata
                    repository.downloadFile(greyScaleImage,
                        FOLDER_PATH,
                        PNG_IMAGE.getFileName(),
                        listener = { isDownloaded, filePath ->
                            Log.d("PluginDropDownReceiver", "isDownloaded: $isDownloaded filePath: $filePath")
                            if (isDownloaded) {
                                pluginDropDownReceiver.addLayer(filePath, response.bounds, true)
                            }
                        })
                }
            }

            override fun onFailed(error: String?, responseCode: Int?) {
                pluginDropDownReceiver.showHidePlayBtn()
                pluginContext.toast(
                    error ?: pluginContext.getString(R.string.error_msg)
                )
            }

        })
    }

    private fun getBestSiteRequest(template: TemplateDataModel?, polygon: DrawingShape): BestSiteRequestModel{
        val antenna = template?.antenna?.let { it ->
            Antenna(it.ant, "0", 2.0, 1, null, "v", 0, it.txg, 0.0, 1)
        }
        val engine = "1"
        val environment =  com.atakmap.android.soothsayer.models.request.Environment("Minimal.clt", 1, 2, 1, 0)
        val model=  Model(0, 2, 7, 50)
        val network =  "BSA"
        val `receiver` = template?.receiver?.let { it ->
            Receiver(it.alt, 0.0, 0.0, it.rxg, it.rxs)
        }
        val site = "Site"
        val polyLon =   polygon.center.get().longitude
        val polyLat =   polygon.center.get().latitude

        val transmitter = template?.transmitter?.let { it ->
            Transmitter(it.alt,  it.bwi,it.frq,polyLat,polyLon, it.powerUnit, it.txw )
        }
        val radius = polygon.calculateRadius(polyLat, polyLon)
        val output = template?.output?.let { it ->
            Output("BESTSITE.bsa", 7, "-120", 7, radius , it.res, "m", null)
        }
        val ui = 3

        val request = BestSiteRequestModel(antenna, engine, environment, model, network, output, receiver, site, transmitter, ui )
        Log.d("PluginDropDownReceiver", "getBestSiteRequest: ${Gson().toJson(request)} ")
        return request
    }

    private fun DrawingShape.calculateRadius(centerLat: Double, centerLon: Double): Double{
        var maxDist = 0f
        val temp = FloatArray(1)

        for (p in points) {
            Location.distanceBetween(
                centerLat, centerLon,
                p.latitude, p.longitude,
                temp
            )
            if (temp[0] > maxDist) maxDist = temp[0]
        }

        return maxDist.toDouble()   // meters
    }

}