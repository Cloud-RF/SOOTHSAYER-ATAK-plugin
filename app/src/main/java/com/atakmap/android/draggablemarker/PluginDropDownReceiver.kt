package com.atakmap.android.draggablemarker

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
import com.atakmap.android.draggablemarker.models.MarkerDataModel
import com.atakmap.android.draggablemarker.models.TemplateDataModel
import com.atakmap.android.draggablemarker.plugin.R
import com.atakmap.android.draggablemarker.recyclerview.RecyclerViewAdapter
import com.atakmap.android.draggablemarker.util.*
import com.atakmap.android.dropdown.DropDown.OnStateListener
import com.atakmap.android.dropdown.DropDownReceiver
import com.atakmap.android.maps.*
import com.atakmap.android.util.SimpleItemSelectedListener
import com.atakmap.coremap.log.Log
import com.atakmap.coremap.maps.assets.Icon
import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.util.*


class PluginDropDownReceiver(
    mapView: MapView?,
    private val pluginContext: Context
) : DropDownReceiver(mapView), OnStateListener, MapEventDispatcher.MapEventDispatchListener {
    // Remember to use the PluginLayoutInflater if you are actually inflating a custom view.
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
    private val templateItems: ArrayList<TemplateDataModel> = ArrayList()
    private var markerAdapter: RecyclerViewAdapter? = null
    private val mItemType: String = "custom-type"

    init {
        initViews()
        initListeners()
    }

    private fun initViews() {
        pluginContext.createAndStoreFiles(getAllFilesFromAssets())

        etServerUrl = settingView.findViewById(R.id.etServerUrl)
        etServerUrl?.setText(Constant.SERVER_URL)
        etApiKey = settingView.findViewById(R.id.etApiKey)

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

                Log.d(TAG, "onItemSelected......$position")
                selectedMarkerType = templateItems[position]
            }
        }

        spinner.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // If user added any other template to that folder then below code will get that template if it is valid one and show in the list.
                    val extraTemplates = getTemplatesFromFolder()
                    if(extraTemplates.isEmpty()){ // add default files again so that folder is not empty.
                        pluginContext.createAndStoreFiles(getAllFilesFromAssets())
                        templateItems.clear()
                        templateItems.addAll(getTemplatesFromFolder())
                    }else{
                        if (extraTemplates.size != templateItems.size) {
                            Log.d(TAG, "extraTemplates : ${extraTemplates.size}")
                            spinner.adapter?.let { adapter ->
                                if (adapter is ArrayAdapter<*>) {
                                    templateItems.clear()
                                    templateItems.addAll(extraTemplates)
                                    adapter.notifyDataSetChanged()
                                    Log.d(TAG, "extraTemplates : addeddddd")
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
        initRecyclerview()
    }

    private fun initListeners() {
        // The button bellow shows settings layout and hide the actual layout
        val btnOpenSettings: ImageView = templateView.findViewById(R.id.ivSettings)
        btnOpenSettings.setOnClickListener {
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
        marker.type = "a-f-G-U-C-I"
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
                    MapEvent.ITEM_DRAG_DROPPED -> {
                        Log.d(TAG, "mapItem : DragDropped ")

                        val latitude = mapItem.clickPoint.latitude
                        val longitude = mapItem.clickPoint.longitude
                        Log.d(
                            TAG,
                            "DragDropped latitude: $latitude Longitude: $longitude Marker_id: ${mapItem.uid}"
                        )
                        // update the lat and lon of that marker.
                        val item = markersList.find { it.markerID == uid }
                        item?.markerDetails?.transmitter?.lat = latitude.roundValue()
                        item?.markerDetails?.transmitter?.lon = longitude.roundValue()
                        markerAdapter?.notifyDataSetChanged()
                    }
                }
            }
        }

        // add marker to list
        selectedMarkerType?.let {
            val markerItem = MarkerDataModel(uid, it)
            markerItem.markerDetails.transmitter?.lat = location.latitude.roundValue()
            markerItem.markerDetails.transmitter?.lon = location.longitude.roundValue()
            markersList.add(markerItem)
            markerAdapter?.notifyDataSetChanged()
        }
        Log.d(TAG, "${markersList.size} listData : ${Gson().toJson(markersList)}")
    }

    private fun removeMarkerFromList(item: MarkerDataModel?) {
        item?.let {
            markersList.remove(it)
            markerAdapter?.notifyDataSetChanged()
        }
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
            // To check custom-type marker when plugin is visible.
            Log.d(TAG, "Group Items: ${mapView.rootGroup.items}")
            for (item in mapView.rootGroup.items) {
                if (item.type == mItemType)
                    Log.d(TAG, "group mapItem : ${item.uid} type: ${item.type}")
            }
        }
    }

    override fun onDropDownSelectionRemoved() {}
    override fun onDropDownVisible(v: Boolean) {}

    override fun onDropDownSizeChanged(width: Double, height: Double) {}
    override fun onDropDownClose() {}

    companion object {
        val TAG: String? = PluginDropDownReceiver::class.java.simpleName
        const val SHOW_PLUGIN = "com.atakmap.android.plugintemplate.SHOW_PLUGIN"
    }

    override fun onMapEvent(event: MapEvent?) {
        Log.d(TAG, "initListeners ITEM_ADDED: ${event?.type}")
    }
}
