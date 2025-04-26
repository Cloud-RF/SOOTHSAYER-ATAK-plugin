package com.atakmap.android.soothsayer.recyclerview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.atakmap.android.soothsayer.models.common.MarkerDataModel
import com.atakmap.android.soothsayer.plugin.R
import com.atakmap.android.maps.MapView
import com.atakmap.android.soothsayer.PluginDropDownReceiver
import com.atakmap.android.soothsayer.util.base64StringToBitmap
import com.atakmap.coremap.log.Log

/**
 * Adapter used to display content in a RecyclerView
 */
class RecyclerViewAdapter(
    private val list: ArrayList<MarkerDataModel>,
    pMapView: MapView,
    plugin: Context?,
    private val onItemRemove: (MarkerDataModel) -> Unit,
    private val onItemSelected: (Int, MarkerDataModel) -> Unit,
) :
    RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder?>() {
    private val mapView: MapView
    private val inflater: LayoutInflater

    init {
        mapView = pMapView
        inflater = LayoutInflater.from(plugin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(inflater.inflate(R.layout.item_layout_templates, parent, false))
    }

    @SuppressLint("StringFormatMatches")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position].markerDetails
        holder.markerDetails.text =  item.template.name
        holder.tvLocation.text = holder.tvLocation.context.getString(R.string.location_details, item.transmitter?.lat ?: "",
            item.transmitter?.lon ?: "", item.transmitter?.alt ?: "", item.transmitter?.txw ?: "")
        holder.ivRemove.setOnClickListener {
            onItemRemove(list[position])
        }
        holder.itemView.setOnClickListener {
            onItemSelected(position, list[position])
        }
        Log.d(PluginDropDownReceiver.TAG, "onBindViewHolder : ${item.customIcon} ")
        holder.ivMarker.apply {
            val drawable = if (item.customIcon == null) {
                ContextCompat.getDrawable(holder.ivMarker.context, R.drawable.marker_icon_svg)
            } else {
                item.customIcon.base64StringToBitmap()?.let { BitmapDrawable(resources, it) }
            }
            setImageDrawable(drawable ?: ContextCompat.getDrawable(holder.ivMarker.context, R.drawable.marker_icon_svg))
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view){
        val markerDetails: TextView
        val tvLocation: TextView
        val ivRemove: ImageView
        val ivMarker: ImageView

        init {
            markerDetails = view.findViewById(R.id.tvMarkerDetails)
            tvLocation = view.findViewById(R.id.tvLocation)
            ivRemove = view.findViewById(R.id.ivRemove)
            ivMarker = view.findViewById(R.id.ivMarker)
        }
    }
}