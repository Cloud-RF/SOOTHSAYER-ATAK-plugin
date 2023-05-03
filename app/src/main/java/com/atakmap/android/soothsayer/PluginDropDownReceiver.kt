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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atak.plugins.impl.PluginLayoutInflater
import com.atakmap.android.soothsayer.layers.CloudRFLayer
import com.atakmap.android.soothsayer.layers.GLCloudRFLayer
import com.atakmap.android.soothsayer.layers.PluginMapOverlay
import com.atakmap.android.soothsayer.models.common.MarkerDataModel
import com.atakmap.android.soothsayer.models.request.MultiSiteTransmitter
import com.atakmap.android.soothsayer.models.request.MultisiteRequest
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.models.response.ResponseModel
import com.atakmap.android.soothsayer.network.remote.RetrofitClient
import com.atakmap.android.soothsayer.network.repository.PluginRepository
import com.atakmap.android.soothsayer.plugin.R
import com.atakmap.android.soothsayer.recyclerview.RecyclerViewAdapter
import com.atakmap.android.soothsayer.util.*
import com.atakmap.android.dropdown.DropDown.OnStateListener
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.hierarchy.HierarchyListReceiver
import com.atakmap.android.importexport.ImportExportMapComponent
import com.atakmap.android.ipc.AtakBroadcast
import com.atakmap.android.maps.MapEvent
import com.atakmap.android.maps.MapItem
import com.atakmap.android.maps.MapView
import com.atakmap.android.maps.MapView.RenderStack
import com.atakmap.android.maps.Marker
import com.atakmap.android.preference.AtakPreferences
import com.atakmap.android.util.SimpleItemSelectedListener
import com.atakmap.coremap.log.Log
import com.atakmap.coremap.maps.assets.Icon
import com.atakmap.map.layer.opengl.GLLayerFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.util.*


class PluginDropDownReceiver(
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
    private val svMode: Switch = settingView.findViewById(R.id.svMode)
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

    init {
        initViews()
        initListeners()
    }

    private fun initViews() {
        pluginContext.createAndStoreFiles(getAllFilesFromAssets())
        initSettings()
        initSpinner()
        initRecyclerview()
    }

    private fun initListeners() {
        // The button bellow shows settings layout and hide the actual layout
        val btnOpenSettings: ImageView = templateView.findViewById(R.id.ivSettings)
        btnOpenSettings.setOnClickListener {
            // set views with the saved setting values
            setDataFromPref()
            if (sharedPrefs?.get(Constant.PreferenceKey.sServerUrl, "")?.isEmpty() == true) {
                etServerUrl?.setText(RetrofitClient.DEFAULT_URL)
            }
            mainLayout.visibility = View.GONE
            settingView.visibility = View.VISIBLE
        }

        // The button bellow shows settings layout and hide the actual layout
        val btnSave = settingView.findViewById<Button>(R.id.btnSave)
        btnSave.setOnClickListener {
            if (isValidSettings()) {
                Constant.sServerUrl = etServerUrl?.text.toString()
                Constant.sAccessToken = etApiKey?.text.toString()
                sharedPrefs?.set(Constant.PreferenceKey.sServerUrl, Constant.sServerUrl)
                sharedPrefs?.set(Constant.PreferenceKey.sApiKey, Constant.sAccessToken)
                sharedPrefs?.set(Constant.PreferenceKey.sCalculationMode, svMode.isChecked)
                moveBackToMainLayout()
            }
        }

        // open help dialog on click of Help view
        val tvHelp = settingView.findViewById<TextView>(R.id.tvHelp)
        tvHelp.setOnClickListener {
            showHelpDialog()
        }

        // The ImageView bellow shows settings layout and hide the actual layout
        val ivBack = settingView.findViewById<ImageView>(R.id.ivBack)
        ivBack.setOnClickListener {
            setDataFromPref()
            moveBackToMainLayout()
        }

        // The button bellow add marker on the map
        val btnAddMarker = templateView.findViewById<Button>(R.id.btnAddMarker)
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
            // remove marker from list
            removeMarkerFromList(it)
            // remove marker from map
            removeMarkerFromMap(it)
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
                    // If user added any other template to that folder then below code will get that template if it is valid one and show in the list.
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
                    // Click was detected, perform click
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

    private fun isValidSettings(): Boolean {
        var isValid = true
        val message = when {
            !URLUtil.isValidUrl(etServerUrl?.text.toString()) -> {
                pluginContext.getString(R.string.invalid_url_error)
            }
            etApiKey?.text?.trim()?.isEmpty() == true -> {
                pluginContext.getString(R.string.empty_api_key)
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
//        marker.type = "a-f-G-U-C-I"
        // m.setMetaBoolean("disableCoordinateOverlay", true); // used if you don't want the coordinate overlay to appear
        marker.setMetaBoolean("readiness", true)
        marker.setMetaBoolean("archive", true)
        marker.setMetaString("how", "h-g-i-g-o")
        marker.setMetaBoolean("editable", true)
        marker.setMetaBoolean("movable", true)
        marker.setMetaBoolean("removable", true)
        marker.setMetaString("entry", "user")
        marker.setMetaString("callsign", selectedMarkerType?.template?.name ?: "Test Marker")
        marker.title = selectedMarkerType?.template?.name ?: "Test Marker"
        marker.type = mItemType

        //Add custom icon
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
                        removeMarkerFromList(item)
                    }
                    MapEvent.ITEM_RELEASE -> {
                        Log.d(TAG, "mapItem : ITEM_RELEASE ")
                    }
                    MapEvent.ITEM_DRAG_DROPPED -> {
//                        val latitude = mapItem.clickPoint.latitude
//                        val longitude = mapItem.clickPoint.longitude
                        val latitude =   marker.geoPointMetaData.get().latitude
                        val longitude =   marker.geoPointMetaData.get().longitude
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
//                        pluginContext.toast("svMode.isChecked :${svMode.isChecked}")
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
                                sendMultiSiteDataToServer(request)
                            }
                        } else {
//                            For single site api
                            item?.let {
                                // send marker position changed data to server.
                                sendSingleSiteDataToServer(item.markerDetails)
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
        }

        Log.d(TAG, "${markersList.size} listData : ${Gson().toJson(markersList)}")
    }

    /**
     * This method is used to send data to server when marker is dragged for single site api.
     */
    private fun sendSingleSiteDataToServer(markerData: TemplateDataModel?) {
        // In case of area api Receiver's lat and lon should be zero.
        if (pluginContext.isConnected()) {
            markerData?.let {
                markerData.receiver.lat = 0.0
                markerData.receiver.lon = 0.0
                repository.sendSingleSiteMarkerData(
                    markerData,
                    object : PluginRepository.ApiCallBacks {
                        override fun onLoading() {
                            pluginContext.toast(pluginContext.getString(R.string.loading_msg))
                        }

                        override fun onSuccess(response: Any?) {
                            Log.d(TAG, "onSuccess called response: ${Gson().toJson(response)}")
                            pluginContext.toast(pluginContext.getString(R.string.success_msg))
                            if (response is ResponseModel) {
                                // download kmz png image
                                repository.downloadFile(response.PNG_WGS84,
                                    FOLDER_PATH,
                                    KMZ_IMAGE.getFileName(),
                                    listener = { isDownloaded, filePath ->
                                        /*Handler(Looper.getMainLooper()).post {
                                            pluginContext.toast("DownloadFile success: $isDownloaded , message: $filePath")
                                        }*/
                                        if (isDownloaded) {
                                            //adding kmz layer using image file
                                            addSingleKMZLayer(
                                                markerData.template.name,
                                                filePath,
                                                response.bounds
                                            )
                                        }
                                    })

                                //Download kmz file
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

    /**
     * This method is used to send data to server when marker is dragged for multi site api.
     */
    private fun sendMultiSiteDataToServer(markerData: MultisiteRequest?) {
        if (pluginContext.isConnected()) {
            markerData?.let {
//                markerData.receiver.lat = 0.0
//                markerData.receiver.lon = 0.0
                repository.sendMultiSiteMarkerData(
                    markerData,
                    object : PluginRepository.ApiCallBacks {
                        override fun onLoading() {
                            pluginContext.toast(pluginContext.getString(R.string.loading_msg))
                        }

                        override fun onSuccess(response: Any?) {
                            Log.d(TAG, "onSuccess called response: ${Gson().toJson(response)}")
                            pluginContext.toast(pluginContext.getString(R.string.success_msg))
                            if (response is ResponseModel) {
                                // download kmz png image
                                repository.downloadFile(response.PNG_WGS84,
                                    FOLDER_PATH,
                                    KMZ_IMAGE.getFileName(),
                                    listener = { isDownloaded, filePath ->
                                        /*Handler(Looper.getMainLooper()).post {
                                            pluginContext.toast("DownloadFile success: $isDownloaded , message: $filePath")
                                        }*/
                                        if (isDownloaded) {
                                            //adding kmz layer using image file
                                            addKMZLayer(filePath, response.bounds)
                                        }
                                    })

                                //Download kmz file
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

    private fun getAllFilesFromAssets(): List<String>? {
        val assetManager = pluginContext.assets
        return assetManager.list("")?.filter { it.endsWith(Constant.TEMPLATE_FORMAT) }
    }

    /**
     * This method is used to get data from local preferences.
     *  */
    private fun setDataFromPref() {
        etServerUrl?.setText(sharedPrefs?.get(Constant.PreferenceKey.sServerUrl, ""))
        etApiKey?.setText(sharedPrefs?.get(Constant.PreferenceKey.sApiKey, ""))
        svMode.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCalculationMode, false) ?: false
    }

    private fun addSingleKMZLayer(layerName: String, filePath: String, bounds: List<Double>) {
        val file = File(filePath)
//        Log.d(TAG, "addKMZLayer: filePath: ${file.absolutePath}")
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
                bounds
            )
        }
        // Add the layer to the map
        singleSiteCloudRFLayer?.let {
            mapView.addLayer(
                RenderStack.MAP_SURFACE_OVERLAYS,
                singleSiteCloudRFLayer
            )
            singleSiteCloudRFLayer?.isVisible = true

            // Pan and zoom to the layer
            /*ATAKUtilities.scaleToFit(
                mapView, singleSiteCloudRFLayer?.points,
                mapView.width, mapView.height
            )*/
            // Refresh Overlay Manager
            AtakBroadcast.getInstance().sendBroadcast(
                Intent(
                    HierarchyListReceiver.REFRESH_HIERARCHY
                )
            )
        }
    }

    private fun addKMZLayer(filePath: String, bounds: List<Double>) {
        val file = File(filePath)
//        Log.d(TAG, "addKMZLayer: filePath: ${file.absolutePath}")
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
                CloudRFLayer(pluginContext, layerName, layerName, file.absolutePath, bounds)
        }
        // Add the layer to the map
        cloudRFLayer?.let {
            mapView.addLayer(
                RenderStack.MAP_SURFACE_OVERLAYS,
                cloudRFLayer
            )
            cloudRFLayer?.isVisible = true

            // Pan and zoom to the layer
            /*ATAKUtilities.scaleToFit(
                mapView, cloudRFLayer?.points,
                mapView.width, mapView.height
            )*/
            // Refresh Overlay Manager
            AtakBroadcast.getInstance().sendBroadcast(
                Intent(
                    HierarchyListReceiver.REFRESH_HIERARCHY
                )
            )
        }
    }

    /**************************** PUBLIC METHODS  */
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

    /**************************** INHERITED METHODS  */
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
                val templateList: ArrayList<MarkerDataModel>? =
                    Gson().fromJson(
                        sharedPrefs?.get(Constant.PreferenceKey.sMarkerList, null),
                        object : TypeToken<ArrayList<MarkerDataModel>>() {}.type
                    )

                val commonList = templateList?.filter { marker ->
                    mapView.rootGroup.items.any { items -> marker.markerID == items.uid }
                }

                Log.d(TAG, "Group Items: commonList : $commonList")
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
                AtakBroadcast.getInstance()
                    .sendBroadcast( Intent(
                        HierarchyListReceiver.REFRESH_HIERARCHY
                    ))
            }
           GRG_DELETE, LAYER_DELETE -> {
                Log.d(
                    TAG,
                    "used the custom action to delete the layer on: "
                            + intent
                        .getStringExtra("uid")
                )
                val l = mapOverlay.findLayer(
                    intent
                        .getStringExtra("uid")
                )
                if (l != null) {
                    promptDelete(l)
                }

           }
            GRG_BRIGHTNESS ->{

            }
            GRG_COLOR ->{

            }
            GRG_TRANSPARENCY ->{

            }
        }

    }

    private fun promptDelete(layer:CloudRFLayer) {
        val context = mapView.context
        val builder = AlertDialog.Builder(context)
        builder.setTitle(com.atakmap.app.R.string.delete_grg)
        builder.setIcon(com.atakmap.app.R.drawable.ic_menu_delete)
        builder.setMessage(
            context.getString(com.atakmap.app.R.string.delete) + layer.metaShape.getMetaString("shapeName", "KMZ Layer")
                    + context.getString(com.atakmap.app.R.string.question_mark_symbol)
        )
        builder.setNegativeButton(com.atakmap.app.R.string.cancel, null)
        builder.setPositiveButton(
            com.atakmap.app.R.string.ok
        ) { dialog, i ->
            mapView.removeLayer(
                RenderStack.MAP_SURFACE_OVERLAYS,
                layer
            )
            mapOverlay.PluginListModel()
            AtakBroadcast.getInstance().sendBroadcast(Intent(ImportExportMapComponent.ACTION_DELETE_DATA)) // not working
            dialog.dismiss()
        }
        builder.show()
    }

    override fun onDropDownSelectionRemoved() {}
    override fun onDropDownVisible(v: Boolean) {}

    override fun onDropDownSizeChanged(width: Double, height: Double) {}
    override fun onDropDownClose() {}

    companion object {
        val TAG: String? = PluginDropDownReceiver::class.java.simpleName
        const val SHOW_PLUGIN = "com.atakmap.android.plugintemplate.SHOW_PLUGIN"
        const val LAYER_VISIBILITY = "com.atakmap.android.plugintemplate.LAYER_VISIBILITY"
        const val LAYER_DELETE = "com.atakmap.android.plugintemplate.LAYER_DELETE"
        const val GRG_DELETE = "com.atakmap.android.grg.DELETE"
        const val GRG_TOGGLE_VISIBILITY = "com.atakmap.android.grg.TOGGLE_VISIBILITY"
        const val GRG_BRIGHTNESS = "com.atakmap.android.grg.BRIGHTNESS"
        const val GRG_COLOR = "com.atakmap.android.grg.COLOR"
        const val GRG_TRANSPARENCY = "com.atakmap.android.grg.TRANSPARENCY"
    }
}
