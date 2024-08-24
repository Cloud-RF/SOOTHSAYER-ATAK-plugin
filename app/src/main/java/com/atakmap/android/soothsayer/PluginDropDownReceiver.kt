package com.atakmap.android.soothsayer

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.*
import androidx.core.widget.addTextChangedListener
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
import com.atakmap.android.soothsayer.models.response.LoginResponse
import com.atakmap.android.soothsayer.models.response.ResponseModel
import com.atakmap.android.soothsayer.models.response.TemplatesResponse
import com.atakmap.android.soothsayer.models.response.TemplatesResponseItem
import com.atakmap.android.soothsayer.network.remote.RetrofitClient
import com.atakmap.android.soothsayer.network.repository.PluginRepository
import com.atakmap.android.soothsayer.plugin.R
import com.atakmap.android.soothsayer.recyclerview.RecyclerViewAdapter
import com.atakmap.android.soothsayer.util.*
import com.atakmap.android.util.SimpleItemSelectedListener
import com.atakmap.coremap.maps.assets.Icon
import com.atakmap.coremap.maps.coords.GeoPoint
import com.atakmap.map.layer.opengl.GLLayerFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class PluginDropDownReceiver (
    mapView: MapView?,
    val pluginContext: Context, private val mapOverlay: PluginMapOverlay
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
    private val loginView = templateView.findViewById<LinearLayout>(R.id.ilLogin)

    private var etLoginServerUrl: EditText? = null
    private var etUsername: EditText? = null
    private var etPassword: EditText? = null
    private var etServerUrl: EditText? = null
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
    private val serverTypes: ArrayList<String> = ArrayList()

    init {
        initViews()
        initListeners()
        initSpotBeam()
    }

    private fun initViews() {
        pluginContext.createAndStoreFiles(getAllFilesFromAssets())
        initSettings()
        initRadioSettingView()
        initSpinner()
        initLoginView()
        initRecyclerview()
    }

    private fun initListeners() {
        // The button below shows settings layout and hides the actual layout
        val btnOpenSettings: ImageView = templateView.findViewById(R.id.ivSettings)
        btnOpenSettings.setOnClickListener {
            // set views with the saved setting values
            setDataFromPref()
            mainLayout.visibility = View.GONE
            settingView.visibility = View.VISIBLE
        }

        val btnSettingLogin = settingView.findViewById<ImageButton>(R.id.btnSettingLogin)
        btnSettingLogin.setOnClickListener {
            setLoginViewVisibility(false)
        }

        val btnsvMode = settingView.findViewById<Switch>(R.id.svMode)
        btnsvMode.setOnClickListener{
            sharedPrefs?.set(Constant.PreferenceKey.sCalculationMode, svMode.isChecked)
        }

        val coverageCB = settingView.findViewById<CheckBox>(R.id.cbKmzLayer)
        coverageCB.setOnClickListener{
            sharedPrefs?.set(Constant.PreferenceKey.sKmzVisibility, cbCoverageLayer.isChecked)
        }

        val linksCB = settingView.findViewById<CheckBox>(R.id.cbLinkLines)
        linksCB.setOnClickListener{
            sharedPrefs?.set(Constant.PreferenceKey.sLinkLinesVisibility, cbLinkLines.isChecked)
        }


        val btnSave = settingView.findViewById<ImageButton>(R.id.btnSave)
        btnSave.setOnClickListener {
            Constant.sServerUrl = etLoginServerUrl?.text.toString()
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

        // open help dialog
        val tvHelp = settingView.findViewById<ImageButton>(R.id.tvHelp)
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
            if (Constant.sAccessToken != "") {
                pluginContext.shortToast("Drag marker(s) to calculate")
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
        etLoginServerUrl = settingView.findViewById(R.id.etLoginServerUrl)
        etLoginServerUrl?.setText(Constant.sServerUrl)

        etUsername = settingView.findViewById(R.id.etUserName)
        etUsername?.setText(Constant.sUsername)
    }

    private fun initLoginView() {
        etLoginServerUrl = loginView.findViewById(R.id.etLoginServerUrl)
        etUsername = loginView.findViewById(R.id.etUserName)

        val server: String? = sharedPrefs?.get(Constant.PreferenceKey.sServerUrl, "https://cloudrf.com").toString()
        val username: String? = sharedPrefs?.get(Constant.PreferenceKey.etUsername, "").toString()
        val apiKey: String? = sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "").toString()

        etUsername?.setText(username)
        etLoginServerUrl?.setText(server)
        Constant.sAccessToken = apiKey.toString()
        Log.d(TAG, "SOOTHSAYER saved server: "+server+" User: "+username+" apiKey: "+apiKey)


        val btnLogin = loginView.findViewById<Button>(R.id.btnLogin)
        btnLogin.setOnClickListener {
            loginUser()
        }
        val btnLoginBack = loginView.findViewById<ImageView>(R.id.ivLoginBack)
        btnLoginBack.setOnClickListener {
            setLoginViewVisibility(true)
            Constant.sServerUrl = etLoginServerUrl?.text.toString()
        }

        etPassword = loginView.findViewById(R.id.etPassword)
        val passwordToggleIcon: ImageView = loginView.findViewById(R.id.ivPasswordToggle)
        passwordToggleIcon.setOnClickListener {
            etPassword?.let {
                it.inputType = if (it.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                }
                passwordToggleIcon.setImageResource(
                    if (it.inputType == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                        R.drawable.ic_eye_open
                    } else {
                        R.drawable.ic_eye_closed
                    }
                )
                it.setSelection(it.text.length) // Move cursor to end
            }
        }
    }

    private fun setLoginViewVisibility(isMoveBack: Boolean,isAfterLogin:Boolean=false) {
        settingView.visibility = if (!isMoveBack || isAfterLogin) View.GONE else View.VISIBLE
        loginView.visibility = if (isMoveBack || isAfterLogin) View.GONE else View.VISIBLE
        mainLayout.visibility = if (isAfterLogin) View.VISIBLE else View.GONE
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
                                (marker.antenna.azi != etAntennaAzimuth.text.toString() && etAntennaAzimuth.text.isNotEmpty())

                    if (isEdit) {
                        //change the marker data
                        marker.transmitter?.let { transmitter ->
                            etRadioHeight.text.toString().toDoubleOrNull()?.let { transmitter.alt = it }
                            etRadioPower.text.toString().toDoubleOrNull()?.let { transmitter.txw = it }
                            etFrequency.text.toString().toDoubleOrNull()?.let { transmitter.frq = it }
                            etBandWidth.text.toString().toDoubleOrNull()?.let { transmitter.bwi = it }
                        }
                        etOutputNoiseFloor.text.toString().toIntOrNull()?.let { marker.output.nf = it }
                        etAntennaAzimuth.text.toString().let { marker.antenna.azi = it }
                        Log.d(TAG, "initRadioSettingView : after update ${markersList[itemPositionForEdit]}")
                        markerAdapter?.notifyDataSetChanged()

                        if(cbLinkLines.isChecked) {
                            // UPDATE MARKER
                            markersList[itemPositionForEdit].markerDetails = marker
                            updateLinkLinesOnMarkerDragging(markersList[itemPositionForEdit])
                        }

                        itemPositionForEdit = -1

                        // Trigger a multisite API call
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
        spotBeamView.visibility = View.GONE
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

    private fun isValidLogin(): Boolean {
        var isValid = true
        val message = when {
            !URLUtil.isValidUrl(etLoginServerUrl?.text.toString()) -> {
                pluginContext.getString(R.string.invalid_url_error)
            }
            etUsername?.text?.trim()?.isEmpty() == true -> {
                pluginContext.getString(R.string.empty_user_name)
            }
            etPassword?.text?.trim()?.isEmpty() == true -> {
                pluginContext.getString(R.string.empty_password)
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
        marker.setMetaBoolean("CLOUDRF", true)
        marker.setMetaString("callsign", selectedMarkerType?.template?.name ?: "Test Marker")
        marker.setMetaString(
            "menu",
            PluginMenuParser.getMenu(pluginContext, "menus/radio_menu.xml")
        )
        marker.title = selectedMarkerType?.template?.name ?: "Test Marker"
        marker.type = mItemType
//        marker.setShowLabel(false)

        // Add custom icon. TODO: custom icons!
        val icon: Bitmap? = if(selectedMarkerType?.customIcon == null) pluginContext.getBitmap(R.drawable.marker_icon_svg) else selectedMarkerType?.customIcon?.base64StringToBitmap()?:pluginContext.getBitmap(R.drawable.marker_icon_svg)
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

                    }
                    MapEvent.ITEM_RELEASE -> {
                        Log.d(TAG, "mapItem : ITEM_RELEASE ")
                    }
                    MapEvent.ITEM_DRAG_DROPPED -> {
                        val latitude = marker.geoPointMetaData.get().latitude
                        val longitude = marker.geoPointMetaData.get().longitude
                        Log.d(
                            "TAGG",
                            "DragDropped latitude: $latitude Longitude: $longitude Marker_id: ${mapItem.uid} actual uid = $uid"
                        )
                        Log.d(
                            "TAGG",
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

                        // Links first. I'm not a slag..
                        item?.let {
                            if(cbLinkLines.isChecked) {
                                updateLinkLinesOnMarkerDragging(item)
                            }
                        }

                        // Multisite API (GPU)
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
//                          // Area API (CPU / GPU)
                            item?.let {
                                // send marker position changed data to server.
                                if(cbCoverageLayer.isChecked) {
                                    sendSingleSiteDataToServer(item.markerDetails)
                                }
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
                    marker.markerDetails.transmitter?.alt ?: 1.0,
                    marker.markerDetails.transmitter?.lat ?: 0.0,
                    marker.markerDetails.transmitter?.lon ?: 0.0,
                   marker.markerDetails.antenna.txg,
                   marker.markerDetails.receiver.rxs
            )


            val linkRequest = LinkRequest(it.markerDetails.antenna,
                it.markerDetails.environment,
                it.markerDetails.model,
                it.markerDetails.network,
                it.markerDetails.output,
                points,
                thisRx,
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

    // Area API request
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

                            val builderSingle = AlertDialog.Builder(mapView.context)
                            builderSingle.setTitle("API error")
                            builderSingle.setMessage(error)
                            builderSingle.setNegativeButton(
                                    pluginContext.getString(R.string.ok_txt)
                            ) { dialog, _ -> dialog.dismiss() }
                            builderSingle.show()
                        }
                    })
            }
        } else {
            pluginContext.toast(pluginContext.getString(R.string.internet_error))
        }
    }

    // Multisite API
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
                            if (error != null) {
                                val builderSingle = AlertDialog.Builder(mapView.context)
                                builderSingle.setTitle("API error")
                                builderSingle.setMessage(error)
                                builderSingle.setNegativeButton(
                                        pluginContext.getString(R.string.ok_txt)
                                ) { dialog, _ -> dialog.dismiss() }
                                builderSingle.show()
                            }
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
        svMode.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCalculationMode, false) ?: false
        cbCoverageLayer.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sKmzVisibility, true) ?: true
        cbLinkLines.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sLinkLinesVisibility, true) ?: true
    }

    // Add a layer. Previously KMZ..
    fun addSingleKMZLayer(layerName: String, filePath: String, bounds: List<Double>) {
        val file = File(filePath)
        synchronized(this@PluginDropDownReceiver) {
            if (singleSiteCloudRFLayer != null) { // remove previous layer if exists.
                singleSiteCloudRFLayer = null
                GLLayerFactory.unregister(GLCloudRFLayer.SPI)
            }

            for (layer in mapView.getLayers(RenderStack.MAP_SURFACE_OVERLAYS)) {
                if (layer.name == "SPOTBEAM") {
                    try {
                        if (layer != null) {
                            mapView.removeLayer(
                                RenderStack.MAP_SURFACE_OVERLAYS,
                                layer
                            )
                        }
                        if (layer != null) {
                            mapView.removeLayer(
                                RenderStack.MAP_SURFACE_OVERLAYS,
                                layer
                            )
                            GLLayerFactory.unregister(GLCloudRFLayer.SPI)
                        }
                    } catch (e: java.lang.Exception) {
                        Log.e("spotbeam", "error", e)
                    }
                }
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

    private fun loginUser(){
        if(isValidLogin()) {
            if (pluginContext.isConnected()) {

                RetrofitClient.BASE_URL = etLoginServerUrl?.text.toString()

                repository.loginUser(
                    etUsername?.text.toString(),
                    etPassword?.text.toString(),
                    object : PluginRepository.ApiCallBacks {
                        override fun onLoading() {
                            Log.d(TAG, "onLoading: user login")
                            pluginContext.shortToast("Logging in to "+etLoginServerUrl?.text.toString()+"..")
                        }

                        override fun onSuccess(response: Any?) {
                            if (response is LoginResponse) {
                                response.apiKey?.let {
                                    /*
                                    Take API key and save it. Also save creds since they work
                                     */

                                    /*
                                    Public service has a UI on different subdomain to API
                                    SOOTHSAYER has both UI & API on same server
                                     */
                                    if(etLoginServerUrl?.text.toString() == "https://cloudrf.com"){
                                        RetrofitClient.BASE_URL = "https://api.cloudrf.com"
                                    }
                                    Log.d(TAG, "SOOTHSAYER API key: "+response.apiKey)
                                    Constant.sAccessToken = it

                                    sharedPrefs?.set(
                                        Constant.PreferenceKey.sApiKey,
                                            response.apiKey
                                    )
                                    sharedPrefs?.set(
                                            Constant.PreferenceKey.sServerUrl,
                                            etLoginServerUrl?.text.toString()
                                    )
                                    sharedPrefs?.set(
                                            Constant.PreferenceKey.etUsername,
                                            etUsername?.text.toString()
                                    )

                                    setLoginViewVisibility(isMoveBack = false, isAfterLogin = true)
                                    Constant.sServerUrl = etServerUrl?.text.toString()
                                    downloadTemplatesFromApi()
                                    Constant.sUsername = etUsername?.text.toString()
                                }
                            }
                        }

                        override fun onFailed(error: String?, responseCode: Int?) {
                            pluginContext.toast(
                                error ?: pluginContext.getString(R.string.error_msg)
                            )
                        }

                    })
            }else{
                pluginContext.toast(pluginContext.getString(R.string.internet_error))
            }
        }
    }

    private fun downloadTemplatesFromApi(){
        repository.downloadTemplates(
            object : PluginRepository.ApiCallBacks {
                override fun onLoading() {
                    Log.d(TAG, "onLoading: downloadTemplatesFromApi")
                    pluginContext.shortToast(pluginContext.getString(R.string.template_downloading))
                }

                override fun onSuccess(response: Any?) {
                    if (response is TemplatesResponse) {
                        Log.d(TAG, "onLoading: fetchTemplateDetail")
                        fetchTemplateDetail(response)
                    }
                }

                override fun onFailed(error: String?, responseCode: Int?) {
                    pluginContext.toast(
                        error ?: pluginContext.getString(R.string.error_msg)
                    )
                }

            })
    }

    private fun fetchTemplateDetail(items: TemplatesResponse){
        if (items.isEmpty()) {
            Log.d(TAG, "onLoading: fetchTemplateDetail no more items")
            return  // when no more items
        }

        val item: TemplatesResponseItem = items.removeAt(0)
        Log.d(TAG, item.name)
        downloadTemplateDetail(item.id, item.name, items)
    }

    private fun downloadTemplateDetail(id:Int, name:String, items: TemplatesResponse){
        repository.downloadTemplateDetail(id,
            object : PluginRepository.ApiCallBacks {
                override fun onLoading() {
                    Log.d(TAG, "Downloading template: $name")
                    //pluginContext.shortToast("Downloading template: $name...")
                }

                override fun onSuccess(response: Any?) {
                    if (response is TemplateDataModel) {
                        Log.d(TAG, "onLoading: fetchTemplateDetail id:$id response : $response")
                        createAndStoreDownloadedFile(response)
                        fetchTemplateDetail(items) // recursive call to this function to download details of other.
                    }
                }

                override fun onFailed(error: String?, responseCode: Int?) {
                    pluginContext.toast(
                        error ?: pluginContext.getString(R.string.error_msg)
                    )
                }

            })
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
                Constant.sServerUrl = etLoginServerUrl?.text.toString()
                Constant.sAccessToken = sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "").toString()

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


    var names = arrayOf("")
    var satellite = Satellite()

    var spotBeamView = templateView.findViewById<LinearLayout>(R.id.sbmainll)

    var resolution = 20

    private fun initSpotBeam() {


        spotBeamView = templateView.findViewById(R.id.sbmainll)
        val sbtopbar = spotBeamView.findViewById<LinearLayout>(R.id.sbtopbar)

        val sbBack = sbtopbar.findViewById<ImageView>(R.id.sbBack)
        sbBack.setOnClickListener {
            setDataFromPref()
            moveBackToMainLayout()
        }

        val btnSpotBeam = templateView.findViewById<ImageButton>(R.id.btnSpotBeam)
        btnSpotBeam.setOnClickListener {
            settingView.visibility = View.GONE
            radioSettingView.visibility = View.GONE
            loginView.visibility = View.GONE
            mainLayout.visibility = View.GONE
            spotBeamView.visibility = View.VISIBLE
        }



        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val timeFormat = SimpleDateFormat("HH:mm:ss")

        val editDate = spotBeamView.findViewById<EditText>(R.id.editDate)
        editDate.setText(dateFormat.format(currentDate))


        val editTime = spotBeamView.findViewById<EditText>(R.id.editTime)
        editTime.setText("12:00:00"); // GS was moving :/ timeFormat.format(currentDate))

        
        val satelliteSearch = spotBeamView.findViewById<AutoCompleteTextView>(R.id.sbSatelliteSearch)

        satelliteSearch.setOnFocusChangeListener { _, b ->
            if (b) satelliteSearch.setText("")
            else if (satelliteSearch.text.length.toString() == "0")
                satelliteSearch.setText("Search Satellites")
        }

        satelliteSearch.addTextChangedListener {
            Satellite.getSats(satelliteSearch.text.toString(), this, RetrofitClient.BASE_URL);
            if (names.isEmpty()) names = arrayOf("")
            val adapter = ArrayAdapter(pluginContext,
                android.R.layout.simple_list_item_1,
                names)
            satelliteSearch.setAdapter(adapter)
            satelliteSearch.threshold = 2
        }

        val resolutionSpinner = spotBeamView.findViewById<Spinner>(R.id.resolutionSpinner);
        val items = arrayOf("Low (20m)", "Medium (10m)", "High (2m)")
        val adapter = ArrayAdapter(pluginContext,
            android.R.layout.simple_spinner_dropdown_item, items)
        resolutionSpinner.adapter = adapter


        resolutionSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                resolution = if (position == 0) 20
                else if (position == 1) 10
                else 2
            }
        }

    }

    fun addSpotBeamAreaMarker() {

        for (marker in mapView.rootGroup.items)
            if (marker.title == "Satellite coverage")
                mapView.rootGroup.removeItem(marker)

        val uid = UUID.randomUUID().toString()
        val location = mapView.centerPoint.get()
        val marker = Marker(location, uid)
        marker.title = "Satellite coverage";

        val icon = pluginContext.getBitmap(R.drawable.spotbeam_marker_icon)
        val outputStream = ByteArrayOutputStream()
        icon?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val b = outputStream.toByteArray()
        val encoded = "base64://" + Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE)
        val markerIconBuilder = Icon.Builder().setImageUri(0, encoded)

        marker.setMetaBoolean("CLOUDRF", true)

        marker.icon = markerIconBuilder.build()

        marker.setMetaBoolean("movable", true)
        mapView.rootGroup.addItem(marker)

        mapView.mapEventDispatcher.addMapItemEventListener(
            marker
        ) { _, mapEvent ->
            when (mapEvent.type) {
                MapEvent.ITEM_DRAG_DROPPED -> {
                    pluginContext.toast("Calculating coverage...");
                    val latitude = marker.geoPointMetaData.get().latitude
                    val longitude = marker.geoPointMetaData.get().longitude

                    val editDate = spotBeamView.findViewById<EditText>(R.id.editDate).text
                    val editTime = spotBeamView.findViewById<EditText>(R.id.editTime).text
                    val dateTime: String = (editDate.toString() + "T" + editTime.toString() + "Z")

                    SpotBeamCall.callAPI(satellite, latitude, longitude, this,
                        sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "").toString(), RetrofitClient.BASE_URL, dateTime);
                }
            }
        }

     }

    fun toast(message: String) {
        pluginContext.toast(message)
    }

    fun drawLine(p1: Array<Double>, p2: Array<Double>, label: Boolean, azi: Double, elev: Double) {
        val line = Polyline(UUID.randomUUID().toString());
        line.toggleMetaData("labels_on", label)

        line.setPoints(arrayOf(GeoPoint(p1[0], p1[1]), GeoPoint(p2[0], p2[1])));
        line.title = "AZIMUTH"
        line.lineLabel = "AZ: " + Math.round(azi*10)/10 + "° EL: " + Math.round(elev*10)/10 + "°"
        line.setLabelTextSize(72, Typeface.DEFAULT)

        mapView.rootGroup.addItem(line)
    }

    fun removeLines() {
        for (it in mapView.rootGroup.items)
            if (it.title == "AZIMUTH")
                mapView.rootGroup.removeItem(it)
    }
}