package com.atakmap.android.draggablemarker.recyclerview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.atakmap.android.draggablemarker.models.MarkerDataModel
import com.atakmap.android.draggablemarker.plugin.R
import com.atakmap.android.maps.MapView

/**
 * Adapter used to display content in a RecyclerView
 */
class RecyclerViewAdapter(
    private val list: ArrayList<MarkerDataModel>,
    pMapView: MapView,
    plugin: Context?,
    private val onItemRemove: (MarkerDataModel) -> Unit
) :
    RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder?>() {
    private val mapView: MapView
    private val inflater: LayoutInflater
//    private val _items: MutableList<MarkerDataModel?> = ArrayList()

    init {
        mapView = pMapView
        inflater = LayoutInflater.from(plugin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.item_layout_templates, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position].markerDetails
        holder.markerDetails.text = holder.markerDetails.context.getString(
            R.string.marker_details,
            item.template.name,
            item.transmitter?.lat ?: "",
            item.transmitter?.lon ?: ""
        )
        holder.ivRemove.setOnClickListener {
            onItemRemove(list[position])
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view){
        val markerDetails: TextView
        val ivRemove: ImageView

        init {
            markerDetails = view.findViewById(R.id.tvMarkerDetails)
            ivRemove = view.findViewById(R.id.ivRemove)
        }
    }
}