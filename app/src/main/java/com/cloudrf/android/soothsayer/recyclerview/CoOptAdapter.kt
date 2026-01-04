package com.cloudrf.android.soothsayer.recyclerview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView
import com.atakmap.android.gui.PluginSpinner
import com.atakmap.android.maps.MapItem
import com.atakmap.android.maps.Marker
import com.atakmap.android.maps.PointMapItem
import com.atakmap.android.maps.MapView
import com.atakmap.android.hierarchy.items.MapItemHierarchyListItem
import com.cloudrf.android.soothsayer.models.request.TemplateDataModel
import com.cloudrf.android.soothsayer.plugin.R
import android.graphics.drawable.GradientDrawable
import com.atakmap.android.preference.AtakPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator

/**
 * An adapter for the Co-Opt feature that receives a correctly styled adapter
 * for its spinners, reusing the exact same implementation as the main screen.
 */
class CoOptAdapter(
    private val context: Context,
    private var markers: List<MapItem>,
    private val templateItems: List<TemplateDataModel>,
    private val sharedPrefs: AtakPreferences?,
    private val getTemplateAdapter: () -> ArrayAdapter<TemplateDataModel>
) : RecyclerView.Adapter<CoOptAdapter.CoOptViewHolder>() {

    // This map holds the final configuration for each callsign, ready to be retrieved.
    val coOptConfigurations = mutableMapOf<String, CoOptConfiguration>()

    companion object {
        private const val PREF_KEY_CO_OPT_SELECTIONS = "co_opt_radio_selections"
        private const val PREF_KEY_CO_OPT_CHECKBOXES = "co_opt_checkbox_selections"
    }

    /**
     * This comment describes the purpose of the CoOptViewHolder inner class.
     * This ViewHolder holds the view components for a single row in the RecyclerView.
     */
    class CoOptViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.co_opt_item_checkbox)
        val markerIcon: ImageView = view.findViewById(R.id.co_opt_item_marker_icon)
        val callsignTextView: TextView = view.findViewById(R.id.co_opt_item_callsign)
        val templateSpinner: PluginSpinner = view.findViewById(R.id.co_opt_item_template_spinner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoOptViewHolder {
        // This comment explains that this method inflates the layout for each row.
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.co_opt_list_item, parent, false)
        return CoOptViewHolder(view)
    }

    override fun onBindViewHolder(holder: CoOptViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: CoOptViewHolder, position: Int, payloads: MutableList<Any>) {
        val marker = markers[position]
        val name = marker.title?.takeIf { it.isNotBlank() }
            ?: (marker as? Marker)?.getMetaString("callsign", null)?.takeIf { it.isNotBlank() }
            ?: marker.getMetaString("name", null)?.takeIf { it.isNotBlank() }
            ?: marker.uid
        holder.callsignTextView.text = name

        // Get or create the configuration for this marker
        val config = coOptConfigurations.getOrPut(marker.uid) { CoOptConfiguration() }

        val validTemplates = templateItems.filter { it.template != null }

        // Restore previous template selection from prefs
        sharedPrefs?.let { prefs ->
            val selectionsJson = prefs.get(PREF_KEY_CO_OPT_SELECTIONS, "{}")
            val type = object : TypeToken<Map<String, String>>() {}.type
            val selections = Gson().fromJson<Map<String, String>>(selectionsJson, type)
            val savedTemplateName = selections[marker.uid]

            if (savedTemplateName != null) {
                val savedTemplate = validTemplates.find { it.template?.name == savedTemplateName }
                if (savedTemplate != null) {
                    config.template = savedTemplate
                }
            }
        }

        // Restore previous checkbox state from prefs
        sharedPrefs?.let { prefs ->
            val checkboxJson = prefs.get(PREF_KEY_CO_OPT_CHECKBOXES, "{}")
            val type = object : TypeToken<Map<String, Boolean>>() {}.type
            val checkboxStates = Gson().fromJson<Map<String, Boolean>>(checkboxJson, type)
            val savedCheckboxState = checkboxStates[marker.uid]

            if (savedCheckboxState != null) {
                config.isEnabled = savedCheckboxState
            }
        }

        // We get the correctly styled adapter from the main receiver and set it.
        val templateAdapter = getTemplateAdapter()
        holder.templateSpinner.adapter = templateAdapter

        // It's important to remove the listeners before setting the state to avoid unwanted triggers
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.templateSpinner.onItemSelectedListener = null

        // Restore the state of the checkbox and spinner
        holder.checkBox.isChecked = config.isEnabled
        val selectionIndex = validTemplates.indexOf(config.template)
        if (selectionIndex != -1) {
            holder.templateSpinner.setSelection(selectionIndex)
        }

        // Set marker icon - prioritize color shapes for contacts, complex icons for CoT
        try {
            // First try: Check if this is a contact or has a simple color
            var color = marker.iconColor
            if (color == 0 || color == -1) {
                color = marker.getMetaInteger("color", -1)
            }

            // Check if this marker has a CoT type that suggests it's a tactical symbol
            val cotType = marker.getMetaString("type", "")
            val hasComplexIcon = cotType.isNotEmpty() && (
                cotType.startsWith("a-") || // Military symbols often start with a-
                cotType.startsWith("b-") || // Position reports often start with b-
                cotType.contains("mil-") || // Military standard
                marker.getMetaBoolean("mil2525", false) // Explicit MIL-STD flag
            )

            if (color != -1 && color != 0 && !hasComplexIcon) {
                // Use color shape - works great for contacts and simple markers
                holder.markerIcon.setImageDrawable(null)
                
                // Determine shape based on marker type or metadata
                val isSquare = marker.getMetaString("shape", "").equals("square", true) ||
                              marker.getMetaString("affiliation", "").equals("hostile", true)
                
                if (isSquare) {
                    // Use square shape for hostile/square markers
                    holder.markerIcon.setBackgroundResource(R.drawable.contact_color_square)
                } else {
                    // Use circle shape for friendly/neutral markers
                    holder.markerIcon.setBackgroundResource(R.drawable.contact_color_circle)
                }
                
                val background = holder.markerIcon.background?.mutate() as? GradientDrawable
                background?.setColor(color)
                holder.markerIcon.visibility = View.VISIBLE
            } else {
                // Use complex icon for tactical symbols and markers without colors
                val mapView = MapView.getMapView()
                val hierarchyListItem = MapItemHierarchyListItem(mapView, marker)
                val markerDrawable = hierarchyListItem.getIconDrawable()

                if (markerDrawable != null) {
                    // Scale the drawable to better fit our 24dp ImageView
                    val scaledDrawable = markerDrawable.mutate()
                    scaledDrawable.setBounds(0, 0, 
                        (24 * context.resources.displayMetrics.density).toInt(),
                        (24 * context.resources.displayMetrics.density).toInt())
                    
                    holder.markerIcon.setImageDrawable(scaledDrawable)
                    holder.markerIcon.visibility = View.VISIBLE
                    // Clear background since we're showing the actual icon
                    holder.markerIcon.background = null
                } else {
                    // No icon or color available
                    holder.markerIcon.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            // Fallback to hiding icon if both methods fail
            holder.markerIcon.visibility = View.GONE
        }

        // Re-set listeners to update the configuration when the user interacts with the row
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            config.isEnabled = isChecked
            saveCheckboxState(marker.uid, isChecked)
            // Re-sort the list to move checked items to top
            sortMarkersWithCheckedFirst()
        }

        holder.templateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos >= 0 && pos < validTemplates.size) {
                    val selectedTemplate = validTemplates[pos]
                    config.template = selectedTemplate

                    // Save the selection
                    sharedPrefs?.let { prefs ->
                        val selectionsJson = prefs.get(PREF_KEY_CO_OPT_SELECTIONS, "{}")
                        val type = object : TypeToken<MutableMap<String, String>>() {}.type
                        val selections = Gson().fromJson<MutableMap<String, String>>(selectionsJson, type)
                        selectedTemplate.template?.name?.let { name ->
                            selections[marker.uid] = name
                        }
                        prefs.set(PREF_KEY_CO_OPT_SELECTIONS, Gson().toJson(selections))
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                config.template = null
            }
        }

        // Handle flash animation if this is a flash update
        if (payloads.contains("FLASH")) {
            flashItemBackground(holder.itemView)
        }
    }

    private fun flashItemBackground(itemView: View) {
        // Create a glow effect using background color animation
        val originalBackground = itemView.background
        val flashColor = android.graphics.Color.parseColor("#4CAF50") // Green glow
        val transparent = android.graphics.Color.TRANSPARENT
        
        // Create color animation from transparent to flash color and back
        val colorAnimator = android.animation.ValueAnimator.ofArgb(transparent, flashColor, transparent)
        colorAnimator.duration = 800 // 0.8 seconds total - fast tactical flash
        colorAnimator.repeatCount = 0
        
        colorAnimator.addUpdateListener { animator ->
            val color = animator.animatedValue as Int
            itemView.setBackgroundColor(color)
        }
        
        colorAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Restore original background
                itemView.background = originalBackground
            }
        })
        
        colorAnimator.start()
    }

    override fun getItemCount(): Int = markers.size

    /**
     * Update the markers list and refresh the RecyclerView
     * Used for search filtering
     */
    fun updateMarkers(newMarkers: List<MapItem>) {
        markers = newMarkers
        notifyDataSetChanged()
    }

    /**
     * Get the current markers list (useful for tap-to-scroll functionality)
     */
    fun getCurrentMarkers(): List<MapItem> {
        return markers
    }

    /**
     * Flash an item to highlight it for the user
     */
    fun flashItem(markerUid: String) {
        val position = markers.indexOfFirst { it.uid == markerUid }
        if (position >= 0) {
            notifyItemChanged(position, "FLASH")
        }
    }

    /**
     * Sort markers to put checked/enabled ones at the top
     */
    private fun sortMarkersWithCheckedFirst() {
        val sortedMarkers = markers.sortedWith(compareBy<MapItem> { mapItem ->
            // Get the checkbox state for this marker
            val config = coOptConfigurations[mapItem.uid]
            val isChecked = config?.isEnabled ?: false
            
            // Return 0 for checked (top), 1 for unchecked (bottom)
            if (isChecked) 0 else 1
        }.thenBy { mapItem ->
            // Secondary sort: maintain original order within each group
            markers.indexOf(mapItem)
        })
        
        updateMarkers(sortedMarkers)
    }

    // Data class to hold the configuration for a single row
    data class CoOptConfiguration(
        var isEnabled: Boolean = false,
        var template: TemplateDataModel? = null
    )

    /**
     * Save checkbox state to preferences
     */
    private fun saveCheckboxState(markerUid: String, isChecked: Boolean) {
        sharedPrefs?.let { prefs ->
            val checkboxJson = prefs.get(PREF_KEY_CO_OPT_CHECKBOXES, "{}")
            val type = object : TypeToken<MutableMap<String, Boolean>>() {}.type
            val checkboxStates = Gson().fromJson<MutableMap<String, Boolean>>(checkboxJson, type)
            checkboxStates[markerUid] = isChecked
            prefs.set(PREF_KEY_CO_OPT_CHECKBOXES, Gson().toJson(checkboxStates))
        }
    }
} 