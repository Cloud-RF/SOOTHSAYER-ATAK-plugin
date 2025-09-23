package com.atakmap.android.soothsayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.atakmap.android.soothsayer.models.common.MarkerDataModel
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.plugin.R
import com.atakmap.android.soothsayer.util.base64StringToBitmap

data class MutableTuple<A, B, C>(
    var first: A,
    var second: B,
    var third: C,
)

class TemplateRecyclerViewAdapter(private val items: MutableList<MutableTuple<TemplateDataModel, Boolean, String?>>,
                                  private val onItemSelected: (Int, MutableTuple<TemplateDataModel, Boolean, String?>) -> Unit,) :
    RecyclerView.Adapter<TemplateRecyclerViewAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.btnLoadTemplate)
        val icon: ImageView = itemView.findViewById(R.id.ivTemplateIcon)
        val radio: CheckBox = itemView.findViewById(R.id.cbSelectTemplate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.template_item_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tuple = items[position]
        holder.button.text = tuple.first.template.name
        holder.icon.setImageBitmap(tuple.third?.base64StringToBitmap())

        // detach old listener to prevent unwanted callbacks
        holder.radio.setOnCheckedChangeListener(null)
        holder.radio.isChecked = tuple.second

        // reattach listener
        holder.radio.setOnCheckedChangeListener { _, isChecked ->
            tuple.second = isChecked
            onItemSelected(position, tuple)
        }
    }

    override fun getItemCount(): Int = items.size

    fun selectAll(select: Boolean) {
        items.forEach { it.second = select }
        notifyDataSetChanged()
    }

    companion object {
        const val MAX_ITEMS = 50  // Can increase and decrease the limit.
    }
}