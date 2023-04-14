package com.atakmap.android.draggablemarker

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.*
import com.atak.plugins.impl.PluginLayoutInflater
import com.atakmap.android.draggablemarker.models.MarkerDataModel
import com.atakmap.android.draggablemarker.models.TemplateDataModel
import com.atakmap.android.draggablemarker.plugin.R
import com.atakmap.android.draggablemarker.util.Constant
import com.atakmap.android.draggablemarker.util.getBitmap
import com.atakmap.android.dropdown.DropDown.OnStateListener
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.ipc.AtakBroadcast
import com.atakmap.android.maps.MapView
import com.atakmap.android.maps.Marker
import com.atakmap.android.util.SimpleItemSelectedListener
import com.atakmap.coremap.log.Log
import com.atakmap.coremap.maps.assets.Icon
import com.google.gson.Gson
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList


class PluginDropDownReceiver(
    mapView: MapView?,
    private val pluginContext: Context
) : DropDownReceiver(mapView), OnStateListener {
    // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
    // In this case, using it is not necessary - but I am putting it here to remind
    // developers to look at this Inflator
    private val templateView: View = PluginLayoutInflater.inflate(
        pluginContext,
        R.layout.main_layout, null
    )
    private val mainLayout: LinearLayout = templateView.findViewById(R.id.llMain)
    private val settingView = templateView.findViewById<LinearLayout>(R.id.ilSettings)
    private var etServerUrl: EditText? = null
    private var etApiKey: EditText? = null
    private var markersList: ArrayList<MarkerDataModel> = ArrayList()
    private var selectedMarkerType: TemplateDataModel? = null

    /**************************** CONSTRUCTOR  */
    init {
        initViews()
        initListeners()
    }

    private fun initViews() {
        etServerUrl = settingView.findViewById(R.id.etServerUrl)
        etServerUrl?.setText(Constant.SERVER_URL)
        etApiKey = settingView.findViewById(R.id.etApiKey)

        // Set Template spinner list from json files.
        val spinner: Spinner = templateView.findViewById(R.id.spTemplate)
        val items: ArrayList<TemplateDataModel> = ArrayList()
        items.addAll(getAllTemplates())
        val adapter: ArrayAdapter<TemplateDataModel> = object :
            ArrayAdapter<TemplateDataModel>(pluginContext, R.layout.spinner_item_layout, items) {
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

                Log.d(TAG, "onItemSelected......$position")
                selectedMarkerType = items[position]
//                if (view is TextView) view.text = items[position].template.name
            }
        }
    }

    private fun initListeners() {
        // The button bellow shows settings layout and hide the actual layout
        val btnOpenSettings: ImageView = templateView.findViewById(R.id.ivSettings)
        btnOpenSettings.setOnClickListener { v: View? ->
            mainLayout.visibility = View.GONE
            settingView.visibility = View.VISIBLE
        }

        // The button bellow shows settings layout and hide the actual layout
        val btnSave = templateView.findViewById<Button>(R.id.btnSave)
        btnSave.setOnClickListener {
            if (isValidSettings()) {
                moveBackToMainLayout()
            }
        }

        // The ImageView bellow shows settings layout and hide the actual layout
        val ivBack = settingView.findViewById<ImageView>(R.id.ivBack)
        ivBack.setOnClickListener {
            moveBackToMainLayout()
        }

        // The button bellow add marker on the map
        val btnAddMarker = templateView.findViewById<Button>(R.id.btnAddMarker)
        btnAddMarker.setOnClickListener {
            addCustomMarker()
        }
    }

    private fun moveBackToMainLayout() {
        mainLayout.visibility = View.VISIBLE
        settingView.visibility = View.GONE
    }

    private fun isValidSettings(): Boolean {
        return URLUtil.isValidUrl(etServerUrl?.text.toString()) && etApiKey?.text?.trim()
            ?.isNotEmpty() ?: false
    }

    private fun addCustomMarker() {
        val uid = UUID.randomUUID().toString()
        val location = mapView.centerPoint.get()

        val marker = Marker(location, uid)
//        marker.setAlwaysShowText(true)
        marker.type = "a-f-G-U-C-I"
        // m.setMetaBoolean("disableCoordinateOverlay", true); // used if you don't want the coordinate overlay to appear
        marker.setMetaBoolean("readiness", true)
        marker.setMetaBoolean("archive", true)
        marker.setMetaString("how", "h-g-i-g-o")
        marker.setMetaBoolean("editable", true)
        marker.setMetaBoolean("movable", true)
        marker.setMetaBoolean("removable", true)
        marker.setMetaString("entry", "user")
        marker.setMetaString("callsign", selectedMarkerType?.template?.name?:"Test Marker")
        marker.title = selectedMarkerType?.template?.name?:"Test Marker"
        marker.type = "custom-type"

        //Add custom icon
        val icon: Bitmap? = pluginContext.getBitmap(R.drawable.marker_icon_svg)
        val baos = ByteArrayOutputStream()
        icon?.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val b = baos.toByteArray()
        val encoded = "base64://" + Base64.encodeToString(b, Base64.NO_WRAP or Base64.URL_SAFE)
        val markerIconBuilder = Icon.Builder().setImageUri(0, encoded)
        marker.icon = markerIconBuilder.build()
        mapView.rootGroup.addItem(marker)


        // This method get callback when we drag a marker.
        marker.addOnStateChangedListener { marker ->
            val latitude = marker.geoPointMetaData.get().latitude
            val longitude = marker.geoPointMetaData.get().longitude
            Log.d(
                TAG,
                "addOnStateChangedListener latitude: $latitude Longitude: $longitude Marker_id: $marker.uid"
            )
            // update the lat and lon of that marker.
            val item = markersList.find {  it.markerID == uid }
                item?.markerDetails?.transmitter?.lat = latitude
                item?.markerDetails?.transmitter?.lon = longitude
        }
        selectedMarkerType?.let {
            markersList.add(MarkerDataModel(uid, it))
        }
        Log.d(TAG, "${markersList.size} listData : ${Gson().toJson(markersList)}")
    }

    private fun getAllTemplates(): ArrayList<TemplateDataModel> {
        val assetManager = pluginContext.assets
        val fileList = assetManager.list("")?.filter { it.endsWith(".json") }
        Log.d(TAG, "getAllTemplates......${fileList?.size}")
        val templateList: ArrayList<TemplateDataModel> = ArrayList()
        fileList?.forEach { fileName ->
            val jsonString = assetManager.open(fileName).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            templateList.add(Gson().fromJson(jsonString, TemplateDataModel::class.java))
            Log.d(TAG, "fileName: $fileName  \n${jsonObject.toString()}")
        }
        return templateList
    }

    /**************************** PUBLIC METHODS  */
    public override fun disposeImpl() {}

    /**************************** INHERITED METHODS  */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == SHOW_PLUGIN) {
            Log.d(TAG, "showing plugin drop down")
            showDropDown(
                templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                HALF_HEIGHT, false, this
            )
        }
    }

    override fun onDropDownSelectionRemoved() {}
    override fun onDropDownVisible(v: Boolean) {}
    override fun onDropDownSizeChanged(width: Double, height: Double) {}
    override fun onDropDownClose() {}

    companion object {
        val TAG = PluginDropDownReceiver::class.java
            .simpleName
        const val SHOW_PLUGIN = "com.atakmap.android.plugintemplate.SHOW_PLUGIN"
    }
}
