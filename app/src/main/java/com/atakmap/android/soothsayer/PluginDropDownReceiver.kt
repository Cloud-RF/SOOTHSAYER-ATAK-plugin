package com.atakmap.android.soothsayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.*
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atak.plugins.impl.PluginLayoutInflater
import com.atakmap.android.drawing.mapItems.DrawingShape
import com.atakmap.android.dropdown.DropDown.OnStateListener
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.grg.GRGMapComponent
import com.atakmap.android.hierarchy.HierarchyListReceiver
import com.atakmap.android.importexport.ImportExportMapComponent
import com.atakmap.android.importexport.ImportReceiver
import com.atakmap.android.ipc.AtakBroadcast
import com.atakmap.android.maps.*
import com.atakmap.android.maps.MapView.RenderStack
import com.atakmap.android.menu.PluginMenuParser
import com.atakmap.android.preference.AtakPreferences
import com.atakmap.android.soothsayer.interfaces.CloudRFLayerListener
import com.atakmap.android.soothsayer.layers.CloudRFLayer
import com.atakmap.android.soothsayer.layers.GLCloudRFLayer
import com.atakmap.android.soothsayer.layers.PluginMapOverlay
import com.atakmap.android.soothsayer.models.common.MarkerDataModel
import com.atakmap.android.soothsayer.models.linksmodel.*
import com.atakmap.android.soothsayer.models.request.MultiSiteTransmitter
import com.atakmap.android.soothsayer.models.request.MultisiteRequest
import com.atakmap.android.soothsayer.models.request.Receiver
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.models.response.ResponseModel
import com.atakmap.android.soothsayer.network.remote.RetrofitClient
import com.atakmap.android.soothsayer.network.repository.PluginRepository
import com.atakmap.android.soothsayer.plugin.R
import com.atakmap.android.soothsayer.recyclerview.RecyclerViewAdapter
import com.atakmap.android.soothsayer.util.*
import com.atakmap.android.util.SimpleItemSelectedListener
import com.atakmap.coremap.log.Log
import com.atakmap.coremap.maps.assets.Icon
import com.atakmap.coremap.maps.coords.GeoPoint
import com.atakmap.map.layer.opengl.GLLayerFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.util.*


class PluginDropDownReceiver (
    mapView: MapView?,
    private val pluginContext: Context, private val mapOverlay: PluginMapOverlay
) : DropDownReceiver(mapView), OnStateListener {
    // Remember to use the PluginLayoutInflater if you are actually inflating a custom view.
    private val templateView: View = PluginLayoutInflater.inflate(
        pluginContext,
        R.layout.main_layout, null
    )
    private val mainLayout: LinearLayout = templateView.findViewById(R.id.llMain)
    private val settingView = templateView.findViewById<LinearLayout>(R.id.ilSettings)
    private val radioSettingView = templateView.findViewById<LinearLayout>(R.id.ilRadioSetting)
    private val svMode: Switch = settingView.findViewById(R.id.svMode)
    private val cbCoverageLayer: CheckBox = settingView.findViewById(R.id.cbKmzLayer)
    private val cbLinkLines: CheckBox = settingView.findViewById(R.id.cbLinkLines)
    private var etServerUrl: EditText? = null
    private var etApiKey: EditText? = null
    private var markersList: ArrayList<MarkerDataModel> = ArrayList()
    private var selectedMarkerType: TemplateDataModel? = null
    private val templateItems: ArrayList<TemplateDataModel> = ArrayList()
    private var markerAdapter: RecyclerViewAdapter? = null
    private val mItemType: String = "custom-type"
    private val repository by lazy { PluginRepository.getInstance() }
    private var sharedPrefs: AtakPreferences? = AtakPreferences(mapView?.context)
    private var cloudRFLayer: CloudRFLayer? = null
    private var singleSiteCloudRFLayer: CloudRFLayer? = null
    private var markerLinkList: ArrayList<LinkDataModel> = ArrayList()
    private var lineGroup: MapGroup? = null
    private var itemPositionForEdit: Int = -1


    init {
        initViews()
        initListeners()
    }

    private fun initViews() {
        pluginContext.createAndStoreFiles(getAllFilesFromAssets())
        initSettings()
        initRadioSettingView()
        initSpinner()
        initRecyclerview()
    }

    private fun initListeners() {
        // The button below shows settings layout and hides the actual layout
        val btnOpenSettings: ImageView = templateView.findViewById(R.id.ivSettings)
        btnOpenSettings.setOnClickListener {
            // set views with the saved setting values
            setDataFromPref()
            if (sharedPrefs?.get(Constant.PreferenceKey.sServerUrl, "")?.isEmpty() == true) {
                etServerUrl?.setText(RetrofitClient.DEFAULT_URL)
            }
            if (sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "")?.isEmpty() == true) {
                etApiKey?.setText(RetrofitClient.DEFAULT_APIKEY)
            }
            mainLayout.visibility = View.GONE
            settingView.visibility = View.VISIBLE
        }

        val btnSave = settingView.findViewById<Button>(R.id.btnSave)
        btnSave.setOnClickListener {
            if (isValidSettings()) {
                Constant.sServerUrl = etServerUrl?.text.toString()
                Constant.sAccessToken = etApiKey?.text.toString()

                sharedPrefs?.set(Constant.PreferenceKey.sServerUrl, Constant.sServerUrl)
                sharedPrefs?.set(Constant.PreferenceKey.sApiKey, Constant.sAccessToken)
                sharedPrefs?.set(Constant.PreferenceKey.sCalculationMode, svMode.isChecked)
                sharedPrefs?.set(Constant.PreferenceKey.sKmzVisibility, cbCoverageLayer.isChecked)
                sharedPrefs?.set(Constant.PreferenceKey.sLinkLinesVisibility, cbLinkLines.isChecked)
                moveBackToMainLayout()
                handleLinkLineVisibility()
                handleKmzLayerVisibility()
                refreshView()
            }
        }

        // open help dialog
        val tvHelp = settingView.findViewById<TextView>(R.id.tvHelp)
        tvHelp.setOnClickListener {
            showHelpDialog()
        }

        val ivBack = settingView.findViewById<ImageView>(R.id.ivBack)
        ivBack.setOnClickListener {
            setDataFromPref()
            moveBackToMainLayout()
        }

        // add a marker on the map
        val btnAddMarker = templateView.findViewById<ImageButton>(R.id.btnAddMarker)
        btnAddMarker.setOnClickListener {
            if (URLUtil.isValidUrl(etServerUrl?.text.toString()) && etApiKey?.text?.trim()
                    ?.isNotEmpty() == true
            ) {
                addCustomMarker()
            } else {
                pluginContext.toast(pluginContext.getString(R.string.marker_error))
            }
        }
    }

    private fun initRecyclerview() {
        val recyclerView: RecyclerView = templateView.findViewById(R.id.rvTemplates)
        markerAdapter = RecyclerViewAdapter(markersList, mapView, pluginContext, onItemRemove = {
            removeMarker(it)
        }, onItemSelected = { position, marker ->
            itemPositionForEdit = position

            setEditViewVisibility(true)
            setEditViewData(marker)
        })
        recyclerView.layoutManager = LinearLayoutManager(
            pluginContext,
            LinearLayoutManager.VERTICAL, false
        )
        recyclerView.adapter = markerAdapter
    }

    private fun initSpinner() {
        // Set Template spinner list from json files.
        val spinner: Spinner = templateView.findViewById(R.id.spTemplate)
        templateItems.addAll(getTemplatesFromFolder())
        val adapter: ArrayAdapter<TemplateDataModel> = object :
            ArrayAdapter<TemplateDataModel>(
                pluginContext,
                R.layout.spinner_item_layout,
                templateItems
            ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as TextView
                val item: TemplateDataModel? = getItem(position)
                if (item != null) {
                    textView.text = item.template.name
                }
                return textView
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val textView = super.getDropDownView(position, convertView, parent) as TextView
                val item: TemplateDataModel? = getItem(position)
                if (item != null) {
                    textView.text = item.template.name
                }
                return textView
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(0)
        spinner.onItemSelectedListener = object : SimpleItemSelectedListener() {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View,
                position: Int, id: Long
            ) {
                selectedMarkerType = templateItems[position]
            }
        }

        spinner.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Load templates from folder
                    val extraTemplates = getTemplatesFromFolder()
                    if (extraTemplates.isEmpty()) { // add default files again so that folder is not empty.
                        pluginContext.createAndStoreFiles(getAllFilesFromAssets())
                        templateItems.clear()
                        templateItems.addAll(getTemplatesFromFolder())
                    } else {
                        if (extraTemplates.size != templateItems.size) {
                            Log.d(TAG, "extraTemplates : ${extraTemplates.size}")
                            spinner.adapter?.let { adapter ->
                                if (adapter is ArrayAdapter<*>) {
                                    templateItems.clear()
                                    templateItems.addAll(extraTemplates)
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    // tap was detected, perform click
                    spinner.performClick()
                }
            }
            false
        }
    }

    private fun initSettings() {
        etServerUrl = settingView.findViewById(R.id.etServerUrl)
        etServerUrl?.setText(Constant.sServerUrl)
        etApiKey = settingView.findViewById(R.id.etApiKey)
        etApiKey?.setText(Constant.sAccessToken)
    }

    private fun initRadioSettingView() {
        radioSettingView.apply {
            val radioBack: ImageView = findViewById(R.id.ivRadioBack)
            val etRadioHeight: EditText = findViewById(R.id.etRadioHeight)
            val etRadioPower: EditText = findViewById(R.id.etRadioPower)
            val etAntennaAzimuth: EditText = findViewById(R.id.etAntennaAzimuth)
            val etFrequency: EditText = findViewById(R.id.etFrequency)
            val etBandWidth: EditText = findViewById(R.id.etBandWidth)
            val etOutputNoiseFloor: EditText = findViewById(R.id.etOutputNoiseFloor)
            radioBack.setOnClickListener {
                setEditViewVisibility(false)
            }
            findViewById<Button>(R.id.btnReCalculate).setOnClickListener {
                if (markersList.isNotEmpty() && itemPositionForEdit != -1) {
                    val marker = markersList[itemPositionForEdit].markerDetails
                    Log.d(TAG, "initRadioSettingView : marker : $marker \nbefore update ${markersList[itemPositionForEdit]}")
                    val isEdit =
                        (marker.transmitter?.alt.toString() != etRadioHeight.text.toString() && etRadioHeight.text.isNotEmpty()) ||
                                (marker.transmitter?.txw.toString() != etRadioPower.text.toString() && etRadioPower.text.isNotEmpty()) ||
                                (marker.transmitter?.frq.toString() != etFrequency.text.toString() && etFrequency.text.isNotEmpty()) ||
                                (marker.transmitter?.bwi.toString() != etBandWidth.text.toString() && etBandWidth.text.isNotEmpty()) ||
                                (marker.output.nf.toString() != etOutputNoiseFloor.text.toString() && etOutputNoiseFloor.text.isNotEmpty()) ||
                                (marker.antenna.azi.toString() != etAntennaAzimuth.text.toString() && etAntennaAzimuth.text.isNotEmpty())

                    if (isEdit) {
                        //change the marker data
                        marker.transmitter?.let { transmitter ->
                            etRadioHeight.text.toString().toIntOrNull()?.let { transmitter.alt = it }
                            etRadioPower.text.toString().toDoubleOrNull()?.let { transmitter.txw = it }
                            etFrequency.text.toString().toDoubleOrNull()?.let { transmitter.frq = it }
                            etBandWidth.text.toString().toDoubleOrNull()?.let { transmitter.bwi = it }
                        }
                        etOutputNoiseFloor.text.toString().toIntOrNull()?.let { marker.output.nf = it }
                        etAntennaAzimuth.text.toString().toIntOrNull()?.let { marker.antenna.azi = it }
                        Log.d(TAG, "initRadioSettingView : after update ${markersList[itemPositionForEdit]}")
                        markerAdapter?.notifyDataSetChanged()

                        if(cbLinkLines.isChecked) {
                            // UPDATE MARKER
                            markersList[itemPositionForEdit].markerDetails = marker
                            updateLinkLinesOnMarkerDragging(markersList[itemPositionForEdit])
                        }

                        itemPositionForEdit = -1

                        // trigger calc
                         if (svMode.isChecked) {
                             marker.let { template ->
                                 val list: List<MultiSiteTransmitter> =
                                     markersList.mapNotNull { marker ->
                                         marker.markerDetails.transmitter?.run {
                                             MultiSiteTransmitter(
                                                 alt,
                                                 bwi,
                                                 frq,
                                                 lat,
                                                 lon,
                                                 powerUnit,
                                                 txw,
                                                 marker.markerDetails.antenna
                                             )
                                         }
                                     }

                                 val request = MultisiteRequest(
                                     template.site,
                                     template.network,
                                     list,
                                     template.receiver,
                                     template.model,
                                     template.environment,
                                     template.output
                                 )
                                 if (cbCoverageLayer.isChecked) {
                                     sendMultiSiteDataToServer(request)
                                 }
                             }
                        }
                    }
                }
                setEditViewVisibility(false)
            }
        }
    }

    private fun showHelpDialog() {
        val builderSingle = AlertDialog.Builder(
            mapView.context
        )
        builderSingle.setTitle(pluginContext.getString(R.string.help_title))
        builderSingle.setMessage(pluginContext.getString(R.string.help_msg))
        builderSingle.setNegativeButton(
            pluginContext.getString(R.string.ok_txt)
        ) { dialog, _ -> dialog.dismiss() }
        builderSingle.show()
    }

    private fun moveBackToMainLayout() {
        mainLayout.visibility = View.VISIBLE
        settingView.visibility = View.GONE
    }

    private fun setEditViewVisibility(isEdit: Boolean) {
        mainLayout.visibility = if (isEdit) View.GONE else View.VISIBLE
        radioSettingView.visibility = if (isEdit) View.VISIBLE else View.GONE
    }

    /**
     * Method is used to set data from selected marker to editView opened on right side.
     * */
    private fun setEditViewData(item: MarkerDataModel) {
        val title = pluginContext.getString(R.string.radio_settings, item.markerDetails.template.name).setSpannableText()

        val radioSettingView = radioSettingView
        val transmitter = item.markerDetails.transmitter
        val antenna = item.markerDetails.antenna

        with(radioSettingView) {
            findViewById<TextView>(R.id.tvRadioTitle).text = title
            findViewById<EditText>(R.id.etRadioHeight).setText("${transmitter?.alt ?: ""}")
            findViewById<EditText>(R.id.etRadioPower).setText("${transmitter?.txw ?: ""}")
            findViewById<EditText>(R.id.etAntennaAzimuth).setText("${antenna.azi}")
            findViewById<EditText>(R.id.etFrequency).setText("${transmitter?.frq ?: ""}")
            findViewById<EditText>(R.id.etBandWidth).setText("${transmitter?.bwi ?: ""}")
            findViewById<EditText>(R.id.etOutputNoiseFloor).setText("${item.markerDetails.output.nf}")
        }
    }

    private fun isValidSettings(): Boolean {
        var isValid = true
        val message = when {
            !URLUtil.isValidUrl(etServerUrl?.text.toString()) -> {
                pluginContext.getString(R.string.invalid_url_error)
            }
            etApiKey?.text?.trim()?.isEmpty() == true -> {
                pluginContext.getString(R.string.empty_api_key)
            }
            (etApiKey?.text?.trim()?.length ?: 0) < 12 -> {
                pluginContext.getString(R.string.unauthorized_error)
            }
            else -> {
                null
            }
        }
        message?.let {
            isValid = false
            pluginContext.toast(message)
        }
        return isValid
    }

    private fun addCustomMarker() {
        val uid = UUID.randomUUID().toString()
        val location = mapView.centerPoint.get()
        Log.d(TAG, "location : $location")
        val marker = Marker(location, uid)
        // marker.type = "a-f-G-U-C-I"
        // m.setMetaBoolean("disableCoordinateOverlay", true); // used if you don't want the coordinate overlay to appear
        marker.setMetaBoolean("readiness", true)
        marker.setMetaBoolean("archive", true)
        marker.setMetaString("how", "h-g-i-g-o")
        marker.setMetaBoolean("editable", true)
        marker.setMetaBoolean("movable", true)
        marker.setMetaBoolean("removable", true)
        marker.setMetaString("entry", "user")
        marker.setMetaString("callsign", selectedMarkerType?.template?.name ?: "Test Marker")
        marker.setMetaString(
            "menu",
            PluginMenuParser.getMenu(pluginContext, "menus/radio_menu.xml")
        )
        marker.title = selectedMarkerType?.template?.name ?: "Test Marker"
        marker.type = mItemType
//        marker.setShowLabel(false)

        // Add custom icon. TODO: custom icons!
        val icon: Bitmap? = pluginContext.getBitmap(R.drawable.marker_icon_svg)
        val outputStream = ByteArrayOutputStream()
        icon?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val b = outputStream.toByteArray()
        val encoded = "base64://" + Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE)
        val markerIconBuilder = Icon.Builder().setImageUri(0, encoded)
        marker.icon = markerIconBuilder.build()
        mapView.rootGroup.addItem(marker)

        // Listener for marker removed and dragged on the map.
        mapView.mapEventDispatcher.addMapItemEventListener(
            marker
        ) { mapItem, mapEvent ->
            if (mapItem.type == mItemType) {
                when (mapEvent.type) {
                    MapEvent.ITEM_ADDED -> {
                        // not getting any callback while adding the marker.
                        Log.d(TAG, "mapItem : Added ")
                    }
                    MapEvent.ITEM_REMOVED -> {
                        Log.d(TAG, "mapItem : Removed ")
                        val item: MarkerDataModel? = markersList.find { marker ->
                            marker.markerID == mapItem.uid
                        }

                        // remove marker from list
                        removeMarkerFromList(item)
                        // remove link lines from map if exists for that marker.
                        removeLinkLinesFromMap(item)

                        // update coverage layer - WIP
                       /* if (svMode.isChecked) {
                            item?.markerDetails?.let { template ->
                                val list: List<MultiSiteTransmitter> =
                                    markersList.mapNotNull { marker ->
                                        marker.markerDetails.transmitter?.run {
                                            MultiSiteTransmitter(
                                                alt,
                                                bwi,
                                                frq,
                                                lat,
                                                lon,
                                                powerUnit,
                                                txw,
                                                marker.markerDetails.antenna
                                            )
                                        }
                                    }

                                val request = MultisiteRequest(
                                    template.site,
                                    template.network,
                                    list,
                                    template.receiver,
                                    template.model,
                                    template.environment,
                                    template.output
                                )
                                if (cbCoverageLayer.isChecked) {
                                    sendMultiSiteDataToServer(request)
                                }
                            }
                        }*/
                    }
                    MapEvent.ITEM_RELEASE -> {
                        Log.d(TAG, "mapItem : ITEM_RELEASE ")
                    }
                    MapEvent.ITEM_DRAG_DROPPED -> {
                        val latitude = marker.geoPointMetaData.get().latitude
                        val longitude = marker.geoPointMetaData.get().longitude
                        Log.d(
                            TAG,
                            "DragDropped latitude: $latitude Longitude: $longitude Marker_id: ${mapItem.uid} actual uid = $uid"
                        )
                        Log.d(
                            TAG,
                            "MapItem: ${mapItem.altitudeMode} radialMenuPath: ${mapItem.radialMenuPath} serialId: ${mapItem.serialId} zOrder: ${mapItem.zOrder} "
                        )

                        // update the lat and lon of that marker.
                        val item = markersList.find { it.markerID == mapItem.uid }
                        item?.let {
                            val index = markersList.indexOf(item)
                            Log.d(
                                TAG,
                                "Before latitude: ${markersList[index].markerDetails.transmitter?.lat} Longitude: ${markersList[index].markerDetails.transmitter?.lon}"
                            )
                            item.markerDetails.transmitter?.lat = latitude.roundValue()
                            item.markerDetails.transmitter?.lon = longitude.roundValue()
                            saveMarkerListToPref()
                            Log.d(
                                TAG,
                                "After latitude: ${markersList[index].markerDetails.transmitter?.lat} Longitude: ${markersList[index].markerDetails.transmitter?.lon}"
                            )
                            markerAdapter?.notifyItemChanged(markersList.indexOf(item))
                        }
                        if (svMode.isChecked) {
                            // For multisite api
                            item?.markerDetails?.let { template ->
                                val list: List<MultiSiteTransmitter> =
                                    markersList.mapNotNull { marker ->
                                        marker.markerDetails.transmitter?.run {
                                            MultiSiteTransmitter(
                                                alt,
                                                bwi,
                                                frq,
                                                lat,
                                                lon,
                                                powerUnit,
                                                txw,
                                                marker.markerDetails.antenna
                                            )
                                        }
                                    }

                                val request = MultisiteRequest(
                                    template.site,
                                    template.network,
                                    list,
                                    template.receiver,
                                    template.model,
                                    template.environment,
                                    template.output
                                )
                                if(cbCoverageLayer.isChecked){
                                    sendMultiSiteDataToServer(request)
                                }
                            }
                        } else {
//                            For single site api
                            item?.let {
                                // send marker position changed data to server.
                                if(cbCoverageLayer.isChecked) {
                                    sendSingleSiteDataToServer(item.markerDetails)
                                }
                            }
                        }
                        item?.let {
                            if(cbLinkLines.isChecked) {
                                updateLinkLinesOnMarkerDragging(item)
                            }
                        }
                    }
                }
            }
        }

        // add marker to list
        selectedMarkerType?.let {
            // below code is to create a new object from selectedMarkerType
            val output = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(output)
            objectOutputStream.writeObject(it)
            objectOutputStream.flush()
            objectOutputStream.close()
            output.close()

            val inputStream = ByteArrayInputStream(output.toByteArray())
            val objectInputStream = ObjectInputStream(inputStream)
            val copiedMarkerType = objectInputStream.readObject() as TemplateDataModel
            objectInputStream.close()
            inputStream.close()

            val markerItem = MarkerDataModel(uid, copiedMarkerType)
            markerItem.markerDetails.transmitter?.lat = location.latitude.roundValue()
            markerItem.markerDetails.transmitter?.lon = location.longitude.roundValue()
            markersList.add(markerItem)

            saveMarkerListToPref()
            markerAdapter?.notifyItemInserted(markersList.indexOf(markerItem))
            getLinksBetween(markerItem)
        }

        Log.d(TAG, "${markersList.size} listData : ${Gson().toJson(markersList)}")
    }

    private fun getLinksBetween(marker: MarkerDataModel?) {
        marker?.let {
            val points: List<Point> = markersList.mapNotNull { data ->
                if (data.markerID != marker.markerID) {
                    Point(
                        data.markerID,
                        data.markerDetails.transmitter?.alt,
                        data.markerDetails.transmitter?.lat,
                        data.markerDetails.transmitter?.lon
                    )
                } else {
                    null
                }
            }
            Log.d(TAG, "getLinksBetween Points: ${Gson().toJson(points)}")

            // override receiver with this location
            // This is the dragged marker. During this test, the gains used for Rx come from the subject's Tx block
            val thisRx = Receiver(
                    marker.markerDetails.transmitter?.alt ?: 1,
                    marker.markerDetails.transmitter?.lat ?: 0.0,
                    marker.markerDetails.transmitter?.lon ?: 0.0,
                   marker.markerDetails.antenna.txg,
                   marker.markerDetails.receiver.rxs
            )
            it.markerDetails.receiver = thisRx;

            var omni = it.markerDetails.antenna
            omni.ant = 1

            val linkRequest = LinkRequest(
                omni,
                it.markerDetails.environment,
                it.markerDetails.model,
                it.markerDetails.network,
                it.markerDetails.output,
                points,
                it.markerDetails.receiver,
                it.markerDetails.site,
                it.markerDetails.transmitter
            )
            val linkDataModel = LinkDataModel(it.markerID, linkRequest, ArrayList(), null)

            if (markerLinkList.isEmpty()) {
                Log.d(TAG, "getLinksBetween markerLinkList empty")
                markerLinkList.add(linkDataModel)
            } else {
                Log.d(TAG, "getLinksBetween markerLinkList not empty")
                repository.getLinks(linkDataModel.linkRequest,
                    object : PluginRepository.ApiCallBacks {
                        override fun onLoading() {
                            //pluginContext.toast(pluginContext.getString(R.string.loading_link_msg))
                        }

                        override fun onSuccess(response: Any?) {
                            linkDataModel.linkResponse = response as LinkResponse
                            markerLinkList.add(linkDataModel)
                            linkDataModel.linkRequest.transmitter?.let { transmitter ->
                                linkDataModel.linkResponse?.let { linkResponse ->
                                    for (data in linkResponse.transmitters) {
                                        Log.d(TAG, "link SNR = "+data.signalToNoiseRatioDB)
                                        pluginContext.getLineColor(data.signalToNoiseRatioDB)
                                            ?.let { color ->
                                                drawLine(
                                                    data.markerId,
                                                    linkDataModel.links,
                                                    GeoPoint(transmitter.lat, transmitter.lon),
                                                    GeoPoint(data.latitude, data.longitude),
                                                    color,
                                                    data.signalToNoiseRatioDB.toInt()
                                                )
                                            }
                                    }
                                }
                            }
                            //pluginContext.toast("Success")
                        }

                        override fun onFailed(error: String?, responseCode: Int?) {
                            pluginContext.toast("onFailed creating link : $error")
                        }

                    })
            }
        }
    }

    private fun drawLine(
        linkToId: String?, // marker id to which link is created
        links: ArrayList<Link>,
        startPoint: GeoPoint,
        endPoint: GeoPoint,
        lineColor: Int,
        snr: Int
    ) {
        val uid = snr
        val mapView = mapView
        if(lineGroup == null) {
            lineGroup = mapView.rootGroup.findMapGroup(pluginContext.getString(R.string.drawing_objects))
        }
        val dslist: MutableList<DrawingShape> = ArrayList()
        val dsUid = UUID.randomUUID().toString()
        val ds = DrawingShape(mapView,dsUid)
        ds.strokeColor = lineColor
        ds.points = arrayOf(startPoint, endPoint)
        ds.hideLabels(false)
        ds.lineLabel = "${snr} dB"
        dslist.add(ds)

        val lineUid = UUID.randomUUID().toString()
        val mp = MultiPolyline(mapView, lineGroup, dslist, lineUid)
        lineGroup?.addItem(mp)
        mp.movable = true
        mp.title = "${snr} dB"
        mp.lineLabel = "${snr} dB"
        mp.hideLabels(false)
        // this "toggleMetaData" is set to true if we want to display label by default.
        mp.toggleMetaData("labels_on", true)
        links.add(Link(lineUid, startPoint, endPoint))
        // add link item to links of other marker so that when we delete item it's link get deleted
        for (item in markerLinkList) {
            if (item.markerId == linkToId) {
                item.links.add(Link(lineUid, endPoint, startPoint))
            }
        }
        handleLinkLineVisibility()
    }

    private fun updateLinkLinesOnMarkerDragging(markerItem: MarkerDataModel){
        // remove link lines from map if exists for that marker.
        removeLinkLinesFromMap(markerItem)
        // add new link lines
        getLinksBetween(markerItem)
    }

    private fun getModifiedMarker(marker: TemplateDataModel): TemplateDataModel {
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
            marker.version
        )
    }

    private fun getModifiedReceiver(pReceiver: Receiver): Receiver {
        return Receiver(pReceiver.alt, 0.0, 0.0, pReceiver.rxg, pReceiver.rxs)
    }

    // Area API
    private fun sendSingleSiteDataToServer(marker: TemplateDataModel?) {
        // For an area call, the receiver lat and lon should be zero.
        if (pluginContext.isConnected()) {
            marker?.let {
                // creating deep copy so that it would not modify the actual object.
                Log.d(TAG, "sendSingleSiteDataToServer: old:$marker")
                val markerData = getModifiedMarker(marker)
                markerData.receiver = getModifiedReceiver(marker.receiver)
                Log.d(TAG, "sendSingleSiteDataToServer: old:$marker \n request: ${Gson().toJson(markerData)}")
                repository.sendSingleSiteMarkerData(
                    markerData,
                    object : PluginRepository.ApiCallBacks {
                        override fun onLoading() {
                            pluginContext.toast(pluginContext.getString(R.string.loading_msg))
                        }

                        override fun onSuccess(response: Any?) {
                            Log.d(TAG, "onSuccess called response: ${Gson().toJson(response)}")
                            //pluginContext.toast(pluginContext.getString(R.string.success_msg)) // gets annoying
                            if (response is ResponseModel) {
                                // download PNG image
                                repository.downloadFile(response.PNG_WGS84,
                                    FOLDER_PATH,
                                    PNG_IMAGE.getFileName(),
                                    listener = { isDownloaded, filePath ->
                                        if (isDownloaded) {
                                            //adding layer using image
                                            addSingleKMZLayer(
                                                markerData.template.name,
                                                filePath,
                                                response.bounds
                                            )
                                        }
                                    })

                                //Download kmz file also to SD card
                                repository.downloadFile(response.kmz,
                                    KMZ_FOLDER,
                                    KMZ_FILE.getFileName(),
                                    listener = { isDownloaded, filePath ->
                                        Log.d(
                                            TAG,
                                            "sendSingleSiteMarkerData: kmz downloaded isDownloaded:$isDownloaded to path: $filePath "
                                        )
                                    })
                            }
                        }

                        override fun onFailed(error: String?, responseCode: Int?) {
                            Log.e(
                                TAG,
                                "onFailed called token: ${Constant.sAccessToken} error:$error responseCode:$responseCode"
                            )

                            val message = when (responseCode) {
//                                Constant.ApiErrorCodes.sUnAuthorized, Constant.ApiErrorCodes.sBadRequest -> {
                                Constant.ApiErrorCodes.sUnAuthorized -> {
                                    pluginContext.getString(R.string.unauthorized_error)
                                }
                                Constant.ApiErrorCodes.sForbidden -> {
                                    pluginContext.getString(R.string.forbidden_url_error)
                                }
                                Constant.ApiErrorCodes.sNotFound -> {
                                    pluginContext.getString(R.string.not_found_url_error)
                                }
                                else -> {
                                    error ?: pluginContext.getString(R.string.error_msg)
                                }
                            }
                            pluginContext.toast(message)
                        }
                    })
            }
        } else {
            pluginContext.toast(pluginContext.getString(R.string.internet_error))
        }
    }

    // Multisite API (GPU!)
    private fun sendMultiSiteDataToServer(markerData: MultisiteRequest?) {
        if (pluginContext.isConnected()) {
            markerData?.let {
                repository.sendMultiSiteMarkerData(
                    markerData,
                    object : PluginRepository.ApiCallBacks {
                        override fun onLoading() {
                            pluginContext.toast(pluginContext.getString(R.string.loading_msg))
                        }

                        override fun onSuccess(response: Any?) {
                            Log.d(TAG, "onSuccess called response: ${Gson().toJson(response)}")
                            //pluginContext.toast(pluginContext.getString(R.string.success_msg))
                            if (response is ResponseModel) {
                                // download PNG image
                                repository.downloadFile(response.PNG_WGS84,
                                    FOLDER_PATH,
                                    PNG_IMAGE.getFileName(),
                                    listener = { isDownloaded, filePath ->
                                        if (isDownloaded) {
                                            //adding layer using image file
                                            addKMZLayer(filePath, response.bounds)
                                        }
                                    })

                                // Download kmz file also to SD card
                                repository.downloadFile(response.kmz,
                                    KMZ_FOLDER,
                                    KMZ_FILE.getFileName(),
                                    listener = { isDownloaded, filePath ->
                                        Log.d(
                                            TAG,
                                            "sendMultiSiteMarkerData: kmz downloaded isDownloaded:$isDownloaded to path: $filePath "
                                        )
                                    })
                            }
                        }

                        override fun onFailed(error: String?, responseCode: Int?) {
                            Log.e(
                                TAG,
                                "onFailed called token: ${Constant.sAccessToken} error:$error responseCode:$responseCode"
                            )
                            val message = when (responseCode) {
                                Constant.ApiErrorCodes.sUnAuthorized -> {
                                    pluginContext.getString(R.string.unauthorized_error)
                                }
                                Constant.ApiErrorCodes.sForbidden -> {
                                    pluginContext.getString(R.string.forbidden_url_error)
                                }
                                Constant.ApiErrorCodes.sNotFound -> {
                                    pluginContext.getString(R.string.not_found_url_error)
                                }
                                Constant.ApiErrorCodes.sInternalServerError -> {
                                    pluginContext.getString(R.string.internal_server_error)
                                }
                                else -> {
                                    error ?: pluginContext.getString(R.string.error_msg)
                                }
                            }
                            pluginContext.toast(message)
                        }
                    })
            }
        } else {
            pluginContext.toast(pluginContext.getString(R.string.internet_error))
        }
    }

    private fun removeMarkerFromList(item: MarkerDataModel?) {
        item?.let {
            markersList.remove(it)
            saveMarkerListToPref()
            markerAdapter?.notifyDataSetChanged()
        }
    }

    private fun saveMarkerListToPref() {
        sharedPrefs?.set(Constant.PreferenceKey.sMarkerList, Gson().toJson(markersList))
    }

    private fun removeMarkerFromMap(marker: MarkerDataModel?) {
        marker?.let {
            val item: MapItem? = mapView.rootGroup.items.find { mapItem ->
                mapItem.uid == it.markerID
            }
            mapView.rootGroup.removeItem(item)
        }
    }

    private fun removeLinkLinesFromMap(marker: MarkerDataModel?) {
        marker?.let {
            val data = markerLinkList.find {
                marker.markerID == it.markerId
            }
            data?.let {
                for(link in data.links){
                    val mapGroup = mapView.rootGroup.findMapGroup(pluginContext.getString(R.string.drawing_objects))
                    val item: MapItem? = mapGroup.items.find { mapItem ->
                        mapItem.uid == link.linkId
                    }
                    mapGroup.removeItem(item)
                }
                markerLinkList.remove(it)
            }
        }
    }

    private fun getAllFilesFromAssets(): List<String>? {
        val assetManager = pluginContext.assets
        return assetManager.list("")?.filter { it.endsWith(Constant.TEMPLATE_FORMAT) }
    }

    // Fetch saved settings
    private fun setDataFromPref() {
        etServerUrl?.setText(sharedPrefs?.get(Constant.PreferenceKey.sServerUrl, ""))
        etApiKey?.setText(sharedPrefs?.get(Constant.PreferenceKey.sApiKey, ""))
        svMode.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCalculationMode, false) ?: false
        cbCoverageLayer.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sKmzVisibility, true) ?: true
        cbLinkLines.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sLinkLinesVisibility, true) ?: true
    }

    // Add a layer. Previously KMZ..
    private fun addSingleKMZLayer(layerName: String, filePath: String, bounds: List<Double>) {
        val file = File(filePath)
        synchronized(this@PluginDropDownReceiver) {
            if (singleSiteCloudRFLayer != null) { // remove previous layer if exists.
                singleSiteCloudRFLayer = null
                GLLayerFactory.unregister(GLCloudRFLayer.SPI)
            }
            // create new layer.
            GLLayerFactory.register(GLCloudRFLayer.SPI)
            singleSiteCloudRFLayer = CloudRFLayer(
                pluginContext,
                layerName,
                pluginContext.getString(R.string.layer, layerName),
                file.absolutePath,
                bounds, object : CloudRFLayerListener {
                    override fun delete(layer: CloudRFLayer) {
                        promptDelete(layer)
                    }
                }
            )
        }
        // Add the layer to the map
        singleSiteCloudRFLayer?.let {
            mapView.addLayer(
                RenderStack.MAP_SURFACE_OVERLAYS,
                singleSiteCloudRFLayer
            )
            singleSiteCloudRFLayer?.isVisible = true

            // Pan and zoom to the layer. Can be annoying

            /*ATAKUtilities.scaleToFit(
                mapView, singleSiteCloudRFLayer?.points,
                mapView.width, mapView.height
            )*/
            handleKmzLayerVisibility()

            // Refresh Overlay Manager
            refreshView()
        }
    }

    private fun addKMZLayer(filePath: String, bounds: List<Double>) {
        val file = File(filePath)
        synchronized(this@PluginDropDownReceiver) {
            if (cloudRFLayer != null) { // remove previous layer if exists.
                mapView.removeLayer(RenderStack.MAP_SURFACE_OVERLAYS, cloudRFLayer)
                cloudRFLayer = null
                GLLayerFactory.unregister(GLCloudRFLayer.SPI)
            }
            // create new layer.
            GLLayerFactory.register(GLCloudRFLayer.SPI)
            val layerName = pluginContext.getString(R.string.multisite_layer)
            cloudRFLayer =
                CloudRFLayer(
                    pluginContext,
                    layerName,
                    layerName,
                    file.absolutePath,
                    bounds,
                    object : CloudRFLayerListener {
                        override fun delete(layer: CloudRFLayer) {
                            promptDelete(layer)
                        }
                    })
        }
        // Add the layer to the map
        cloudRFLayer?.let {
            mapView.addLayer(
                RenderStack.MAP_SURFACE_OVERLAYS,
                cloudRFLayer
            )
            cloudRFLayer?.isVisible = true
            handleKmzLayerVisibility()

            // Pan and zoom to the layer
            /*ATAKUtilities.scaleToFit(
                mapView, cloudRFLayer?.points,
                mapView.width, mapView.height
            )*/

            // Refresh Overlay Manager
            refreshView()
        }
    }

    public override fun disposeImpl() {
        try {
            if (singleSiteCloudRFLayer != null) {
                mapView.removeLayer(
                    RenderStack.MAP_SURFACE_OVERLAYS,
                    singleSiteCloudRFLayer
                )
                singleSiteCloudRFLayer = null
            }
            if (cloudRFLayer != null) {
                mapView.removeLayer(
                    RenderStack.MAP_SURFACE_OVERLAYS,
                    cloudRFLayer
                )
                GLLayerFactory.unregister(GLCloudRFLayer.SPI)
            }
            cloudRFLayer = null
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "error", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            SHOW_PLUGIN -> {
                Log.d(TAG, "showing plugin drop down")
                showDropDown(
                    templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this
                )
                // To check custom-type marker when plugin is visible.
                Log.d(TAG, "Group Items: ${mapView.rootGroup.items}")
                Log.d(
                    TAG,
                    "Pref Items: ${sharedPrefs?.get(Constant.PreferenceKey.sMarkerList, null)}"
                )
                try {
                    val templateList: ArrayList<MarkerDataModel>? =
                        Gson().fromJson(
                            sharedPrefs?.get(Constant.PreferenceKey.sMarkerList, null),
                            object : TypeToken<ArrayList<MarkerDataModel>>() {}.type
                        )

                    val commonList = templateList?.filter { marker ->
                        mapView.rootGroup.items.any { items -> marker.markerID == items.uid }
                    }
                    Log.d(TAG, "Group Items: commonList : $commonList")
                } catch (e: java.lang.Exception) {
                    Log.e(TAG, "error", e)
                }

                setDataFromPref()
                Constant.sServerUrl = etServerUrl?.text.toString()
                Constant.sAccessToken = etApiKey?.text.toString()

            }
            GRG_TOGGLE_VISIBILITY, LAYER_VISIBILITY -> {
                Log.d(
                    TAG,
                    "used the custom action to toggle layer visibility on: "
                            + intent
                        .getStringExtra("uid")
                )
                val l: CloudRFLayer? = mapOverlay.findLayer(
                    intent
                        .getStringExtra("uid")
                )
                if (l != null) {
                    l.isVisible = !l.isVisible
                }
                refreshView()
            }
            GRG_DELETE, LAYER_DELETE -> {
                Log.d(
                    TAG,
                    "used the custom action to delete the layer on: "
                            + intent
                        .getStringExtra("uid")
                )
                val l = mapOverlay.findLayer(intent.getStringExtra("uid"))
                if (l != null) {
                    promptDelete(l)
                }

            }
            RADIO_EDIT -> {
                // show dropdown if it is not visible
                if(!this.isVisible) {
                    showDropDown(
                        templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                        HALF_HEIGHT, false, this
                    )
                }

                val id = intent.getStringExtra("uid")
                val item = markersList.find {
                    it.markerID == id
                }
                val position = if (item != null) markersList.indexOf(item) else -1
                Log.d(
                    TAG,
                    "used the custom action to RADIO_EDIT the layer on: $id item:${
                        Gson().toJson(item)
                    } position:$position"
                )

                item?.let { marker ->
                    itemPositionForEdit = position
                    setEditViewVisibility(true)
                    setEditViewData(marker)
                }

                PluginLayoutInflater.inflate(
                    pluginContext,
                    R.layout.setting_layout, null
                )
            }
            RADIO_DELETE -> {
                val id = intent.getStringExtra("uid")
                val item = markersList.find {
                    it.markerID == id
                }
                Log.d(TAG, "used the custom action to RADIO_DELETE the layer on: $id")
                item?.let { marker ->
                    mapView.context.showAlert(pluginContext.getString(R.string.alert_title), "${pluginContext.getString(R.string.delete)} ${marker.markerDetails.template.name}",
                        pluginContext.getString(R.string.yes), pluginContext.getString(R.string.cancel)) {
                        removeMarker(marker)
                    }
                }
            }
            GRG_BRIGHTNESS -> {

            }
            GRG_COLOR -> {

            }
            GRG_TRANSPARENCY -> {

            }
        }

    }

    private fun promptDelete(layer: CloudRFLayer) {
        val builder = AlertDialog.Builder(mapView.context)
        builder.setTitle(pluginContext.getString(R.string.civ_delete_layer))
        builder.setIcon(com.atakmap.app.R.drawable.ic_menu_delete)
        builder.setMessage(
            "${pluginContext.getString(R.string.delete)}${layer.description}${
                pluginContext.getString(
                    R.string.question_mark_symbol
                )
            }"
        )
        builder.setNegativeButton(pluginContext.getString(R.string.cancel), null)
        builder.setPositiveButton(
            pluginContext.getString(R.string.ok_txt)
        ) { _, _ ->
            delete(layer)
            refreshView()
        }
        builder.show()
    }

    private fun delete(layer: CloudRFLayer?) {
        if (layer?.fileUri == null) return

        // remove layer from map
        mapView.removeLayer(
            RenderStack.MAP_SURFACE_OVERLAYS,
            layer
        )
        mapOverlay.PluginListModel()

        val pathsToDelete: ArrayList<String> = ArrayList()
        pathsToDelete.add(layer.fileUri) // kmz img uri.
        val fileName = File(layer.fileUri).name.substringBeforeLast('.', "")
        val kmzFilePath = File(KMZ_FOLDER, "$fileName$KMZ_FILE").absolutePath
        pathsToDelete.add(kmzFilePath) // kmz file uri.
        for (path in pathsToDelete) {
            Log.d(TAG, "Deleting at $path")
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

    /**
     * This method handle visibility of link lines together by it's "Drawing object" Group.
     * */
    private fun handleLinkLineVisibility() {
        val mapGroup =
            mapView.rootGroup.findMapGroup(pluginContext.getString(R.string.drawing_objects))
        mapGroup.visible = cbLinkLines.isChecked
        refreshView()
    }

    /**
     * This method handle visibility of kmz layer together by it's "SOOTHSAYER Layer" Name.
     * */
    private fun handleKmzLayerVisibility() {
        if (mapOverlay.hideAllKmzLayer(pluginContext.getString(R.string.soothsayer_layer), cbCoverageLayer.isChecked)) {
           refreshView()
        }
    }

    private fun refreshView(){
        AtakBroadcast.getInstance().sendBroadcast(Intent(HierarchyListReceiver.REFRESH_HIERARCHY))
    }

    private fun removeMarker(marker:MarkerDataModel){
        // remove marker from list
        removeMarkerFromList(marker)
        // remove marker from map
        removeMarkerFromMap(marker)
        // remove link lines from map if exists for that marker.
        removeLinkLinesFromMap(marker)
    }

    override fun onDropDownSelectionRemoved() {}
    override fun onDropDownVisible(v: Boolean) {}

    override fun onDropDownSizeChanged(width: Double, height: Double) {}
    override fun onDropDownClose() {}

    companion object {
        val TAG: String? = PluginDropDownReceiver::class.java.simpleName
        const val SHOW_PLUGIN = "com.atakmap.android.soothsayer.SHOW_PLUGIN"
        const val LAYER_VISIBILITY = "com.atakmap.android.soothsayer.LAYER_VISIBILITY"
        const val LAYER_DELETE = "com.atakmap.android.soothsayer.LAYER_DELETE"
        const val GRG_DELETE = "com.atakmap.android.grg.DELETE"
        const val GRG_TOGGLE_VISIBILITY = "com.atakmap.android.grg.TOGGLE_VISIBILITY"
        const val GRG_BRIGHTNESS = "com.atakmap.android.grg.BRIGHTNESS"
        const val GRG_COLOR = "com.atakmap.android.grg.COLOR"
        const val GRG_TRANSPARENCY = "com.atakmap.android.grg.TRANSPARENCY"
        const val RADIO_EDIT = "com.atakmap.android.maps.EDIT_DETAILS"
        const val RADIO_DELETE = "com.atakmap.android.soothsayer.RADIO_DELETE"
    }
}


