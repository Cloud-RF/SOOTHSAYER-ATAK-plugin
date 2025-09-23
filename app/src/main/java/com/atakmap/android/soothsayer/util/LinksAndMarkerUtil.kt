package com.atakmap.android.soothsayer.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import android.view.View
import com.atakmap.android.contact.Contact
import com.atakmap.android.drawing.mapItems.DrawingShape
import com.atakmap.android.dropdown.DropDownReceiver.FULL_HEIGHT
import com.atakmap.android.dropdown.DropDownReceiver.FULL_WIDTH
import com.atakmap.android.dropdown.DropDownReceiver.HALF_HEIGHT
import com.atakmap.android.dropdown.DropDownReceiver.HALF_WIDTH
import com.atakmap.android.grg.GRGMapComponent
import com.atakmap.android.importexport.ImportExportMapComponent
import com.atakmap.android.importexport.ImportReceiver
import com.atakmap.android.ipc.AtakBroadcast
import com.atakmap.android.maps.MapEvent
import com.atakmap.android.maps.MapGroup
import com.atakmap.android.maps.MapItem
import com.atakmap.android.maps.MapView
import com.atakmap.android.maps.Marker
import com.atakmap.android.maps.MultiPolyline
import com.atakmap.android.maps.PointMapItem
import com.atakmap.android.menu.PluginMenuParser
import com.atakmap.android.preference.AtakPreferences
import com.atakmap.android.soothsayer.MutableTuple
import com.atakmap.android.soothsayer.PluginDropDownReceiver
import com.atakmap.android.soothsayer.PluginDropDownReceiver.Companion.TAG
import com.atakmap.android.soothsayer.layers.CloudRFLayer
import com.atakmap.android.soothsayer.layers.PluginMapOverlay
import com.atakmap.android.soothsayer.models.common.CoOptedMarkerSettings
import com.atakmap.android.soothsayer.models.common.MarkerDataModel
import com.atakmap.android.soothsayer.models.linksmodel.Link
import com.atakmap.android.soothsayer.models.linksmodel.LinkDataModel
import com.atakmap.android.soothsayer.models.linksmodel.LinkRequest
import com.atakmap.android.soothsayer.models.linksmodel.LinkResponse
import com.atakmap.android.soothsayer.models.linksmodel.Point
import com.atakmap.android.soothsayer.models.request.Receiver
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.models.request.Transmitter
import com.atakmap.android.soothsayer.plugin.R
import com.atakmap.android.soothsayer.recyclerview.CoOptAdapter
import com.atakmap.coremap.maps.assets.Icon
import com.atakmap.coremap.maps.coords.GeoPoint
import com.atakmap.map.elevation.ElevationData
import com.atakmap.map.elevation.ElevationManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.UUID


fun MapView.drawLine(
    context: Context,
    linkToId: String?,
    links: ArrayList<Link>,
    startPoint: GeoPoint,
    endPoint: GeoPoint,
    lineColor: Int,
    snr: Int,
    linkUnits: String,
    mapGroup: MapGroup?,
    markerLinkList: ArrayList<LinkDataModel>,
    handleVisibility: () -> Unit
) {

    val mapView = this
    var lineGroup = mapGroup
    if (lineGroup == null) {
        lineGroup = mapView.rootGroup.findMapGroup(context.getString(R.string.drawing_objects))
    }
    val dslist: MutableList<DrawingShape> = ArrayList()
    val dsUid = UUID.randomUUID().toString()
    val ds = DrawingShape(mapView, dsUid)

    ds.strokeColor = lineColor
    ds.points = arrayOf(startPoint, endPoint)
    ds.hideLabels(false)
    ds.lineLabel = "${snr} ${linkUnits}" // is either dB or dBm
    ds.remarks = "SOOTHSAYER" // used for id for removal later
    dslist.add(ds)

    val lineUid = UUID.randomUUID().toString()
    val mp = MultiPolyline(mapView, lineGroup, dslist, lineUid)

    lineGroup?.addItem(mp)
    mp.movable = true
    mp.title = "${snr} ${linkUnits}"
    mp.lineLabel = "${snr} ${linkUnits}"
    mp.hideLabels(false)
    mp.toggleMetaData("labels_on", true)
    links.add(Link(lineUid, startPoint, endPoint))
    for (item in markerLinkList) {
        if (item.markerId == linkToId) {
            item.links.add(Link(lineUid, endPoint, startPoint))
        }
    }
    handleVisibility()
}

fun MapView.runCoOptUpdate(markersList: ArrayList<MarkerDataModel> ,
                                   sharedPrefs: AtakPreferences?,
                                   coOptedMarkers: HashMap<String, CoOptedMarkerSettings>,
                                   updateAdapter:(Int)->Unit,
                                   calculate:(MarkerDataModel) -> Unit
                           ) {
    Constant.sAccessToken = sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "") ?: ""
    var lastUpdatedMarker: MarkerDataModel? = null

    for ((uid, _) in coOptedMarkers) {
        val markerInList = markersList.find { it.coopted_uid == uid }
        val currentMarker = this.rootGroup.deepFindItem("uid", uid) as? PointMapItem

        if (markerInList != null && currentMarker != null) {
            markerInList.markerDetails.transmitter?.lat = Math.round(currentMarker.point.latitude * 1e5).toDouble() / 1e5
            markerInList.markerDetails.transmitter?.lon = Math.round(currentMarker.point.longitude * 1e5).toDouble() / 1e5

            val altitude = Math.round(currentMarker.point.altitude)
            val DTM_FILTER = ElevationManager.QueryParameters()
            DTM_FILTER.elevationModel = ElevationData.MODEL_TERRAIN
            val terrain = ElevationManager.getElevation(currentMarker.point.latitude,currentMarker.point.longitude,DTM_FILTER)

            Log.d(TAG, "runCoOptUpdate() AGL= "+terrain.toString())

            // If Height AGL is > 120m / 400ft, this is probably flying so we switch units to meters AMSL and use GPS altitude
            if(altitude-terrain > 120.0){
                markerInList.markerDetails.transmitter?.alt = altitude.toDouble()
                markerInList.markerDetails.receiver.alt = terrain+1
                markerInList.markerDetails.output.units = "m_amsl"
            }else{
                markerInList.markerDetails.output.units = "m"
            }

            // NOTE: If an aircraft causes a switch to AMSL, all other markers will be AMSL
            // Users can override altitude by clicking the marker to open the edit form.

            val index = markersList.indexOf(markerInList)
            if (index != -1) {
                updateAdapter(index)
//                markerAdapter?.notifyItemChanged(index)
                lastUpdatedMarker = markerInList
            }
        }

    }

    if (lastUpdatedMarker != null) {
        calculate(lastUpdatedMarker)
    }
}

fun CoOptAdapter.sortMarkersWithCheckedFirst() {
    val currentMarkers = this.getCurrentMarkers()

    // Sort markers: checked/enabled first, then by original order
    val sortedMarkers = currentMarkers.sortedWith(compareBy<MapItem> { mapItem ->
        // Get the checkbox state for this marker
        val config = this.coOptConfigurations[mapItem.uid]
        val isChecked = config?.isEnabled ?: false

        // Return 0 for checked (top), 1 for unchecked (bottom)
        if (isChecked) 0 else 1
    }.thenBy { mapItem ->
        // Secondary sort: maintain original order within each group
        currentMarkers.indexOf(mapItem)
    })

    this.updateMarkers(sortedMarkers)
}

fun MapView.getAllAvailableMarkers(allContacts: MutableList<Contact>): List<MapItem> {
    // Get all markers from contacts
    val callsignMarkers = allContacts.mapNotNull {
        this.rootGroup.deepFindItem("uid", it.getUID())
    }.toMutableList()
    Log.d(TAG, "Found ${callsignMarkers.size} map items from ${allContacts.size} contacts.")

    // Add self marker
    val self = this.selfMarker
    if (!callsignMarkers.any { it.uid == self.uid }) {
        callsignMarkers.add(self)
    }

    // Add all CoT markers from the map (excluding our own plugin markers)
    fun collectAllMarkers(group: MapGroup): List<MapItem> {
        val markers = mutableListOf<MapItem>()
        for (item in group.items) {
            if (item is Marker && !item.getMetaBoolean("CLOUDRF", false)) {
                // Skip markers that are already in our list and skip our plugin's markers
                if (!callsignMarkers.any { it.uid == item.uid } && item.height > 0) {
                    markers.add(item)
                }else{
                    Log.d(TAG, item.toString());
                }
            }
        }
        // Recursively check child groups
        for (childGroup in group.childGroups) {
            markers.addAll(collectAllMarkers(childGroup))
        }
        return markers
    }

    val allCotMarkers = collectAllMarkers(this.rootGroup)
    Log.d(TAG, "Added ${allCotMarkers.size} CoT markers from map.")

    // Debug: Log available timestamps for first few markers
    allCotMarkers.take(3).forEach { marker ->
        val cotTime = marker.getMetaLong("time", -1L)
        val startTime = marker.getMetaLong("start", -1L)
        val addedTime = marker.getMetaLong("addedTime", -1L)
        val lastUpdateTime = marker.getMetaLong("lastUpdateTime", -1L)
        Log.d(TAG, "Marker ${marker.title ?: marker.uid}: time=$cotTime, start=$startTime, addedTime=$addedTime, lastUpdate=$lastUpdateTime")
    }

    // Sort contacts first, then CoT markers
    val contactMarkers = callsignMarkers.toList() // Current list contains contacts + self
    val sortedContactMarkers = contactMarkers.sortedBy {
        it.title?.takeIf { it.isNotBlank() }
            ?: (it as? Marker)?.getMetaString("callsign", null)?.takeIf { it.isNotBlank() }
            ?: it.getMetaString("name", null)?.takeIf { it.isNotBlank() }
            ?: it.uid
    }

    val sortedCotMarkers = allCotMarkers.sortedByDescending { marker ->
        // Try multiple time sources to get the most accurate creation/update time
        val cotTime = marker.getMetaLong("time", -1L)
        val startTime = marker.getMetaLong("start", -1L)
        val addedTime = marker.getMetaLong("addedTime", -1L)
        val lastUpdateTime = marker.getMetaLong("lastUpdateTime", -1L)

        // Use the most recent non-negative timestamp, or current time as fallback
        listOf(cotTime, startTime, addedTime, lastUpdateTime, System.currentTimeMillis())
            .filter { it > 0 }
            .maxOrNull() ?: System.currentTimeMillis()
    }

    // Combine lists with contacts first, then CoT markers
    val allMarkers = mutableListOf<MapItem>()
    allMarkers.addAll(sortedContactMarkers)
    allMarkers.addAll(sortedCotMarkers)

    Log.d(TAG, "Final list: ${sortedContactMarkers.size} contacts + ${sortedCotMarkers.size} CoT markers = ${allMarkers.size} total")
    return allMarkers
}

fun MapView.delete(mapOverlay: PluginMapOverlay, layer: CloudRFLayer?) {
    if (layer?.fileUri == null) return

    this.removeLayer(
        MapView.RenderStack.MAP_SURFACE_OVERLAYS,
        layer
    )
    mapOverlay.PluginListModel()

    val pathsToDelete: ArrayList<String> = ArrayList()
    pathsToDelete.add(layer.fileUri)

    for (path in pathsToDelete) {
        val intent = Intent(
            ImportExportMapComponent.ACTION_DELETE_DATA
        )
        intent.putExtra(
            ImportReceiver.EXTRA_CONTENT,
            GRGMapComponent.IMPORTER_CONTENT_TYPE
        )
        intent.putExtra(
            ImportReceiver.EXTRA_MIME_TYPE,
            GRGMapComponent.IMPORTER_DEFAULT_MIME_TYPE
        )
        intent.putExtra(ImportReceiver.EXTRA_URI, path)
        AtakBroadcast.getInstance().sendBroadcast(intent)
    }
}

fun MapView.removeLinkLinesFromMap(pluginContext:Context,marker: MarkerDataModel?) {

    val data = this.rootGroup.findMapGroup(pluginContext.getString(R.string.drawing_objects))

    for (it in data.items){
        if(it.title.contains(" dB")){
            Log.d(TAG,"Removing "+it.toString())
            data.removeItem(it)
        }

    }
}

fun MapView.addCustomMarker(
    context: Context,
    selectedMarkerType: TemplateDataModel?,
    mItemType: String,
    markersList: ArrayList<MarkerDataModel>,
    sharedPrefs: AtakPreferences?,
    onItemInserted: (Int) -> Unit,
    onItemChanged: (Int) -> Unit,
    onCalculate: (MarkerDataModel?) -> Unit,
    onRemoveMarker: (MarkerDataModel?) -> Unit
) {
    val uid = UUID.randomUUID().toString()
    val location = this.centerPoint.get()

    val marker = Marker(location, uid).apply {
        setMetaBoolean("readiness", true)
        setMetaBoolean("archive", true)
        setMetaString("how", "h-g-i-g-o")
        setMetaBoolean("editable", true)
        setMetaBoolean("movable", true)
        setMetaBoolean("removable", true)
        setMetaString("entry", "user")
        setMetaBoolean("CLOUDRF", true)
        setMetaString("callsign", selectedMarkerType?.template?.name ?: "Test Marker")
        setMetaString("menu", PluginMenuParser.getMenu(context, "menus/radio_menu.xml"))
        title = selectedMarkerType?.template?.name ?: "Test Marker"
        type = mItemType
    }

    // icon
    val icon: Bitmap? = if (selectedMarkerType?.customIcon == null)
        context.getBitmap(R.drawable.marker_icon_svg)
    else
        selectedMarkerType.customIcon?.base64StringToBitmap()
            ?: context.getBitmap(R.drawable.marker_icon_svg)
    Log.d("BitmapInfo", "Width=${icon?.width}, Height=${icon?.height}")

    val outputStream = ByteArrayOutputStream()
    icon?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    var encoded = "base64://" + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
   if(selectedMarkerType?.customIcon!=null){
      Log.d("AddMarker","Base64:\n ${selectedMarkerType?.customIcon}")
   }
    marker.icon = Icon.Builder().setImageUri(0, encoded).build()

    this.rootGroup.addItem(marker)

    // Listener for map events
    this.mapEventDispatcher.addMapItemEventListener(marker) { mapItem, mapEvent ->
        if (mapItem.type == mItemType) {
            when (mapEvent.type) {
                MapEvent.ITEM_ADDED -> {
                    Log.d(TAG, "mapItem : Added ")
                }
                MapEvent.ITEM_REMOVED -> {
                    Log.d(TAG, "mapItem : Removed ")
                    val item = markersList.find { it.markerID == mapItem.uid }
                    this.removeLinkLinesFromMap(context, item)
                    onRemoveMarker(item)
                }
                MapEvent.ITEM_RELEASE -> {
                    Log.d(TAG, "mapItem : ITEM_RELEASE ")
                }
                MapEvent.ITEM_DRAG_DROPPED -> {
                    val latitude = marker.geoPointMetaData.get().latitude
                    val longitude = marker.geoPointMetaData.get().longitude
                    val item = markersList.find { it.markerID == mapItem.uid }
                    item?.let {
                        it.markerDetails.transmitter?.lat = latitude.roundValue()
                        it.markerDetails.transmitter?.lon = longitude.roundValue()
                        sharedPrefs?.saveMarkerListToPref(markersList)
                        onItemChanged(markersList.indexOf(it))
//                        markerAdapter?.notifyItemChanged(markersList.indexOf(it))
                    }
                    onCalculate(item)
                }
                else -> Unit
            }
        }
    }

    // clone TemplateDataModel to a new MarkerDataModel
    selectedMarkerType?.let {
        val copiedMarkerType = it.deepCopy()
        val markerItem = MarkerDataModel(uid, copiedMarkerType)
        markerItem.markerDetails.transmitter?.lat = location.latitude.roundValue()
        markerItem.markerDetails.transmitter?.lon = location.longitude.roundValue()

        markersList.add(markerItem)
        sharedPrefs?.saveMarkerListToPref(markersList)
        onItemInserted(markersList.indexOf(markerItem))
//        markerAdapter?.notifyItemInserted(markersList.indexOf(markerItem))
    }
}

fun MapView.removeMarkerFromMap(marker: MarkerDataModel?) {
    marker?.let {
        val item: MapItem? = this.rootGroup.items.find { mapItem ->
            mapItem.uid == it.markerID
        }
        this.rootGroup.removeItem(item)
    }
}

fun AtakPreferences?.saveMarkerListToPref(markersList:ArrayList<MarkerDataModel>) {
    this?.set(Constant.PreferenceKey.sMarkerList, Gson().toJson(markersList))
}

fun AtakPreferences?.saveSettingTemplateListToPref(settingTemplateList:MutableList<MutableTuple<TemplateDataModel, Boolean, String?>>) {
    this?.set(Constant.PreferenceKey.sSettingTemplateList, Gson().toJson(settingTemplateList))
}

fun AtakPreferences? .getSettingTemplateListFromPref(): MutableList<MutableTuple<TemplateDataModel, Boolean, String?>>?{
    val list: MutableList<MutableTuple<TemplateDataModel, Boolean, String?>>? =
        Gson().fromJson(
            this?.get(Constant.PreferenceKey.sSettingTemplateList, null),
            object : TypeToken<MutableList<MutableTuple<TemplateDataModel, Boolean, String?>>>() {}.type
        )
    return list
}

fun PluginDropDownReceiver.handleShowPlugin(templateView: View, sharedPrefs: AtakPreferences?, saveData:()->Unit) {
    showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH, HALF_HEIGHT, false, this)

    try {
        val templateList: ArrayList<MarkerDataModel>? =
            Gson().fromJson(
                sharedPrefs?.get(Constant.PreferenceKey.sMarkerList, null),
                object : TypeToken<ArrayList<MarkerDataModel>>() {}.type
            )

        // Example: if you want to do something with only the ones on the map
        val commonList = templateList?.filter { marker ->
            mapView.rootGroup.items.any { items -> marker.markerID == items.uid }
        }
        // store / update something with commonList if needed
    } catch (e: Exception) {
        Log.e(TAG, "error", e)
    }
    saveData()
}

fun TemplateDataModel.getModifiedMarker(): TemplateDataModel {
    val marker=this
    return TemplateDataModel(
        marker.antenna,
        marker.coordinates,
        marker.engine,
        marker.environment,
        marker.feeder,
        marker.model,
        marker.network,
        marker.output,
        marker.receiver,
        marker.reference,
        marker.site,
        marker.template,
        marker.transmitter,
        marker.version,
        marker.bounds
    )
}

fun Receiver.getModifiedReceiver(): Receiver {
    val pReceiver = this
    return Receiver(pReceiver.alt, 0.0, 0.0, pReceiver.rxg, pReceiver.rxs)
}

fun MarkerDataModel.toLinkDataModel(markersList: List<MarkerDataModel>): LinkDataModel {
    val points = markersList.mapNotNull { data ->
        if (data.markerID != this.markerID) {
            Point(
                data.markerID,
                data.markerDetails.transmitter?.alt,
                data.markerDetails.transmitter?.lat,
                data.markerDetails.transmitter?.lon
            )
        } else null
    }

    val thisRx = Receiver(
        markerDetails.transmitter?.alt ?: 1.0,
        markerDetails.transmitter?.lat ?: 0.0,
        markerDetails.transmitter?.lon ?: 0.0,
        markerDetails.antenna.txg,
        markerDetails.receiver.rxs
    )

    val linkRequest = LinkRequest(
        markerDetails.antenna,
        markerDetails.environment,
        markerDetails.model,
        markerDetails.network,
        markerDetails.output,
        points,
        thisRx,
        markerDetails.site,
        markerDetails.transmitter
    )
    return LinkDataModel(this.markerID, linkRequest, ArrayList(), null)
}

fun MapView.drawLinksForResponse(
    pluginContext: Context,
    transmitter: Transmitter?,
    linkResponse: LinkResponse?,
    linkUnits: String,
    lineGroup: MapGroup?,
    linksList: ArrayList<Link>,
    markerLinkList: ArrayList<LinkDataModel>,
    getLineColour: (Double) -> Int?,
    handleLinkLineVisibility: () -> Unit
) {
    transmitter?.let { tx ->
        linkResponse?.let { linkResponse ->
            for (data in linkResponse.transmitters) {
                var powerLevel = data.signalToNoiseRatioDB
                if (linkUnits == "dBm") {
                    powerLevel = data.signalPowerAtReceiverDBm
                }
                getLineColour(powerLevel)?.let { color ->
                    this.drawLine(
                        pluginContext,
                        data.markerId,
                        linksList,
                        GeoPoint(tx.lat, tx.lon, tx.alt),
                        GeoPoint(
                            (tx.lat + data.latitude) / 2,
                            (tx.lon + data.longitude) / 2,
                            data.antennaHeight
                        ),
                        color,
                        powerLevel.toInt(),
                        linkUnits,
                        lineGroup, markerLinkList = markerLinkList
                    ) {
                        handleLinkLineVisibility()
                    }
                }
            }
        }
    }
}

fun TemplateDataModel.deepCopy(): TemplateDataModel {
    val output = ByteArrayOutputStream()
    ObjectOutputStream(output).use { it.writeObject(this) }
    val bytes = output.toByteArray()
    ObjectInputStream(bytes.inputStream()).use {
        return it.readObject() as TemplateDataModel
    }
}
