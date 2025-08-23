package com.atakmap.android.soothsayer

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
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
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atak.plugins.impl.PluginLayoutInflater
import com.atakmap.android.contact.Contact
import com.atakmap.android.contact.Contacts
import com.atakmap.android.drawing.mapItems.DrawingShape
import com.atakmap.android.dropdown.DropDown.OnStateListener
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.grg.GRGMapComponent
import com.atakmap.android.hierarchy.HierarchyListReceiver
import com.atakmap.android.importexport.ImportExportMapComponent
import com.atakmap.android.importexport.ImportReceiver
import com.atakmap.android.ipc.AtakBroadcast
import com.atakmap.android.maps.MapEvent
import com.atakmap.android.maps.MapEventDispatcher
import com.atakmap.android.maps.MapGroup
import com.atakmap.android.maps.MapItem
import com.atakmap.android.maps.MapView
import com.atakmap.android.maps.Marker
import com.atakmap.android.maps.MultiPolyline
import com.atakmap.android.maps.PointMapItem
import com.atakmap.android.maps.Polyline
import com.atakmap.android.menu.PluginMenuParser
import com.atakmap.android.preference.AtakPreferences
import com.atakmap.android.soothsayer.interfaces.CloudRFLayerListener
import com.atakmap.android.soothsayer.layers.CloudRFLayer
import com.atakmap.android.soothsayer.layers.GLCloudRFLayer
import com.atakmap.android.soothsayer.layers.PluginMapOverlay
import com.atakmap.android.soothsayer.models.common.CoOptedMarkerSettings
import com.atakmap.android.soothsayer.models.common.MarkerDataModel
import com.atakmap.android.soothsayer.models.linksmodel.Link
import com.atakmap.android.soothsayer.models.linksmodel.LinkDataModel
import com.atakmap.android.soothsayer.models.linksmodel.LinkRequest
import com.atakmap.android.soothsayer.models.linksmodel.LinkResponse
import com.atakmap.android.soothsayer.models.linksmodel.Point
import com.atakmap.android.soothsayer.models.request.Bounds
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
import com.atakmap.android.soothsayer.recyclerview.CoOptAdapter
import com.atakmap.android.soothsayer.recyclerview.RecyclerViewAdapter
import com.atakmap.android.soothsayer.util.Constant
import com.atakmap.android.soothsayer.util.FOLDER_PATH
import com.atakmap.android.soothsayer.util.PNG_IMAGE
import com.atakmap.android.soothsayer.util.base64StringToBitmap
import com.atakmap.android.soothsayer.util.createAndStoreDownloadedFile
import com.atakmap.android.soothsayer.util.createAndStoreFiles
import com.atakmap.android.soothsayer.util.getBitmap
import com.atakmap.android.soothsayer.util.getFileName
import com.atakmap.android.soothsayer.util.getLineColor
import com.atakmap.android.soothsayer.util.getTemplatesFromFolder
import com.atakmap.android.soothsayer.util.isConnected
import com.atakmap.android.soothsayer.util.roundValue
import com.atakmap.android.soothsayer.util.setSpannableText
import com.atakmap.android.soothsayer.util.shortToast
import com.atakmap.android.soothsayer.util.showAlert
import com.atakmap.android.soothsayer.util.toast
import com.atakmap.android.util.SimpleItemSelectedListener
import com.atakmap.coremap.maps.assets.Icon
import com.atakmap.coremap.maps.coords.GeoPoint
import com.atakmap.map.elevation.ElevationData
import com.atakmap.map.elevation.ElevationManager
import com.atakmap.map.layer.opengl.GLLayerFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gov.tak.platform.graphics.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt


class PluginDropDownReceiver(
    mapView: MapView?,
    val pluginContext: Context, private val mapOverlay: PluginMapOverlay
) : DropDownReceiver(mapView), OnStateListener, Contacts.OnContactsChangedListener, MapEventDispatcher.MapEventDispatchListener {
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
    private val svMode: Switch = settingsLayersView.findViewById(R.id.svMode)
    private val cbCoverageLayer: CheckBox = settingsLayersView.findViewById(R.id.cbKmzLayer)
    private val cbLinkLines: CheckBox = settingsLayersView.findViewById(R.id.cbLinkLines)
    private val cbCoOptTimeRefresh: CheckBox = settingsCooptView.findViewById(R.id.cbCoOptTimeRefresh)
    private val etCoOptTimeInterval: EditText = settingsCooptView.findViewById(R.id.etCoOptTimeInterval)
    private val cbCoOptDistanceRefresh: CheckBox = settingsCooptView.findViewById(R.id.cbCoOptDistanceRefresh)
    private val etCoOptDistanceThreshold: EditText = settingsCooptView.findViewById(R.id.etCoOptDistanceThreshold)
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
    private var allContacts: MutableList<Contact> = mutableListOf()
    private val coOptedMarkers = HashMap<String, CoOptedMarkerSettings>()
    private val trackingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var trackingRunnable: Runnable? = null
    private val lastKnownLocations = HashMap<String, GeoPoint>()
    private var colourPickerCurId: Int = 0

    class ColourRef(var value: Int)

    private var optionsColour1: ColourRef = ColourRef(0xFF00FF00.toInt())
    private var optionsColour2: ColourRef = ColourRef(0xFFFF8800.toInt())
    private var optionsColour3: ColourRef = ColourRef(0xFFFF0000.toInt())
    private var optionsColour4: ColourRef = ColourRef(0xFF222222.toInt())

    private fun getCurrentColour(): ColourRef {
        return when (colourPickerCurId) {
            1 -> optionsColour1
            2 -> optionsColour2
            3 -> optionsColour3
            4 -> optionsColour4
            else -> ColourRef(0)
        }
    }

    private fun setRedChannel(colour: Int, red: Int): Int {
        return (colour and 0xFF00FFFF.toInt()) or ((red and 0xFF) shl 16)
    }

    private fun setGreenChannel(colour: Int, green: Int): Int {
        return (colour and 0xFFFF00FF.toInt()) or ((green and 0xFF) shl 8)
    }

    private fun setBlueChannel(colour: Int, blue: Int): Int {
        return (colour and 0xFFFFFF00.toInt()) or (blue and 0xFF)
    }

    // Initialise views, listeners and handle map clicks
    init {
        initViews()
        initListeners()
        initSpotBeam()
        Contacts.getInstance().addListener(this)
        onContactsSizeChange(null)
        // Register for map events to capture marker taps
        mapView?.mapEventDispatcher?.addMapEventListener(MapEvent.ITEM_CLICK, this)
    }

    private fun initViews() {
        pluginContext.createAndStoreFiles(getAllFilesFromAssets())
        initSettings()
        initRadioSettingView()
        initTemplateSpinner()
        initMegapixelSpinner()
        initLoginView()
        initRecyclerview()
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
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
        btnAddPolygon.setOnClickListener {
            if (Constant.sAccessToken != "") {
                pluginContext.shortToast("Draw a polygon for the study area")
                CustomPolygonTool.createPolygon()
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

        val btnOpenOptionsSettings = settingView.findViewById<Button>(R.id.btnOpenOptionsSettings)
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

        val curColourView = colourPickerView.findViewById<View>(R.id.colourPickerCurColour)

        val colourPickerRedBar = colourPickerView.findViewById<SeekBar>(R.id.colourPickerRedBar);
        val colourPickerGreenBar = colourPickerView.findViewById<SeekBar>(R.id.colourPickerGreenBar);
        val colourPickerBlueBar = colourPickerView.findViewById<SeekBar>(R.id.colourPickerBlueBar);

        val btnOptionsColour1 = settingsOptionsView.findViewById<Button>(R.id.btnOptionsColour1)
        btnOptionsColour1.setOnClickListener {
            colourPickerCurId = 1;
            settingsOptionsView.visibility = View.GONE
            colourPickerView.visibility = View.VISIBLE
            colourPickerRedBar.progress = Color.red(optionsColour1.value)
            colourPickerGreenBar.progress = Color.green(optionsColour1.value)
            colourPickerBlueBar.progress = Color.blue(optionsColour1.value)
        }

        val btnOptionsColour2 = settingsOptionsView.findViewById<Button>(R.id.btnOptionsColour2)
        btnOptionsColour2.setOnClickListener {
            colourPickerCurId = 2;
            settingsOptionsView.visibility = View.GONE
            colourPickerView.visibility = View.VISIBLE
            colourPickerRedBar.progress = Color.red(optionsColour2.value)
            colourPickerGreenBar.progress = Color.green(optionsColour2.value)
            colourPickerBlueBar.progress = Color.blue(optionsColour2.value)
        }

        val btnOptionsColour3 = settingsOptionsView.findViewById<Button>(R.id.btnOptionsColour3)
        btnOptionsColour3.setOnClickListener {
            colourPickerCurId = 3;
            settingsOptionsView.visibility = View.GONE
            colourPickerView.visibility = View.VISIBLE
            colourPickerRedBar.progress = Color.red(optionsColour3.value)
            colourPickerGreenBar.progress = Color.green(optionsColour3.value)
            colourPickerBlueBar.progress = Color.blue(optionsColour3.value)
        }

        val btnOptionsColour4 = settingsOptionsView.findViewById<Button>(R.id.btnOptionsColour4)
        btnOptionsColour4.setOnClickListener {
            colourPickerCurId = 4;
            settingsOptionsView.visibility = View.GONE
            colourPickerView.visibility = View.VISIBLE
            colourPickerRedBar.progress = Color.red(optionsColour4.value)
            colourPickerGreenBar.progress = Color.green(optionsColour4.value)
            colourPickerBlueBar.progress = Color.blue(optionsColour4.value)
        }

        colourPickerRedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var curColour = getCurrentColour()
                curColour.value = setRedChannel(curColour.value, progress)
                curColourView.setBackgroundColor(curColour.value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        colourPickerGreenBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var curColour = getCurrentColour()
                curColour.value = setGreenChannel(curColour.value, progress)
                curColourView.setBackgroundColor(curColour.value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        colourPickerBlueBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var curColour = getCurrentColour()
                curColour.value = setBlueChannel(curColour.value, progress)
                curColourView.setBackgroundColor(curColour.value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { }
        })

        val btnColourPickerBack = colourPickerView.findViewById<ImageView>(R.id.colourPickerBack)
        btnColourPickerBack.setOnClickListener {
            val colour = getCurrentColour()

            when (colourPickerCurId) {
                1 -> btnOptionsColour1.setBackgroundColor(colour.value)
                2 -> btnOptionsColour2.setBackgroundColor(colour.value)
                3 -> btnOptionsColour3.setBackgroundColor(colour.value)
                4 -> btnOptionsColour4.setBackgroundColor(colour.value)
                else -> {}
            }

            colourPickerCurId = 0

            colourPickerView.visibility = View.GONE
            settingsOptionsView.visibility = View.VISIBLE
        }

        val optionsUnitSwitch = settingsOptionsView.findViewById<Switch>(R.id.optionsUnitSwitch)
        val optionsUnitView1 = settingsOptionsView.findViewById<TextView>(R.id.optionsUnit1)
        val optionsUnitView2 = settingsOptionsView.findViewById<TextView>(R.id.optionsUnit2)
        val optionsUnitView3 = settingsOptionsView.findViewById<TextView>(R.id.optionsUnit3)
        val optionsUnitView4 = settingsOptionsView.findViewById<TextView>(R.id.optionsUnit4)

        var settingsOptionsDB1 = settingsOptionsView.findViewById<EditText>(R.id.db1)
        var settingsOptionsDB2 = settingsOptionsView.findViewById<EditText>(R.id.db2)
        var settingsOptionsDB3 = settingsOptionsView.findViewById<EditText>(R.id.db3)
        var settingsOptionsDB4 = settingsOptionsView.findViewById<EditText>(R.id.db4)

        var optionsUnitSwitchActivated = false
        optionsUnitSwitch.setOnCheckedChangeListener { _, isChecked ->
            optionsUnitSwitchActivated = isChecked
            optionsUnitView1.text = if (optionsUnitSwitchActivated) "dBm" else "dB"
            optionsUnitView2.text = if (optionsUnitSwitchActivated) "dBm" else "dB"
            optionsUnitView3.text = if (optionsUnitSwitchActivated) "dBm" else "dB"
            optionsUnitView4.text = if (optionsUnitSwitchActivated) "dBm" else "dB"

            settingsOptionsDB1.setText(settingsOptionsDB1.text)
            settingsOptionsDB2.setText(settingsOptionsDB2.text)
            settingsOptionsDB3.setText(settingsOptionsDB3.text)
            settingsOptionsDB4.setText(settingsOptionsDB4.text)
        }

        val btnResetColours = settingsOptionsView.findViewById<Button>(R.id.btnResetColours)
        btnResetColours.setOnClickListener {
            btnOptionsColour1.setBackgroundColor(0xFF00FF00.toInt())
            btnOptionsColour2.setBackgroundColor(0xFFFF8800.toInt())
            btnOptionsColour3.setBackgroundColor(0xFFFF0000.toInt())
            btnOptionsColour4.setBackgroundColor(0xFF222222.toInt())

            optionsColour1.value = 0xFF00FF00.toInt()
            optionsColour2.value = 0xFFFF8800.toInt()
            optionsColour3.value = 0xFFFF0000.toInt()
            optionsColour4.value = 0xFF222222.toInt()

            optionsUnitSwitch.isChecked = false

            optionsUnitView1.text = "dB"
            optionsUnitView2.text = "dB"
            optionsUnitView3.text = "dB"
            optionsUnitView4.text = "dB"

            settingsOptionsDB1.setText("20")
            settingsOptionsDB2.setText("10")
            settingsOptionsDB3.setText("0")
            settingsOptionsDB4.setText("-10")
        }

        settingsOptionsDB1.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(editable: Editable?) {
                if (editable.toString().isEmpty() || editable.toString().equals("-"))
                    return

                var value = editable.toString().toInt()
                val min = if (optionsUnitSwitchActivated) -140 else -20
                val max = if (optionsUnitSwitchActivated) 0 else 90

                if (value < min)
                    value = min
                else if (value > max)
                    value = max
                else return

                settingsOptionsDB1.removeTextChangedListener(this)
                settingsOptionsDB1.setText(value.toString())
                settingsOptionsDB1.addTextChangedListener(this)
            }
        })

        settingsOptionsDB2.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(editable: Editable?) {
                if (editable.toString().isEmpty() || editable.toString().equals("-"))
                    return

                var value = editable.toString().toInt()
                val min = if (optionsUnitSwitchActivated) -140 else -20
                val max = if (optionsUnitSwitchActivated) 0 else 90

                if (value < min)
                    value = min
                else if (value > max)
                    value = max
                else return

                settingsOptionsDB2.removeTextChangedListener(this)
                settingsOptionsDB2.setText(value.toString())
                settingsOptionsDB2.addTextChangedListener(this)            }
        })

        settingsOptionsDB3.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(editable: Editable?) {
                if (editable.toString().isEmpty() || editable.toString().equals("-"))
                    return

                var value = editable.toString().toInt()
                val min = if (optionsUnitSwitchActivated) -140 else -20
                val max = if (optionsUnitSwitchActivated) 0 else 90

                if (value < min)
                    value = min
                else if (value > max)
                    value = max
                else return

                settingsOptionsDB3.removeTextChangedListener(this)
                settingsOptionsDB3.setText(value.toString())
                settingsOptionsDB3.addTextChangedListener(this)            }
        })

        settingsOptionsDB4.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { }
            override fun afterTextChanged(editable: Editable?) {
                if (editable.toString().isEmpty() || editable.toString().equals("-"))
                    return

                var value = editable.toString().toInt()
                val min = if (optionsUnitSwitchActivated) -140 else -20
                val max = if (optionsUnitSwitchActivated) 0 else 90

                if (value < min)
                    value = min
                else if (value > max)
                    value = max
                else return

                settingsOptionsDB4.removeTextChangedListener(this)
                settingsOptionsDB4.setText(value.toString())
                settingsOptionsDB4.addTextChangedListener(this)            }
        })

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

    private fun initTemplateSpinner() {
        val spinner: Spinner = templateView.findViewById(R.id.spTemplate)
        templateItems.addAll(getTemplatesFromFolder())
        val validTemplateItems = templateItems.filter { it.template != null }

        val spinnerAdapter: ArrayAdapter<TemplateDataModel> = object :
            ArrayAdapter<TemplateDataModel>(
                pluginContext,
                R.layout.spinner_item_layout,
                validTemplateItems
            ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val textView = super.getView(position, convertView, parent) as TextView
                val item: TemplateDataModel? = getItem(position)
                if (item?.template != null) {
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
                if (item?.template != null) {
                    textView.text = item.template.name
                }
                return textView
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
                    val extraTemplates = getTemplatesFromFolder()
                    if (extraTemplates.isEmpty()) { 
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
                    spinner.performClick()
                }
            }
            false
        }
    }

    // A 1000x1000px image is 1MP and can be considered average for a 2024 phone
    // You can load a 16MP image on a phone but this may crash an older model with an OOM error
    // In the early days of CloudRF's Android app (2012), the resolution was limited by device mem at 0.09MP (300x300px)
    var megapixels = 1.0;

    private fun initMegapixelSpinner(){
        val mpSpinner = settingsOptionsView.findViewById<Spinner>(R.id.megapixelSpinner)
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
                    megapixels=0.25
                }
                if(position==1){
                    megapixels=1.0
                }
                if(position==2){
                    megapixels=4.0
                }
            }
        }
    }

    // Auto-scale the resolution to match the desired megapixel.
    // The API does this automatically to enforce different plans but those MP limits are higher for laptops etc

    private fun megapixelCalculator(radius: Double): Double {
        // Calculate the resolution based upon the desired radius
        var diameter_m = (radius * 2) * 1e3 //eg. 4000m
        var res = diameter_m / sqrt(megapixels*1e6)  //eg. 4000 / 1000 = 4

        if(res < 1){
            res = 1.0
        }

        if(res > 180){
            res = 180.0
        }

        return res
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

    // used to adjust radius for polygons far away
    fun haversine(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val dLat = Math.toRadians(toLat - fromLat)
        val dLon = Math.toRadians(toLon - fromLon)
        val originLat = Math.toRadians(fromLat)
        val destinationLat = Math.toRadians(toLat)

        val a = Math.pow(Math.sin(dLat / 2), 2.0) + Math.pow(Math.sin(dLon / 2), 2.0) * Math.cos(originLat) * Math.cos(destinationLat)
        val c = 2 * Math.asin(sqrt(a))
        return 6372.8 * c
    }


    private fun calculate(item: MarkerDataModel?){
        val showLinks = sharedPrefs?.get(Constant.PreferenceKey.sLinkLinesVisibility, true) ?: true
        val showCoverage = sharedPrefs?.get(Constant.PreferenceKey.sKmzVisibility, true) ?: true
        val multisiteMode = sharedPrefs?.get(Constant.PreferenceKey.sCalculationMode, false) ?: false

        if(!showLinks && !showCoverage) {
            toast("You need either links or coverage enabled")
            return
        }

        if(markersList.size < 1) {
            toast("You need to add a radio first")
            return
        }

        // Dynamic resolution
        val radius = item?.markerDetails!!.output.rad
        item?.markerDetails?.output?.res = megapixelCalculator(radius)

        // Force engine. 1=GPU, 2=CPU
        if(multisiteMode){
            item?.markerDetails?.engine = 1
        }else{
            item?.markerDetails?.engine = 2
        }


        if(showLinks && markersList.size > 1) {
            updateLinkLines(item)
        }else{
            removeLinkLinesFromMap(item)
        }

        val polygon = CustomPolygonTool.getMaskingPolygon()

        // Bounded area for focused calculations (CPU & GPU)
        if(polygon != null) {
            Log.d(TAG,polygon.toString());

            val area = polygon.area
            item?.markerDetails?.output?.res = min(10.0,ceil(sqrt(area) / 1000.0) + 1.0) // adjust resolution based upon polygon size
            val txlat = item.markerDetails.transmitter?.lat
            val txlon = item.markerDetails.transmitter?.lon

            if(txlat != null && txlon != null) {
                val polyLon = (GeoImageMasker.getBounds(polygon.points).east + GeoImageMasker.getBounds(polygon.points).west) / 2
                val polyLat = (GeoImageMasker.getBounds(polygon.points).north + GeoImageMasker.getBounds(polygon.points).south) / 2
                val haversineDistance = haversine(txlat, txlon, polyLat, polyLon)
                if(haversineDistance > item.markerDetails.output.rad){
                    item.markerDetails.output.rad = haversineDistance*1.1
                }
            }
            val newbounds = Bounds(GeoImageMasker.getBounds(polygon.points).north,GeoImageMasker.getBounds(polygon.points).east,GeoImageMasker.getBounds(polygon.points).south,GeoImageMasker.getBounds(polygon.points).west)
            item?.markerDetails?.output?.bounds = newbounds
        }else{
            item?.markerDetails?.output?.bounds = null
        }

        // A Multisite API call simulates many towers at once and uses a GPU
        if (multisiteMode) {
            item?.markerDetails?.let { template ->
                 // in a multisite call, each transmitter can have its own location, frequency, altitude and antenna
                val txlist: List<MultiSiteTransmitter> =
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
                                        marker.markerDetails.antenna,
                                        false // remote is false unless we're doing a satellite call far away
                                )
                            }
                        }

                val request = MultisiteRequest(
                        template.site,
                        template.network,
                        txlist, // array of transmitters :)
                        template.receiver,
                        template.model,
                        template.environment,
                        template.output
                )
                if(showCoverage){
                    showHidePlayBtn();
                    sendMultiSiteDataToServer(request)
                }
            }
        } else {
            item?.let {
                if(showCoverage) {
                    showHidePlayBtn();
                    sendSingleSiteDataToServer(item.markerDetails)
                }
            }
        }
    }

    private fun addCustomMarker() {
        val uid = UUID.randomUUID().toString()
        val location = mapView.centerPoint.get()
        val marker = Marker(location, uid)
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

        val icon: Bitmap? = if(selectedMarkerType?.customIcon == null) pluginContext.getBitmap(R.drawable.marker_icon_svg) else selectedMarkerType?.customIcon?.base64StringToBitmap()?:pluginContext.getBitmap(R.drawable.marker_icon_svg)
        val outputStream = ByteArrayOutputStream()
        icon?.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val b = outputStream.toByteArray()
        val encoded = "base64://" + Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE)
        val markerIconBuilder = Icon.Builder().setImageUri(0, encoded)
        marker.icon = markerIconBuilder.build()
        mapView.rootGroup.addItem(marker)

        mapView.mapEventDispatcher.addMapItemEventListener(
            marker
        ) { mapItem, mapEvent ->
            if (mapItem.type == mItemType) {
                when (mapEvent.type) {
                    MapEvent.ITEM_ADDED -> {
                        Log.d(TAG, "mapItem : Added ")
                    }
                    MapEvent.ITEM_REMOVED -> {
                        Log.d(TAG, "mapItem : Removed ")
                        val item: MarkerDataModel? = markersList.find { marker ->
                            marker.markerID == mapItem.uid
                        }
                        removeLinkLinesFromMap(item)
                        removeMarkerFromList(item)

                    }
                    MapEvent.ITEM_RELEASE -> {
                        Log.d(TAG, "mapItem : ITEM_RELEASE ")
                    }
                    MapEvent.ITEM_DRAG_DROPPED -> {
                        val latitude = marker.geoPointMetaData.get().latitude
                        val longitude = marker.geoPointMetaData.get().longitude
                        val item = markersList.find { it.markerID == mapItem.uid }
                        item?.let {
                            val index = markersList.indexOf(item)
                            item.markerDetails.transmitter?.lat = latitude.roundValue()
                            item.markerDetails.transmitter?.lon = longitude.roundValue()
                            saveMarkerListToPref()
                            markerAdapter?.notifyItemChanged(markersList.indexOf(item))
                        }

                        calculate(item)
                    }
                }
            }
        }

        selectedMarkerType?.let {
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
    }

    private fun getLinksBetween(marker: MarkerDataModel?) {
       // Log.d(TAG, "getLinksBetween: "+marker.toString())
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
                markerLinkList.add(linkDataModel)
            }

            repository.getLinks(linkDataModel.linkRequest,
                object : PluginRepository.ApiCallBacks {
                    override fun onLoading() {
                    }

                    override fun onSuccess(response: Any?) {
                        linkDataModel.linkResponse = response as LinkResponse
                        markerLinkList.add(linkDataModel)
                        linkDataModel.linkRequest.transmitter?.let { transmitter ->
                            linkDataModel.linkResponse?.let { linkResponse ->
                                for (data in linkResponse.transmitters) {
                                    // Line colours are dynamic, based upon the SNR
                                    pluginContext.getLineColor(data.signalToNoiseRatioDB)
                                        ?.let { color ->
                                            drawLine(
                                                data.markerId,
                                                linkDataModel.links,
                                                GeoPoint(transmitter.lat, transmitter.lon,transmitter.alt),
                                                GeoPoint((transmitter.lat + data.latitude)/2, (transmitter.lon+data.longitude)/2,data.antennaHeight),
                                                color,
                                                data.signalToNoiseRatioDB.toInt()
                                            )
                                        }
                                }
                            }
                        }
                    }

                    override fun onFailed(error: String?, responseCode: Int?) {
                        stopTrackingLoop()
                        pluginContext.toast("Link error: $error")
                    }

                })

        }
    }

    private fun drawLine(
        linkToId: String?,
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
        ds.remarks = "SOOTHSAYER" // used for id for removal later
        dslist.add(ds)

        val lineUid = UUID.randomUUID().toString()
        val mp = MultiPolyline(mapView, lineGroup, dslist, lineUid)

        lineGroup?.addItem(mp)
        mp.movable = true
        mp.title = "${snr} dB"
        mp.lineLabel = "${snr} dB"
        mp.hideLabels(false)
        mp.toggleMetaData("labels_on", true)
        links.add(Link(lineUid, startPoint, endPoint))
        for (item in markerLinkList) {
            if (item.markerId == linkToId) {
                item.links.add(Link(lineUid, endPoint, startPoint))
            }
        }
        handleLinkLineVisibility()
    }

    private fun updateLinkLines(markerItem: MarkerDataModel){

        // many to many now ;)
        markersList.forEach{
            removeLinkLinesFromMap(it)
            getLinksBetween(it)
        }

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
            marker.version,
            marker.bounds
        )
    }

    private fun getModifiedReceiver(pReceiver: Receiver): Receiver {
        return Receiver(pReceiver.alt, 0.0, 0.0, pReceiver.rxg, pReceiver.rxs)
    }

    private fun showHidePlayBtn(){
        if(templateView.findViewById<ImageButton>(R.id.btnPlayBtn).visibility == View.VISIBLE){
            templateView.findViewById<ImageButton>(R.id.btnPlayBtn).visibility = View.GONE;
            templateView.findViewById<ProgressBar>(R.id.progressBar).visibility = View.VISIBLE;
        }else{
            templateView.findViewById<ImageButton>(R.id.btnPlayBtn).visibility = View.VISIBLE;
            templateView.findViewById<ProgressBar>(R.id.progressBar).visibility = View.GONE;
        }

    }
    // The Area API simulates one transmitter only and can use a CPU or a GPU
    private fun sendSingleSiteDataToServer(marker: TemplateDataModel?) {
        if (pluginContext.isConnected()) {
            marker?.let {
                val markerData = getModifiedMarker(marker)

                markerData.receiver = getModifiedReceiver(marker.receiver)
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
                            showHidePlayBtn();
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

    private fun sendMultiSiteDataToServer(markerData: MultisiteRequest?) {
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

        val data = mapView.rootGroup.findMapGroup(pluginContext.getString(R.string.drawing_objects))

         for (it in data.items){
             if(it.title.contains(" dB")){
                 Log.d(TAG,"Removing "+it.toString())
                 data.removeItem(it)
             }

         }
    }

    private fun getAllFilesFromAssets(): List<String>? {
        val assetManager = pluginContext.assets
        return assetManager.list("")?.filter { it.endsWith(Constant.TEMPLATE_FORMAT) }
    }

    private fun setDataFromPref() {
        svMode.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCalculationMode, false) ?: false
        cbCoverageLayer.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sKmzVisibility, true) ?: true
        cbLinkLines.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sLinkLinesVisibility, true) ?: true
        cbCoOptTimeRefresh.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshEnabled, true) ?: true
        etCoOptTimeInterval.setText((sharedPrefs?.get(Constant.PreferenceKey.sCoOptTimeRefreshInterval, 60L) ?: 60L).toString())
        cbCoOptDistanceRefresh.isChecked = sharedPrefs?.get(Constant.PreferenceKey.sCoOptDistanceRefreshEnabled, false) ?: false
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
                bounds, object : CloudRFLayerListener {
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

    private fun addLayer(filePath: String, bounds: List<Double>) {
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
            SHOW_PLUGIN -> {
                showDropDown(
                    templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this
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
                } catch (e: java.lang.Exception) {
                    Log.e(TAG, "error", e)
                }

                setDataFromPref()
                Constant.sServerUrl = etLoginServerUrl?.text.toString()
                Constant.sAccessToken = sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "") ?: ""
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
            "${pluginContext.getString(R.string.delete)} ${layer.description}${
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

        mapView.removeLayer(
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

        removeLinkLinesFromMap(marker)
        removeMarkerFromList(marker)
        removeMarkerFromMap(marker)

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
                toast("Add a radio marker to the map first")
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
                        sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "") ?: "", RetrofitClient.BASE_URL, dateTime)
                }
            }
        }
     }

    fun toast(message: String) {
        pluginContext.toast(message)
    }

    fun drawLine(p1: Array<Double>, p2: Array<Double>, label: Boolean, azi: Double, elev: Double) {
        val line = Polyline(UUID.randomUUID().toString())
        line.toggleMetaData("labels_on", label)
        line.setPoints(arrayOf(GeoPoint(p1[0], p1[1]), GeoPoint(p2[0], p2[1])))
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

    private fun getAllAvailableMarkers(): List<MapItem> {
        // Get all markers from contacts
        val callsignMarkers = allContacts.mapNotNull {
            mapView.rootGroup.deepFindItem("uid", it.getUID())
        }.toMutableList()
        Log.d(TAG, "Found ${callsignMarkers.size} map items from ${allContacts.size} contacts.")

        // Add self marker
        val self = mapView.selfMarker
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

        val allCotMarkers = collectAllMarkers(mapView.rootGroup)
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
    
    private fun populateCoOptList() {
        val coOptRecyclerView = coOptView.findViewById<RecyclerView>(R.id.co_opt_recycler_view)
        coOptRecyclerView.layoutManager = LinearLayoutManager(pluginContext)

        // Get all available markers (this will be our master list)
        val allAvailableMarkers = getAllAvailableMarkers()
        
        // Create adapter with initial full list
        val coOptAdapter = CoOptAdapter(pluginContext, allAvailableMarkers, templateItems, sharedPrefs) {
            createTemplateSpinnerAdapter()
        }
        coOptRecyclerView.adapter = coOptAdapter

        // Sort markers to put checked ones at the top
        sortMarkersWithCheckedFirst(coOptAdapter)

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

    private fun sortMarkersWithCheckedFirst(adapter: CoOptAdapter) {
        val currentMarkers = adapter.getCurrentMarkers()

        // Sort markers: checked/enabled first, then by original order
        val sortedMarkers = currentMarkers.sortedWith(compareBy<MapItem> { mapItem ->
            // Get the checkbox state for this marker
            val config = adapter.coOptConfigurations[mapItem.uid]
            val isChecked = config?.isEnabled ?: false
            
            // Return 0 for checked (top), 1 for unchecked (bottom)
            if (isChecked) 0 else 1
        }.thenBy { mapItem ->
            // Secondary sort: maintain original order within each group
            currentMarkers.indexOf(mapItem)
        })
        
        adapter.updateMarkers(sortedMarkers)
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
        runCoOptUpdate()

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
                    lastKnownLocations[uid] = GeoPoint(currentMarker.point.latitude, currentMarker.point.longitude)
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
                        runCoOptUpdate()
                        countdown = refreshIntervalSeconds
                        periodicUpdateJustHappened = true
                    } else {
                        countdown--
                    }
                } else {
                    nextUpdateTextView.visibility = View.GONE
                }

                if (distanceEnabled && !periodicUpdateJustHappened) {
                    checkDistanceAndRecalculate()
                }

                // 1 second count
                trackingHandler.postDelayed(this, 1000)
            }
        }
        trackingHandler.post(trackingRunnable as Runnable)
    }

    private fun runCoOptUpdate() {
        Constant.sAccessToken = sharedPrefs?.get(Constant.PreferenceKey.sApiKey, "") ?: ""
        var lastUpdatedMarker: MarkerDataModel? = null

        for ((uid, _) in coOptedMarkers) {
            val markerInList = markersList.find { it.coopted_uid == uid }
            val currentMarker = mapView.rootGroup.deepFindItem("uid", uid) as? PointMapItem

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
                    markerAdapter?.notifyItemChanged(index)
                    lastUpdatedMarker = markerInList
                }
            }

        }
        
        if (lastUpdatedMarker != null) {
            calculate(lastUpdatedMarker)
        }
    }

    private fun checkDistanceAndRecalculate() {
        var needsRecalculation = false
        var lastUpdatedMarkerForRecalc: MarkerDataModel? = null
        val refreshDistance = sharedPrefs?.get(Constant.PreferenceKey.sCoOptDistanceRefreshThreshold, 100.0) ?: 100.0

        for ((uid, settings) in coOptedMarkers) {
            val currentMarker = mapView.rootGroup.deepFindItem("uid", uid) as? PointMapItem ?: continue
            val lastLocation = lastKnownLocations[uid]

            if (lastLocation == null) {
                // This should not happen if we initialized properly, but just in case
                lastKnownLocations[uid] = GeoPoint(currentMarker.point.latitude, currentMarker.point.longitude)
                Log.d(TAG, "Late initialization of tracking for marker $uid")
            } else {
                val distanceMoved = lastLocation.distanceTo(currentMarker.point)

                
                if (distanceMoved >= refreshDistance) {
                    Log.d(TAG, "Marker $uid moved ${distanceMoved}m (threshold: ${refreshDistance}m)")
                    val markerInList = markersList.find { it.coopted_uid == uid }
                    if (markerInList != null) {
                        markerInList.markerDetails.transmitter?.lat = Math.round(currentMarker.point.latitude * 1e5).toDouble() / 1e5;
                        markerInList.markerDetails.transmitter?.lon = Math.round(currentMarker.point.longitude * 1e5).toDouble() / 1e5;

                        val altitude = Math.round(currentMarker.point.altitude)
                        val DTM_FILTER = ElevationManager.QueryParameters()
                        DTM_FILTER.elevationModel = ElevationData.MODEL_TERRAIN
                        val terrain = ElevationManager.getElevation(currentMarker.point.latitude,currentMarker.point.longitude,DTM_FILTER)

                        Log.d(TAG, "checkDistanceAndRecalculate() AGL= "+terrain.toString())

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
                            markerAdapter?.notifyItemChanged(index)
                        }
                        needsRecalculation = true
                        lastUpdatedMarkerForRecalc = markerInList
                        Log.d(TAG, "Marker $uid triggered distance recalculation (moved ${distanceMoved}m)")
                    }
                    // Update the last known location to the current position
                    lastKnownLocations[uid] = GeoPoint(currentMarker.point.latitude, currentMarker.point.longitude)
                }
            }
        }

        if (needsRecalculation && templateView.findViewById<ProgressBar>(R.id.progressBar).visibility == View.GONE) {
            calculate(lastUpdatedMarkerForRecalc)
        }
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

}