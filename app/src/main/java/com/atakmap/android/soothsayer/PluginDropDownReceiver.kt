package com.atakmap.android.soothsayer

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atak.plugins.impl.PluginLayoutInflater
import com.atakmap.android.contact.Contact
import com.atakmap.android.contact.Contacts
import com.atakmap.android.dropdown.DropDown.OnStateListener
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.hierarchy.HierarchyListReceiver
import com.atakmap.android.ipc.AtakBroadcast
import com.atakmap.android.maps.MapEvent
import com.atakmap.android.maps.MapEventDispatcher
import com.atakmap.android.maps.MapGroup
import com.atakmap.android.maps.MapView
import com.atakmap.android.maps.Marker
import com.atakmap.android.maps.PointMapItem
import com.atakmap.android.maps.Shape
import com.atakmap.android.preference.AtakPreferences
import com.atakmap.android.soothsayer.TemplateRecyclerViewAdapter.Companion.MAX_ITEMS
import com.atakmap.android.soothsayer.interfaces.CloudRFLayerListener
import com.atakmap.android.soothsayer.interfaces.CustomPolygonInterface
import com.atakmap.android.soothsayer.layers.CloudRFLayer
import com.atakmap.android.soothsayer.layers.GLCloudRFLayer
import com.atakmap.android.soothsayer.layers.PluginMapOverlay
import com.atakmap.android.soothsayer.models.common.CoOptedMarkerSettings
import com.atakmap.android.soothsayer.models.common.MarkerDataModel
import com.atakmap.android.soothsayer.models.linksmodel.LinkDataModel
import com.atakmap.android.soothsayer.models.linksmodel.LinkResponse
import com.atakmap.android.soothsayer.models.request.MultisiteRequest
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.models.response.LoginResponse
import com.atakmap.android.soothsayer.models.response.ResponseModel
import com.atakmap.android.soothsayer.models.response.TemplatesResponse
import com.atakmap.android.soothsayer.models.response.TemplatesResponseItem
import com.atakmap.android.soothsayer.network.remote.RetrofitClient
import com.atakmap.android.soothsayer.network.repository.PluginRepository
import com.atakmap.android.soothsayer.plugin.R
import com.atakmap.android.soothsayer.recyclerview.CoOptAdapter
import com.atakmap.android.soothsayer.recyclerview.RecyclerViewAdapter
import com.atakmap.android.soothsayer.util.BestSiteManager
import com.atakmap.android.soothsayer.util.CalculationManager
import com.atakmap.android.soothsayer.util.Constant
import com.atakmap.android.soothsayer.util.FOLDER_PATH
import com.atakmap.android.soothsayer.util.PNG_IMAGE
import com.atakmap.android.soothsayer.util.addCustomMarker
import com.atakmap.android.soothsayer.util.createAndStoreDownloadedFile
import com.atakmap.android.soothsayer.util.createAndStoreFiles
import com.atakmap.android.soothsayer.util.delete
import com.atakmap.android.soothsayer.util.deleteFilesMatchingTemplates
import com.atakmap.android.soothsayer.util.drawLinksForResponse
import com.atakmap.android.soothsayer.util.getAllAvailableMarkers
import com.atakmap.android.soothsayer.util.getAllFilesFromAssets
import com.atakmap.android.soothsayer.util.getBitmap
import com.atakmap.android.soothsayer.util.getFileName
import com.atakmap.android.soothsayer.util.getModifiedMarker
import com.atakmap.android.soothsayer.util.getModifiedReceiver
import com.atakmap.android.soothsayer.util.getSettingTemplateListFromPref
import com.atakmap.android.soothsayer.util.getTemplatesFromFolder
import com.atakmap.android.soothsayer.util.handleShowPlugin
import com.atakmap.android.soothsayer.util.isConnected
import com.atakmap.android.soothsayer.util.removeLinkLinesFromMap
import com.atakmap.android.soothsayer.util.removeMarkerFromMap
import com.atakmap.android.soothsayer.util.runCoOptUpdate
import com.atakmap.android.soothsayer.util.saveMarkerListToPref
import com.atakmap.android.soothsayer.util.saveSettingTemplateListToPref
import com.atakmap.android.soothsayer.util.setSpannableText
import com.atakmap.android.soothsayer.util.shortToast
import com.atakmap.android.soothsayer.util.showAlert
import com.atakmap.android.soothsayer.util.sortMarkersWithCheckedFirst
import com.atakmap.android.soothsayer.util.toBase64String
import com.atakmap.android.soothsayer.util.toLinkDataModel
import com.atakmap.android.soothsayer.util.toast
import com.atakmap.android.util.SimpleItemSelectedListener
import com.atakmap.coremap.maps.assets.Icon
import com.atakmap.map.layer.opengl.GLLayerFactory
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID


@RequiresApi(Build.VERSION_CODES.O)
class PluginDropDownReceiver(
    mapView: MapView?,
    val pluginContext: Context, private val mapOverlay: PluginMapOverlay
) : DropDownReceiver(mapView), OnStateListener, Contacts.OnContactsChangedListener, MapEventDispatcher.MapEventDispatchListener{
    // Remember to use the PluginLayoutInflater if you are actually inflating a custom view.
    private val templateView: View = PluginLayoutInflater.inflate(
        pluginContext,
        R.layout.main_layout, null
    )
    private val mainLayout: LinearLayout = templateView.findViewById(R.id.llMain)
    private val settingView = templateView.findViewById<LinearLayout>(R.id.ilSettings)
    private val radioSettingView = templateView.findViewById<LinearLayout>(R.id.ilRadioSetting)
    private val coOptView: View = templateView.findViewById(R.id.ilCoOpt)

    private val settingsCooptView = templateView.findViewById<LinearLayout>(R.id.settingsCoOptLayout)
    private val settingsLayersView = templateView.findViewById<LinearLayout>(R.id.settingsLayersLayout)
    private val settingsOptionsView = templateView.findViewById<LinearLayout>(R.id.settingsOptionsLayout)
    private val colourPickerView = templateView.findViewById<LinearLayout>(R.id.colourPickerLayout)
    private val templatesMenuView = templateView.findViewById<LinearLayout>(R.id.templatesMenuLayout)
    private val pickIconMenuView = templateView.findViewById<LinearLayout>(R.id.pickIconLayout)
    private val newTemplateMenuView = templateView.findViewById<LinearLayout>(R.id.newTemplateMenuLayout)
    private val svMode: Switch = settingsLayersView.findViewById(R.id.svMode)
    private val cbCoverageLayer: CheckBox = settingsLayersView.findViewById(R.id.cbKmzLayer)
    private val cbLinkLines: CheckBox = settingsLayersView.findViewById(R.id.cbLinkLines)
    private val cbCoOptTimeRefresh: CheckBox = settingsCooptView.findViewById(R.id.cbCoOptTimeRefresh)
    private val etCoOptTimeInterval: EditText = settingsCooptView.findViewById(R.id.etCoOptTimeInterval)
    private val cbCoOptDistanceRefresh: CheckBox = settingsCooptView.findViewById(R.id.cbCoOptDistanceRefresh)
    private val etCoOptDistanceThreshold: EditText = settingsCooptView.findViewById(R.id.etCoOptDistanceThreshold)
    private val loginView = templateView.findViewById<LinearLayout>(R.id.ilLogin)
    private var templateRecyclerView: RecyclerView? = null
    private val cbSelectAllTemplates = templatesMenuView.findViewById<CheckBox>(R.id.cbTemplatesAll)
    private val spinner: Spinner = templateView.findViewById(R.id.spTemplate)

    private var etLoginServerUrl: EditText? = null
    private var etUsername: EditText? = null
    private var etPassword: EditText? = null
    private var etServerUrl: EditText? = null
    private var markersList: ArrayList<MarkerDataModel> = ArrayList()
    private var settingTemplateList: MutableList<MutableTuple<TemplateDataModel, Boolean, String?>> = mutableListOf()
    private var selectedMarkerType: TemplateDataModel? = null
    private val templateItems: ArrayList<TemplateDataModel> = ArrayList()
    private var markerAdapter: RecyclerViewAdapter? = null
    private var templateAdapter: TemplateRecyclerViewAdapter? = null
    private val mItemType: String = "custom-type"
    private val repository by lazy { PluginRepository.getInstance() }
    private var sharedPrefs: AtakPreferences? = AtakPreferences(mapView?.context)
    private var cloudRFLayer: CloudRFLayer? = null
    private var singleSiteCloudRFLayer: CloudRFLayer? = null
    private var markerLinkList: ArrayList<LinkDataModel> = ArrayList()
    private var lineGroup: MapGroup? = null
    private var itemPositionForEdit: Int = -1
    private val serverTypes: ArrayList<String> = ArrayList()
    private var allContacts: MutableList<Contact> = mutableListOf()
    private val coOptedMarkers = HashMap<String, CoOptedMarkerSettings>()
    private val trackingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var trackingRunnable: Runnable? = null
    private var preImportTemplates: ArrayList<TemplateDataModel> = ArrayList()
    private var spinnerAdapter: ArrayAdapter<TemplateDataModel>?=null
    private val calcManager by lazy {
        CalculationManager(pluginContext, sharedPrefs, mapView, markersList, this)
    }
    private val bestSiteManager by lazy {
        BestSiteManager(pluginContext, repository, this)
    }
    private var settingsLinksController: SettingsLinksController? = null
    private var templateMenuController: TemplateMenuController? = null

    // create a single main-thread handler
    private val mainHandler = Handler(Looper.getMainLooper())

    private var filePickerReceiverRegistered = false

    private val filePickedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != FilePickerActivity.INTENT_ACTION) return

            val fileName = intent.getStringExtra(FilePickerActivity.FILE_NAME) ?: return
            val templates = intent.getSerializableExtra(FilePickerActivity.JSON_LIST) as ArrayList<TemplateDataModel>
                ?: return
            val someInvalid = intent.getBooleanExtra(FilePickerActivity.SOME_INVALID, false)

            // update UI on main thread
            mainHandler.post {
                newTemplateMenuView.findViewById<TextView>(R.id.tvNewTemplateFileName).text = fileName
                preImportTemplates = templates
                templateMenuController?.setPreImportTemplates(templates)
                if (someInvalid) pluginContext.toast("Some templates were invalid, skipping…")
            }
        }
    }

    // Initialise views, listeners and handle map clicks
    init {
        if (!filePickerReceiverRegistered) {
            pluginContext.registerReceiver(
                filePickedReceiver,
                IntentFilter(FilePickerActivity.INTENT_ACTION),
                Context.RECEIVER_EXPORTED
            )
            filePickerReceiverRegistered = true
        }

        initViews()
        initListeners()
        initSpotBeam()
        Contacts.getInstance().addListener(this)
        onContactsSizeChange(null)
        // Register for map events to capture marker taps
        mapView?.mapEventDispatcher?.addMapEventListener(MapEvent.ITEM_CLICK, this)
    }

    private fun initViews() {
        //stopping to reload the template again and again from the asset folder.
        //val isLoadedFirstTime = sharedPrefs?.get(Constant.PreferenceKey.sTemplatesLoadedFromAssests, true)
       //if(isLoadedFirstTime?:true) {
           pluginContext.createAndStoreFiles(pluginContext.getAllFilesFromAssets())
           //sharedPrefs?.set(Constant.PreferenceKey.sTemplatesLoadedFromAssests, false)
       //}
        initSettings()
        initRadioSettingView()
        initTemplateSpinner()
        initMegapixelSpinner()
        initLoginView()
        initRecyclerview()
        initSettingRecyclerview()
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode", "ImplicitSamInstance")
    private fun initListeners() {
        val btnOpenSettings: ImageView = templateView.findViewById(R.id.ivSettings)
        btnOpenSettings.setOnClickListener {
            setDataFromPref()
            mainLayout.visibility = View.GONE
            settingView.visibility = View.VISIBLE
        }

        val btnSettingLogin = settingView.findViewById<ImageButton>(R.id.btnSettingLogin)
        btnSettingLogin.setOnClickListener {
            setLoginViewVisibility(false)
        }

        val btnsvMode = settingsLayersView.findViewById<Switch>(R.id.svMode)
        btnsvMode.setOnClickListener{
            sharedPrefs?.set(Constant.PreferenceKey.sCalculationMode, svMode.isChecked)
        }

        val coverageCB = settingsLayersView.findViewById<CheckBox>(R.id.cbKmzLayer)
        coverageCB.setOnClickListener{
            sharedPrefs?.set(Constant.PreferenceKey.sKmzVisibility, cbCoverageLayer.isChecked)
        }

        val linksCB = settingsLayersView.findViewById<CheckBox>(R.id.cbLinkLines)
        linksCB.setOnClickListener{
            sharedPrefs?.set(Constant.PreferenceKey.sLinkLinesVisibility, cbLinkLines.isChecked)
        }

        val coOptTimeRefreshCB = settingsCooptView.findViewById<CheckBox>(R.id.cbCoOptTimeRefresh)
        coOptTimeRefreshCB.setOnClickListener{
            sharedPrefs?.set(Constant.PreferenceKey.sCoOptTimeRefreshEnabled, cbCoOptTimeRefresh.isChecked)
        }

        val coOptTimeIntervalET = settingsCooptView.findViewById<EditText>(R.id.etCoOptTimeInterval)
        coOptTimeIntervalET.addTextChangedListener {
            val value = it.toString().toLongOrNull() ?: 60L
            sharedPrefs?.set(Constant.PreferenceKey.sCoOptTimeRefreshInterval, value)
        }

        val coOptDistanceRefreshCB = settingsCooptView.findViewById<CheckBox>(R.id.cbCoOptDistanceRefresh)
        coOptDistanceRefreshCB.setOnClickListener{
            sharedPrefs?.set(Constant.PreferenceKey.sCoOptDistanceRefreshEnabled, cbCoOptDistanceRefresh.isChecked)
        }

        val coOptDistanceThresholdET = settingsCooptView.findViewById<EditText>(R.id.etCoOptDistanceThreshold)
        coOptDistanceThresholdET.addTextChangedListener {
            val value = it.toString().toDoubleOrNull() ?: 100.0
            sharedPrefs?.set(Constant.PreferenceKey.sCoOptDistanceRefreshThreshold, value)
        }

        val tvHelp = settingView.findViewById<ImageButton>(R.id.tvHelp)
        tvHelp.setOnClickListener {
            showHelpDialog()
        }

        val ivBack = settingView.findViewById<ImageView>(R.id.ivBack)
        ivBack.setOnClickListener {
            setDataFromPref()
            moveBackToMainLayout()
        }

        val btnAddMarker = templateView.findViewById<ImageButton>(R.id.btnAddMarker)
        btnAddMarker.setOnClickListener {
            if (Constant.sAccessToken != "") {
                pluginContext.shortToast("Drag marker(s) or click play to calculate")
                addCustomMarker()
            } else {
                pluginContext.toast(pluginContext.getString(R.string.marker_error))
            }
        }
        val btnAddPolygon = templateView.findViewById<ImageButton>(R.id.btnAddPolygon)
        val btnBestSiteAnalysis = templateView.findViewById<ImageButton>(R.id.btnBestSiteAnalysis)
        btnBestSiteAnalysis.setOnClickListener {
            btnBestSiteAnalysis.visibility = View.GONE
            bestSiteManager.performBestSiteAnalysis(selectedMarkerType)
        }
        btnAddPolygon.setOnClickListener {
            if (Constant.sAccessToken != "") {
                pluginContext.shortToast("Draw a polygon for the study area")
                CustomPolygonTool.createPolygon(object: CustomPolygonInterface{
                    override fun onPolygonDrawn(polygon: Shape) {
                        btnBestSiteAnalysis.visibility = View.VISIBLE
                    }
                })
            }
        }

        templateView.findViewById<ImageButton>(R.id.coOptButton).setOnClickListener {
            showCoOptView(true)
        }
        templateView.findViewById<ImageButton>(R.id.stopCoOptButton).setOnClickListener {
            stopTrackingLoop()
        }

        val btnOpenCooptView = settingView.findViewById<Button>(R.id.btnOpenCooptSettings)
        btnOpenCooptView.setOnClickListener {
            settingView.visibility = View.GONE
            settingsCooptView.visibility = View.VISIBLE
        }

        val btnOpenLayersSettings = settingView.findViewById<Button>(R.id.btnOpenLayersSettings)
        btnOpenLayersSettings.setOnClickListener {
            settingView.visibility = View.GONE
            settingsLayersView.visibility = View.VISIBLE
        }

        val btnOpenOptionsSettings = settingView.findViewById<Button>(R.id.btnOpenLinksSettings)
        btnOpenOptionsSettings.setOnClickListener {
            settingView.visibility = View.GONE
            settingsOptionsView.visibility = View.VISIBLE
        }

        val btnCooptBack = settingsCooptView.findViewById<ImageView>(R.id.coOptBack)
        btnCooptBack.setOnClickListener {
            settingView.visibility = View.VISIBLE;
            settingsCooptView.visibility = View.GONE;
        }

        val btnLayersBack = settingsLayersView.findViewById<ImageView>(R.id.layersBack)
        btnLayersBack.setOnClickListener {
            settingView.visibility = View.VISIBLE;
            settingsLayersView.visibility = View.GONE;
        }

        val btnOptionsBack = settingsOptionsView.findViewById<ImageView>(R.id.optionsBack)
        btnOptionsBack.setOnClickListener {
            settingView.visibility = View.VISIBLE;
            settingsOptionsView.visibility = View.GONE;
        }

        initSettingLinkController()

        initTemplateMenuController()
    }

    private fun initSettingLinkController(){
        // initilaize the settingink controller
        settingsLinksController = SettingsLinksController(
            pluginContext,
            settingsOptionsView,
            colourPickerView
        )
    }

    private fun initTemplateMenuController(){
        templateMenuController = TemplateMenuController(
            mapView,
            pluginContext,
            settingView,
            templatesMenuView,
            newTemplateMenuView,
            pickIconMenuView,
            cbSelectAllTemplates,
            templateRecyclerView,
            templateAdapter,
            templateItems,
            settingTemplateList,
            sharedPrefs,
            onDeleteTemplate = {deleteSelectedTemplates()}
        )
    }

    // List of radio templates. They can be selected to edit settings etc
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

    // List of radio templates. They can be imported or deleted.
    private fun initSettingRecyclerview() {
        settingTemplateList = sharedPrefs.getSettingTemplateListFromPref()?:getTemplatesForSettingView()
        templateRecyclerView = templatesMenuView.findViewById(R.id.templatesRecyclerView)
        templateAdapter = TemplateRecyclerViewAdapter(settingTemplateList, onItemSelected = { position, template ->
            // Always refresh select all checkbox based on list state
            cbSelectAllTemplates.setOnCheckedChangeListener(null) // detach
            cbSelectAllTemplates.isChecked = settingTemplateList.all { it.second }
            cbSelectAllTemplates.setOnCheckedChangeListener { _, isChecked -> // reattach
                templateAdapter?.selectAll(isChecked)
            }
        })
        templateRecyclerView?.adapter = templateAdapter
        templateRecyclerView?.layoutManager = LinearLayoutManager(templateRecyclerView?.context)
        sharedPrefs?.saveSettingTemplateListToPref(settingTemplateList)
    }

    private fun getTemplatesForSettingView():MutableList<MutableTuple<TemplateDataModel, Boolean, String?>>{
        val tupleList: MutableList<MutableTuple<TemplateDataModel, Boolean, String?>> = mutableListOf()
        // we can remove limit if required
        for (template in getTemplatesFromFolder().take(MAX_ITEMS)) {
            val icon = pluginContext.getBitmap(R.drawable.marker_icon_svg)
            val tuple = MutableTuple(template, false, icon?.toBase64String())
            tupleList.add(tuple)
        }
        return tupleList
    }

    private fun deleteSelectedTemplates(){
        val toRemove = mutableListOf<Int>()
        val toRemoveFromFolder: ArrayList<TemplateDataModel> = ArrayList()

        settingTemplateList.forEachIndexed { index, item ->
            if (item.second) {
                toRemove.add(index)
                toRemoveFromFolder.add(item.first)
            }
        }

        // remove in reverse order so indices don’t shift
        for (pos in toRemove.asReversed()) {
            settingTemplateList.removeAt(pos)
            templateAdapter?.notifyItemRemoved(pos)
        }

        // do heavy file deletion in background thread
        Thread {
            val deletedCount = toRemoveFromFolder.deleteFilesMatchingTemplates()
            val namesToRemove = toRemoveFromFolder.map { it.template.name }.toHashSet()
            templateItems.removeAll { it.template.name in namesToRemove }

//            // back to UI thread
            mainHandler.post {
                spinnerAdapter?.notifyDataSetChanged()
            }
        }.start()
        toRemoveFromFolder.deleteFilesMatchingTemplates()
    }

    private fun initTemplateSpinner() {
        templateItems.apply {
            clear()
            addAll(getTemplatesFromFolder().filter { it.template != null }) // only picking valid templates.
        }

        spinnerAdapter = object :
            ArrayAdapter<TemplateDataModel>(
                pluginContext,
                R.layout.spinner_item_layout,
                templateItems
            ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as TextView
                val item: TemplateDataModel? = getItem(position)
                textView.text = if (item?.template != null) {
                     item.template.name
                }else {
                    ""
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
                textView.text = if (item?.template != null) {
                    item.template.name
                }else{
                    ""
                }
                return textView
            }
        }
        spinnerAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter
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
                    Log.d(TAG, "extraTemplates : ACTION_DOWN clicked")
                    val extraTemplates = getTemplatesFromFolder()
                    if (extraTemplates.isEmpty()) {
//                        pluginContext.createAndStoreFiles(getAllFilesFromAssets())
                        templateItems.clear()
                        spinnerAdapter?.notifyDataSetChanged()
//                        templateItems.addAll(getTemplatesFromFolder())
                    } else {
                        if (extraTemplates.size != templateItems.size) {
                            Log.d(TAG, "extraTemplates : ${extraTemplates.size}")
                            spinnerAdapter.let { adapter ->
                                if (adapter is ArrayAdapter<*>) {
                                    templateItems.clear()
                                    templateItems.addAll(extraTemplates)
                                    Log.d(TAG, "extraTemplates templateItems : ${templateItems.size}")
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    spinner.performClick()
                }
            }
            false
        }
    }

    private fun initMegapixelSpinner(){
        val mpSpinner = settingsLayersView.findViewById<Spinner>(R.id.megapixelSpinner)
        val items = arrayOf("Low res (0.25MP)", "Mid res (1.0MP)", "High res (4.0MP)")
        val adapter = ArrayAdapter(pluginContext,
                android.R.layout.simple_spinner_dropdown_item, items)
        mpSpinner.adapter = adapter

        mpSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Kotlin has a when statement which is painful.
                if(position==0){
                    calcManager.setMegaPixel(0.25)
                }
                if(position==1){
                    calcManager.setMegaPixel(1.0)
                }
                if(position==2){
                    calcManager.setMegaPixel(4.0)
                }
            }
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

        val server: String? = sharedPrefs?.get(Constant.PreferenceKey.sServerUrl, "https://api.cloudrf.com")
        val username: String? = sharedPrefs?.get(Constant.PreferenceKey.etUsername, "")
        val apiKey: String? = sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "")

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
        etPassword?.setText(sharedPrefs?.get(Constant.PreferenceKey.etPassword, ""))
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
                it.setSelection(it.text.length)
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
                    val markerDataModel = markersList[itemPositionForEdit]
                    val marker = markerDataModel.markerDetails
                    Log.d(TAG, "initRadioSettingView : marker : $marker \nbefore update ${markersList[itemPositionForEdit]}")
                    val isEdit =
                        (marker.transmitter?.alt.toString() != etRadioHeight.text.toString() && etRadioHeight.text.isNotEmpty()) ||
                                (marker.transmitter?.txw.toString() != etRadioPower.text.toString() && etRadioPower.text.isNotEmpty()) ||
                                (marker.transmitter?.frq.toString() != etFrequency.text.toString() && etFrequency.text.isNotEmpty()) ||
                                (marker.transmitter?.bwi.toString() != etBandWidth.text.toString() && etBandWidth.text.isNotEmpty()) ||
                                (marker.output.nf.toString() != etOutputNoiseFloor.text.toString() && etOutputNoiseFloor.text.isNotEmpty()) ||
                                (marker.antenna.azi != etAntennaAzimuth.text.toString() && etAntennaAzimuth.text.isNotEmpty())

                    if (isEdit) {
                        marker.transmitter?.let { transmitter ->
                            etRadioHeight.text.toString().toDoubleOrNull()?.let { transmitter.alt = it }
                            etRadioPower.text.toString().toDoubleOrNull()?.let { transmitter.txw = it }
                            etFrequency.text.toString().toDoubleOrNull()?.let { transmitter.frq = it }
                            etBandWidth.text.toString().toDoubleOrNull()?.let { transmitter.bwi = it }
                        }
                        etOutputNoiseFloor.text.toString().let { marker.output.nf = it }

                        etAntennaAzimuth.text.toString().let { marker.antenna.azi = it }
                        Log.d(TAG, "initRadioSettingView : after update ${markersList[itemPositionForEdit]}")
                        markerAdapter?.notifyDataSetChanged()

                        itemPositionForEdit = -1
                    }
                }
                setEditViewVisibility(false)
            }
        }
    }

    private fun showHelpDialog() {
        mapView.context.showAlert(pluginContext.getString(R.string.help_title),
            pluginContext.getString(R.string.help_msg),
            positiveText = pluginContext.getString(R.string.ok_txt))
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

    private fun setEditViewData(item: MarkerDataModel) {
        val title = pluginContext.getString(R.string.radio_settings, item.markerDetails.template.name).setSpannableText()

        val radioSettingView = radioSettingView
        val transmitter = item.markerDetails.transmitter
        val antenna = item.markerDetails.antenna

        with(radioSettingView) {
            findViewById<TextView>(R.id.tvRadioTitle).text = title
            findViewById<EditText>(R.id.etRadioHeight).setText("${transmitter?.alt ?: ""}")
            findViewById<EditText>(R.id.etRadioPower).setText("${transmitter?.txw ?: ""}")
            findViewById<EditText>(R.id.etAntennaAzimuth).setText(antenna.azi) // string: 0 OR 0,90,180,270
            findViewById<EditText>(R.id.etFrequency).setText("${transmitter?.frq ?: ""}")
            findViewById<EditText>(R.id.etBandWidth).setText("${transmitter?.bwi ?: ""}")
            findViewById<EditText>(R.id.etOutputNoiseFloor).setText(item.markerDetails.output.nf) // string: database OR -100
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
        mapView.addCustomMarker(
            context = pluginContext,
            selectedMarkerType = selectedMarkerType,
            mItemType = mItemType,
            markersList = markersList,
            sharedPrefs = sharedPrefs,
            onItemInserted = {index->
                markerAdapter?.notifyItemInserted(index)
            },
            onItemChanged ={index ->
                markerAdapter?.notifyItemChanged(index)
            },
            onCalculate = { item -> calculate(item) },
            onRemoveMarker = { item -> removeMarkerFromList(item) }
        )
    }

    private fun getLinksBetween(marker: MarkerDataModel?) {
       // Log.d(TAG, "getLinksBetween: "+marker.toString())
        marker?.let {
            val linkDataModel = it.toLinkDataModel(markersList)

            if (markerLinkList.isEmpty()) {
                markerLinkList.add(linkDataModel)
            }

            repository.getLinks(linkDataModel.linkRequest,
                object : PluginRepository.ApiCallBacks {
                    override fun onLoading() {
                    }

                    override fun onSuccess(response: Any?) {
                        linkDataModel.linkResponse = response as LinkResponse
                        markerLinkList.add(linkDataModel)
                        mapView.drawLinksForResponse(
                            pluginContext,
                            linkDataModel.linkRequest.transmitter,
                            linkDataModel.linkResponse,
                            settingsLinksController?.linkUnits ?: "dB",
                            lineGroup,
                            linkDataModel.links,
                            markerLinkList,
                            ::getLineColour,
                            ::handleLinkLineVisibility
                        )
                    }

                    override fun onFailed(error: String?, responseCode: Int?) {
                        stopTrackingLoop()
                        pluginContext.toast("Link error: $error")
                    }

                })

        }
    }

    private fun getLineColour(db: Double): Int? = settingsLinksController?.getLineColour(db)

    fun updateLinkLines(markerItem: MarkerDataModel?){

        // many to many now ;)
        markersList.forEach{
            mapView.removeLinkLinesFromMap(pluginContext,it)
            getLinksBetween(it)
        }

    }

    fun showHidePlayBtn(){
        if(templateView.findViewById<ImageButton>(R.id.btnPlayBtn).visibility == View.VISIBLE){
            templateView.findViewById<ImageButton>(R.id.btnPlayBtn).visibility = View.GONE;
            templateView.findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE;
        }else{
            templateView.findViewById<ImageButton>(R.id.btnPlayBtn).visibility = View.VISIBLE;
            templateView.findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE;
        }

    }
    // The Area API simulates one transmitter only and can use a CPU or a GPU
    fun sendSingleSiteDataToServer(marker: TemplateDataModel?) {
        if (pluginContext.isConnected()) {
            marker?.let {
                val markerData = marker.getModifiedMarker()

                markerData.receiver = (marker.receiver).getModifiedReceiver()
                repository.sendSingleSiteMarkerData(
                    markerData,
                    object : PluginRepository.ApiCallBacks {
                        override fun onLoading() {
                        }

                        override fun onSuccess(response: Any?) {
                            showHidePlayBtn();
                            if (response is ResponseModel) {

                                // Fetch the PNG image from the JSON response and create a layer using the bounds metadata
                                repository.downloadFile(response.PNG_WGS84,
                                    FOLDER_PATH,
                                    PNG_IMAGE.getFileName(),
                                    listener = { isDownloaded, filePath ->
                                        if (isDownloaded) {
                                            addLayer(filePath, response.bounds)
                                            /*addSingleLayer(
                                                markerData.template.name,
                                                filePath,
                                                response.bounds
                                            )*/
                                        }
                                    })
                            }
                        }
                        // The API will return an error for bad inputs like a transmitter inside a hill instead of atop it
                        override fun onFailed(error: String?, responseCode: Int?) {
                            showHidePlayBtn()
                            mapView.context.showAlert("API error", error, positiveText = pluginContext.getString(R.string.ok_txt))
                        }
                    })
            }
        } else {
            pluginContext.toast(pluginContext.getString(R.string.internet_error))
        }
    }

    fun sendMultiSiteDataToServer(markerData: MultisiteRequest?) {
        if (pluginContext.isConnected()) {
            markerData?.let {
                repository.sendMultiSiteMarkerData(
                    markerData,
                    object : PluginRepository.ApiCallBacks {
                        override fun onLoading() {

                        }
                        override fun onSuccess(response: Any?) {
                            showHidePlayBtn();
                            if (response is ResponseModel) {
                                /*
                                Fetch the PNG image from the JSON response and create a layer using the bounds metadata
                                 */
                                repository.downloadFile(response.PNG_WGS84,
                                    FOLDER_PATH,
                                    PNG_IMAGE.getFileName(),
                                    listener = { isDownloaded, filePath ->
                                        if (isDownloaded) {
                                            addLayer(filePath, response.bounds)
                                        }
                                    })
                            }
                        }

                        override fun onFailed(error: String?, responseCode: Int?) {
                            showHidePlayBtn();
                            stopTrackingLoop()
                            if (error != null) {
                                mapView.context.showAlert("API error",error, positiveText = pluginContext.getString(R.string.ok_txt))
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
            sharedPrefs.saveMarkerListToPref(markersList)
            markerAdapter?.notifyDataSetChanged()
        }
    }

    private fun setDataFromPref() {
        svMode.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCalculationMode, true) ?: true
        cbCoverageLayer.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sKmzVisibility, true) ?: true
        cbLinkLines.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sLinkLinesVisibility, true) ?: true
        cbCoOptTimeRefresh.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshEnabled, false) ?: false
        etCoOptTimeInterval.setText((sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshInterval, 30L) ?: 30L).toString())
        cbCoOptDistanceRefresh.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCoOptDistanceRefreshEnabled, true) ?: true
        etCoOptDistanceThreshold.setText((sharedPrefs?.get(Constant.PreferenceKey.sCoOptDistanceRefreshThreshold, 100.0) ?: 100.0).toString())
    }

    fun addSingleLayer(layerName: String, filePath: String, bounds: List<Double>) {
        val file = File(filePath)
        synchronized(this@PluginDropDownReceiver) {
            if (singleSiteCloudRFLayer != null) {
                singleSiteCloudRFLayer = null
                GLLayerFactory.unregister(GLCloudRFLayer.SPI)
            }

            for (layer in mapView.getLayers(MapView.RenderStack.MAP_SURFACE_OVERLAYS)) {
                if (layer.name == "SPOTBEAM") {
                    try {
                        if (layer != null) {
                            mapView.removeLayer(
                                MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                                layer
                            )
                        }
                        if (layer != null) {
                            mapView.removeLayer(
                                MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                                layer
                            )
                            GLLayerFactory.unregister(GLCloudRFLayer.SPI)
                        }
                    } catch (e: java.lang.Exception) {
                        Log.e("spotbeam", "error", e)
                    }
                }
            }

            GLLayerFactory.register(GLCloudRFLayer.SPI)
            singleSiteCloudRFLayer = CloudRFLayer(
                pluginContext,
                layerName,
                pluginContext.getString(R.string.layer, layerName),
                file.absolutePath,
                bounds, false,object : CloudRFLayerListener {
                    override fun delete(layer: CloudRFLayer) {
                        promptDelete(layer)
                    }
                }
            )
        }

        singleSiteCloudRFLayer?.let {
            mapView.addLayer(
                MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                singleSiteCloudRFLayer
            )
            singleSiteCloudRFLayer?.isVisible = true

            handleLayerVisibility()

            refreshView()
        }
    }

    fun addLayer(filePath: String, bounds: List<Double>, isBsaLayer: Boolean?=false) {
        val file = File(filePath)
        synchronized(this@PluginDropDownReceiver) {
            if (cloudRFLayer != null) {
                mapView.removeLayer(MapView.RenderStack.MAP_SURFACE_OVERLAYS, cloudRFLayer)
                cloudRFLayer = null
                GLLayerFactory.unregister(GLCloudRFLayer.SPI)
            }
            GLLayerFactory.register(GLCloudRFLayer.SPI)
            val layerName = pluginContext.getString(R.string.coverage_layer)
            cloudRFLayer =
                CloudRFLayer(
                    pluginContext,
                    layerName,
                    layerName,
                    file.absolutePath,
                    bounds,
                    isBsaLayer,
                    object : CloudRFLayerListener {
                        override fun delete(layer: CloudRFLayer) {
                            promptDelete(layer)
                        }
                    })
        }

        cloudRFLayer?.let {
            mapView.addLayer(
                MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                cloudRFLayer
            )
            cloudRFLayer?.isVisible = true
            handleLayerVisibility()

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
                                    sharedPrefs?.set(Constant.PreferenceKey.etPassword, etPassword?.text.toString())
                                    setLoginViewVisibility(isMoveBack = false, isAfterLogin = true)
                                    Constant.sServerUrl = etServerUrl?.text.toString()
                                    downloadTemplatesFromApi()
                                    Constant.sUsername = etUsername?.text.toString()
                                }
                            }
                        }

                        override fun onFailed(error: String?, responseCode: Int?) {
                            if(error != null){
                                pluginContext.toast(error)
                            }
                            if(responseCode == 404){
                                pluginContext.toast("This plugin needs SOOTHSAYER > v1.9. Try an older plugin from CloudRF.com")
                            }
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
                    pluginContext.shortToast(pluginContext.getString(R.string.template_downloading))
                }

                override fun onSuccess(response: Any?) {
                    if (response is TemplatesResponse) {
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
            return
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
                }

                override fun onSuccess(response: Any?) {
                    if (response is TemplateDataModel) {
                        Log.d(TAG, "onLoading: fetchTemplateDetail id:$id response : $response")
                        createAndStoreDownloadedFile(response)
                        fetchTemplateDetail(items)
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
        Contacts.getInstance().removeListener(this)
        // Clean up map event listener
        mapView?.mapEventDispatcher?.removeMapEventListener(MapEvent.ITEM_CLICK, this)
        stopTrackingLoop()
        try {
            if (singleSiteCloudRFLayer != null) {
                mapView.removeLayer(
                    MapView.RenderStack.MAP_SURFACE_OVERLAYS,
                    singleSiteCloudRFLayer
                )
                singleSiteCloudRFLayer = null
            }
            if (cloudRFLayer != null) {
                mapView.removeLayer(
                    MapView.RenderStack.MAP_SURFACE_OVERLAYS,
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
            SHOW_PLUGIN -> { handleShowPlugin(templateView, sharedPrefs, saveData = {
                    setDataFromPref()
                    Constant.sServerUrl = etLoginServerUrl?.text.toString()
                    Constant.sAccessToken = sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "") ?: ""
                })
            }
            GRG_TOGGLE_VISIBILITY, LAYER_VISIBILITY -> {
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
                val l = mapOverlay.findLayer(intent.getStringExtra("uid"))
                if (l != null) {
                    promptDelete(l)
                }
            }
            RADIO_EDIT -> {
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
                item?.let { marker ->
                    mapView.context.showAlert(pluginContext.getString(R.string.alert_title), "${pluginContext.getString(R.string.delete)} ${marker.markerDetails.template.name}",
                        pluginContext.getString(R.string.yes), pluginContext.getString(R.string.cancel), null, positiveListener = {
                            removeMarker(marker)
                        }) {
                    }
                }
            }
            GRG_BRIGHTNESS -> {}
            GRG_COLOR -> {}
            GRG_TRANSPARENCY -> {}
        }
    }

    private fun promptDelete(layer: CloudRFLayer) {
        mapView.context.showAlert(pluginContext.getString(R.string.civ_delete_layer),
            "${pluginContext.getString(R.string.delete)} ${layer.description}${
            pluginContext.getString(
                R.string.question_mark_symbol
            )
        }",    pluginContext.getString(R.string.ok_txt), pluginContext.getString(R.string.cancel),
            ContextCompat.getDrawable(pluginContext,R.drawable.ic_menu_delete), positiveListener = {
                mapView.delete(mapOverlay, layer)
                refreshView()
            } )
    }

    private fun handleLinkLineVisibility() {
        val mapGroup =
            mapView.rootGroup.findMapGroup(pluginContext.getString(R.string.drawing_objects))
        mapGroup.visible = cbLinkLines.isChecked
        refreshView()
    }

    private fun handleLayerVisibility() {
        if (mapOverlay.hideAllLayer(pluginContext.getString(R.string.soothsayer_layer), cbCoverageLayer.isChecked)) {
           refreshView()
        }
    }

    private fun refreshView(){
        AtakBroadcast.getInstance().sendBroadcast(Intent(HierarchyListReceiver.REFRESH_HIERARCHY))
    }

    private fun removeMarker(marker:MarkerDataModel){
        marker.coopted_uid?.let {
            coOptedMarkers.remove(it)
            if (coOptedMarkers.isEmpty()) {
                stopTrackingLoop()
            }
        }

        mapView.removeLinkLinesFromMap(pluginContext,marker)
        removeMarkerFromList(marker)
        mapView.removeMarkerFromMap(marker)

    }

    override fun onDropDownSelectionRemoved() {}
    override fun onDropDownVisible(v: Boolean) {}

    override fun onDropDownSizeChanged(width: Double, height: Double) {}
    override fun onDropDownClose() {
    }

    override fun onContactsSizeChange(contacts: Contacts?) {
        allContacts = Contacts.getInstance().getAllContacts() ?: mutableListOf()
    }

    override fun onContactChanged(uuid: String?) {
    }

    override fun onMapEvent(event: MapEvent) {
        // Handle marker tap events to auto-scroll co-opt list
        if (event.type == MapEvent.ITEM_CLICK && coOptView.visibility == View.VISIBLE) {
            val clickedItem = event.item
            if (clickedItem != null) {
                scrollToMarkerInCoOptList(clickedItem.uid)
            }
        }
    }

    private fun scrollToMarkerInCoOptList(markerUid: String) {
        try {
            val coOptRecyclerView = coOptView.findViewById<RecyclerView>(R.id.co_opt_recycler_view)
            val adapter = coOptRecyclerView.adapter as? CoOptAdapter

            if (adapter != null) {
                // Find the position of the marker in the current displayed list
                val currentMarkers = adapter.getCurrentMarkers()
                val position = currentMarkers.indexOfFirst { it.uid == markerUid }

                if (position >= 0) {
                    // Use LinearLayoutManager to scroll to position at top
                    val layoutManager = coOptRecyclerView.layoutManager as? LinearLayoutManager
                    layoutManager?.scrollToPositionWithOffset(position, 0)

                    Log.d(TAG, "Scrolled to marker at position $position in co-opt list")

                    // Flash the item after a short delay to ensure it's visible
                    coOptRecyclerView.postDelayed({
                        adapter.flashItem(markerUid)
                    }, 100)
                } else {
                    Log.d(TAG, "Marker not found in current co-opt list")
                    Toast.makeText(pluginContext, "Marker not found in current list", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scrolling to marker in co-opt list", e)
        }
    }

    companion object {
        const val TAG = "SOOTHSAYER"
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

    // WARNING: Satellite functionality may get canned as GS has largely been killed by Starlink et al
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

        val btnPlayBtn = templateView.findViewById<ImageButton>(R.id.btnPlayBtn)
        btnPlayBtn.setOnClickListener {
            val item: MarkerDataModel? = markersList.findLast { true }
            if(item == null){
                pluginContext.toast("Add a radio marker to the map first")
            }

            // Handle pause
            if(trackingRunnable != null){
                stopTrackingLoop();
            }else {
                calculate(item)
            }
        }

        val currentDate = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        val editDate = spotBeamView.findViewById<EditText>(R.id.editDate)
        editDate.setText(dateFormat.format(currentDate))
        val editTime = spotBeamView.findViewById<EditText>(R.id.editTime)
        editTime.setText("12:00:00")
        val satelliteSearch = spotBeamView.findViewById<AutoCompleteTextView>(R.id.sbSatelliteSearch)

        satelliteSearch.setOnFocusChangeListener { _, b ->
            if (b) satelliteSearch.setText("")
            else if (satelliteSearch.text.isEmpty())
                satelliteSearch.setText("Search Satellites")
        }

        satelliteSearch.addTextChangedListener {
            Satellite.getSats(satelliteSearch.text.toString(), this, RetrofitClient.BASE_URL)
            if (names.isEmpty()) names = arrayOf("")
            val adapter = ArrayAdapter(pluginContext,
                android.R.layout.simple_list_item_1,
                names)
            satelliteSearch.setAdapter(adapter)
            satelliteSearch.threshold = 2
        }

        val resolutionSpinner = spotBeamView.findViewById<Spinner>(R.id.megapixelSpinner)
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
        marker.title = "Satellite coverage"

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
                    pluginContext.toast("Calculating coverage...")
                    val latitude = marker.geoPointMetaData.get().latitude
                    val longitude = marker.geoPointMetaData.get().longitude
                    val editDate = spotBeamView.findViewById<EditText>(R.id.editDate).text
                    val editTime = spotBeamView.findViewById<EditText>(R.id.editTime).text
                    val dateTime: String = (editDate.toString() + "T" + editTime.toString() + "Z")
                    SpotBeamCall.callAPI(satellite, latitude, longitude, this,
                        sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "") ?: "", RetrofitClient.BASE_URL, dateTime){ line->
                        mapView.rootGroup.addItem(line)
                    }
                }
            }
        }
     }

    fun removeLines() {
        for (it in mapView.rootGroup.items)
            if (it.title == "AZIMUTH")
                mapView.rootGroup.removeItem(it)
    }

    private fun showCoOptView(show: Boolean) {
        if (show) {
            mainLayout.visibility = View.GONE
            coOptView.visibility = View.VISIBLE
            populateCoOptList()
        } else {
            coOptView.visibility = View.GONE
            mainLayout.visibility = View.VISIBLE
        }
    }

    private fun populateCoOptList() {
        val coOptRecyclerView = coOptView.findViewById<RecyclerView>(R.id.co_opt_recycler_view)
        coOptRecyclerView.layoutManager = LinearLayoutManager(pluginContext)

        // Get all available markers (this will be our master list)
        val allAvailableMarkers = mapView.getAllAvailableMarkers(allContacts)

        // Create adapter with initial full list
        val coOptAdapter = CoOptAdapter(pluginContext, allAvailableMarkers, templateItems, sharedPrefs) {
            createTemplateSpinnerAdapter()
        }
        coOptRecyclerView.adapter = coOptAdapter

        // Sort markers to put checked ones at the top
        coOptAdapter.sortMarkersWithCheckedFirst()

        // Set up search functionality
        val searchEditText = coOptView.findViewById<EditText>(R.id.co_opt_search_edittext)
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s.toString().trim()
                val filteredMarkers = if (query.isEmpty()) {
                    allAvailableMarkers
                } else {
                    allAvailableMarkers.filter { marker ->
                        val name = marker.title?.takeIf { it.isNotBlank() }
                            ?: (marker as? Marker)?.getMetaString("callsign", null)?.takeIf { it.isNotBlank() }
                            ?: marker.getMetaString("name", null)?.takeIf { it.isNotBlank() }
                            ?: marker.uid
                        name.contains(query, ignoreCase = true)
                    }
                }
                coOptAdapter.updateMarkers(filteredMarkers)
            }
        })

        coOptView.findViewById<Button>(R.id.co_opt_cancel_button).setOnClickListener {
            showCoOptView(false)
        }
        coOptView.findViewById<Button>(R.id.co_opt_ok_button).setOnClickListener {
            // Get refresh settings from main settings instead of co-opt dialog
            val refreshInterval = sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshInterval, 300L) ?: 300L
            val refreshDistance = sharedPrefs?.get(Constant.PreferenceKey.sCoOptDistanceRefreshThreshold, 100.0) ?: 100.0

            for ((uid, config) in coOptAdapter.coOptConfigurations) {
                markersList.removeAll { it.coopted_uid == uid }

                if (config.isEnabled && config.template != null) {
                    val originalMarker = allAvailableMarkers.find { it.uid == uid } as? Marker ?: continue
                    val callsign = originalMarker.getMetaString("callsign", "marker")

                    val newMarkerData = MarkerDataModel(
                        markerID = UUID.randomUUID().toString(),
                        markerDetails = config.template!!.copy(
                            template = config.template!!.template.copy(
                                name = "$callsign - ${config.template!!.template.name}"
                            ),
                            transmitter = config.template!!.transmitter?.copy(
                                lat = Math.round(originalMarker.point.latitude * 1e5).toDouble() / 1e5,
                                lon = Math.round(originalMarker.point.longitude * 1e5).toDouble() / 1e5
                            )
                        ),
                        coopted_uid = uid
                    )
                    markersList.add(newMarkerData)

                    val settings = CoOptedMarkerSettings(
                        uid = uid,
                        template = config.template!!,
                        refreshIntervalSeconds = refreshInterval,
                        refreshDistanceMeters = refreshDistance
                    )
                    coOptedMarkers[uid] = settings
                } else {
                    coOptedMarkers.remove(uid)
                }
            }

            markerAdapter?.notifyDataSetChanged()

            if (coOptedMarkers.isNotEmpty()) {
                startTrackingLoop()
            } else {
                stopTrackingLoop()
            }

            showCoOptView(false)
        }
    }

    private fun createTemplateSpinnerAdapter(): ArrayAdapter<TemplateDataModel> {
        val validTemplates = templateItems.filter { it.template != null }
        val adapter: ArrayAdapter<TemplateDataModel> = object :
            ArrayAdapter<TemplateDataModel>(
                pluginContext,
                R.layout.spinner_item_layout,
                validTemplates
            ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as TextView
                getItem(position)?.template?.name?.let {
                    textView.text = it
                }
                return textView
            }

            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val textView = super.getDropDownView(position, convertView, parent) as TextView
                getItem(position)?.template?.name?.let {
                    textView.text = it
                }
                return textView
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return adapter
    }

    private fun startTrackingLoop() {
        Log.d(TAG, "startTrackingLoop()")

        stopTrackingLoop()
        mapView.runCoOptUpdate(markersList, sharedPrefs,coOptedMarkers, updateAdapter = { index->
            markerAdapter?.notifyItemChanged(index)
        }, calculate = { lastUpdatedMarker->
            calculate(lastUpdatedMarker)
        })
        var timeEnabled = sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshEnabled, true) ?: true
        var distanceEnabled = sharedPrefs?.get(Constant.PreferenceKey.sCoOptDistanceRefreshEnabled, false) ?: false

        if (!timeEnabled && !distanceEnabled) {
            return
        }

        // Initialize lastKnownLocations with current positions when starting tracking
        if (distanceEnabled) {
            for ((uid, _) in coOptedMarkers) {
                val currentMarker = mapView.rootGroup.deepFindItem("uid", uid) as? PointMapItem
                if (currentMarker != null) {
                    calcManager.setLastKnownLocation(uid,currentMarker.point.latitude, currentMarker.point.longitude)
                    Log.d(TAG, "Initialized tracking for marker $uid at position: ${currentMarker.point.latitude}, ${currentMarker.point.longitude}")
                }
            }
        }

        templateView.findViewById<ImageButton>(R.id.btnPlayBtn).setImageResource(android.R.drawable.ic_media_pause)

        val nextUpdateTextView = templateView.findViewById<TextView>(R.id.co_opt_next_update_textview)
        var refreshIntervalSeconds = sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshInterval, 30L) ?: 30L

        trackingRunnable = object : Runnable {
            var countdown = refreshIntervalSeconds

            override fun run() {
                // keep checking for changes to preferences..
                timeEnabled = sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshEnabled, true) ?: true
                distanceEnabled = sharedPrefs?.get(Constant.PreferenceKey.sCoOptDistanceRefreshEnabled, false) ?: false
                refreshIntervalSeconds = sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshInterval, 30L) ?: 30L

                var periodicUpdateJustHappened = false
                if (timeEnabled && refreshIntervalSeconds > 1) {
                    nextUpdateTextView.visibility = View.VISIBLE
                    nextUpdateTextView.text = "Refresh in ${countdown}s"
                    if (countdown <= 0) {
                        mapView.runCoOptUpdate(markersList, sharedPrefs,coOptedMarkers, updateAdapter = { index->
                            markerAdapter?.notifyItemChanged(index)
                        }, calculate = { lastUpdatedMarker->
                            calculate(lastUpdatedMarker)
                        })
                        countdown = refreshIntervalSeconds
                        periodicUpdateJustHappened = true
                    } else {
                        countdown--
                    }
                } else {
                    nextUpdateTextView.visibility = View.GONE
                }

                if (distanceEnabled && !periodicUpdateJustHappened) {
                    calcManager.checkDistanceAndRecalculate(coOptedMarkers,templateView, updateAdapter = { index->
                        markerAdapter?.notifyItemChanged(index)
                    })
                }

                // 1 second count
                trackingHandler.postDelayed(this, 1000)
            }
        }
        trackingHandler.post(trackingRunnable as Runnable)
    }

    private fun stopTrackingLoop() {
        Log.d(TAG, "stopTrackingLoop()")
        trackingHandler.removeCallbacksAndMessages(null);

        trackingRunnable?.let {
            trackingRunnable = null
        }
        templateView.findViewById<TextView>(R.id.co_opt_next_update_textview).visibility = View.GONE
        templateView.findViewById<ImageButton>(R.id.btnPlayBtn).setImageResource(android.R.drawable.ic_media_play)
    }

    private fun calculate(item: MarkerDataModel?) {
        calcManager.calculate(item)
    }
}