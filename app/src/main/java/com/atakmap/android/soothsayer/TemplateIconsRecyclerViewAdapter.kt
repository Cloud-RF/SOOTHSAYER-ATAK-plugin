package com.atakmap.android.soothsayer

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.atakmap.android.soothsayer.plugin.R

class TemplateIconsRecyclerViewAdapter(private val items: MutableList<Bitmap?>, private val iconView: ImageView?, private val iconPickerLayout: LinearLayout?, private val newTemplateLayout: LinearLayout?) :
    RecyclerView.Adapter<TemplateIconsRecyclerViewAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: ImageView = itemView.findViewById(R.id.ivTemplateIconItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.template_icons_item_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.button.setImageBitmap(items[position])
        holder.button.setOnClickListener {
            iconView?.setImageBitmap(items[position])

            iconPickerLayout?.visibility = View.GONE
            newTemplateLayout?.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = items.size

    fun addIcon(icon: Bitmap?) {
        items.add(icon)
        notifyItemInserted(items.size - 1)
    }
}