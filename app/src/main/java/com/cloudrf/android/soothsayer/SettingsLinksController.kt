package com.cloudrf.android.soothsayer

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.cloudrf.android.soothsayer.plugin.R
import java.lang.ref.WeakReference

class SettingsLinksController(
    context: Context,
    settingsOptionsView: View,
    colourPickerView: View
) {
    private val contextRef = WeakReference(context)
    private val settingsViewRef = WeakReference(settingsOptionsView)
    private val colourViewRef = WeakReference(colourPickerView)

    private fun ctx(): Context? = contextRef.get()
    private fun settingsView(): View? = settingsViewRef.get()
    private fun colourView(): View? = colourViewRef.get()

    private var colourPickerCurId = 0
    var linkUnits = "dB"

    private val optionsColour3 = ColourRef(0xFF00FF00.toInt())
    private val optionsColour2 = ColourRef(0xFFFFb600.toInt())
    private val optionsColour1 = ColourRef(0xFFFF0000.toInt())
    private val optionsColour4 = ColourRef(0xFF222222.toInt())

    private val curColourView: View = colourPickerView.findViewById(R.id.colourPickerCurColour)
    private val redBar: SeekBar = colourPickerView.findViewById(R.id.colourPickerRedBar)
    private val greenBar: SeekBar = colourPickerView.findViewById(R.id.colourPickerGreenBar)
    private val blueBar: SeekBar = colourPickerView.findViewById(R.id.colourPickerBlueBar)

    private val btnOptionsColour1: Button = settingsOptionsView.findViewById(R.id.btnOptionsColour1)
    private val btnOptionsColour2: Button = settingsOptionsView.findViewById(R.id.btnOptionsColour2)
    private val btnOptionsColour3: Button = settingsOptionsView.findViewById(R.id.btnOptionsColour3)
    private val btnOptionsColour4: Button = settingsOptionsView.findViewById(R.id.btnOptionsColour4)

    private val optionsUnitSwitch: Switch = settingsOptionsView.findViewById(R.id.optionsUnitSwitch)
    private val optionsUnitViews = listOf<TextView>(
        settingsOptionsView.findViewById(R.id.optionsUnit1),
        settingsOptionsView.findViewById(R.id.optionsUnit2),
        settingsOptionsView.findViewById(R.id.optionsUnit3),
        settingsOptionsView.findViewById(R.id.optionsUnit4)
    )

    private val dbEdits = listOf<EditText>(
        settingsOptionsView.findViewById(R.id.db1),
        settingsOptionsView.findViewById(R.id.db2),
        settingsOptionsView.findViewById(R.id.db3),
        settingsOptionsView.findViewById(R.id.db4)
    )

    init {
        setupColourButtons()
        setupSeekBars()
        setupUnitSwitch()
        setupBackButton()
        setupPresetButtons()
        setupDbWatchers()
    }

    // region === private helpers ===

    private fun setupColourButtons() {
        setupColourButton(btnOptionsColour1, optionsColour1, 1)
        setupColourButton(btnOptionsColour2, optionsColour2, 2)
        setupColourButton(btnOptionsColour3, optionsColour3, 3)
        setupColourButton(btnOptionsColour4, optionsColour4, 4)
    }

    private fun setupColourButton(button: Button, colourRef: ColourRef, id: Int) {
        button.setOnClickListener {
            colourPickerCurId = id
            settingsView()?.visibility = View.GONE
            colourView()?.visibility = View.VISIBLE
            redBar.progress = Color.red(colourRef.value)
            greenBar.progress = Color.green(colourRef.value)
            blueBar.progress = Color.blue(colourRef.value)
        }
    }

    private fun setupSeekBars() {
        redBar.setOnSeekBarChangeListener(simpleListener {
            updateCurrentColour { c, p -> setRedChannel(c, p) }
        })
        greenBar.setOnSeekBarChangeListener(simpleListener {
            updateCurrentColour { c, p -> setGreenChannel(c, p) }
        })
        blueBar.setOnSeekBarChangeListener(simpleListener {
            updateCurrentColour { c, p -> setBlueChannel(c, p) }
        })
    }

    private fun updateCurrentColour(channel: (Int, Int) -> Int) {
        val c = getCurrentColour()
        c.value = channel(c.value, redBar.progress) // you can pass progress as needed
        curColourView.setBackgroundColor(c.value)
    }

    private fun setupBackButton() {
        colourView()?.findViewById<ImageView>(R.id.colourPickerBack)?.setOnClickListener {
            val colour = getCurrentColour().value
            when (colourPickerCurId) {
                1 -> btnOptionsColour1.setBackgroundColor(colour)
                2 -> btnOptionsColour2.setBackgroundColor(colour)
                3 -> btnOptionsColour3.setBackgroundColor(colour)
                4 -> btnOptionsColour4.setBackgroundColor(colour)
            }
            colourPickerCurId = 0
            colourView()?.visibility = View.GONE
            settingsView()?.visibility = View.VISIBLE
        }
    }

    private fun setupUnitSwitch() {
        optionsUnitSwitch.setOnCheckedChangeListener { _, checked ->
            linkUnits = if (checked) "dBm" else "dB"
            optionsUnitViews.forEach { it.text = linkUnits }
            val defaults = if (checked) listOf("-80","-90","-100","-110") else listOf("25","15","5","-5")
            dbEdits.forEachIndexed { i, e -> e.setText(defaults[i]) }
        }
    }

    private fun setupPresetButtons() {
        settingsView()?.findViewById<Button>(R.id.btnManetColours)?.setOnClickListener {
            presetColours(
                listOf(0xFFFF0000.toInt(),0xFFFFb600.toInt(),0xFF00FF00.toInt(),0xFF222222.toInt()),
                false,
                listOf("25","15","5","-5")
            )
        }
        settingsView()?.findViewById<Button>(R.id.btnLpwanColours)?.setOnClickListener {
            presetColours(
                listOf(0xFFf4fc05.toInt(),0xFF00FF00.toInt(),0xFF055cfc.toInt(),0xFFc305fc.toInt()),
                true,
                listOf("-105","-115","-125","-135")
            )
        }
    }

    private fun presetColours(colours: List<Int>, switch: Boolean, values: List<String>) {
        listOf(optionsColour1,optionsColour2,optionsColour3,optionsColour4).forEachIndexed { i,c->
            c.value = colours[i]
        }
        listOf(btnOptionsColour1,btnOptionsColour2,btnOptionsColour3,btnOptionsColour4).forEachIndexed { i,b->
            b.setBackgroundColor(colours[i])
        }
        optionsUnitSwitch.isChecked = switch
        dbEdits.forEachIndexed { i,e -> e.setText(values[i]) }
    }

    private fun setupDbWatchers() {
        dbEdits.forEach { edit ->
            edit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(e: Editable?) {
                    val t = e?.toString() ?: return
                    if (t.isEmpty() || t=="-") return
                    var v = t.toInt()
                    val min = if (optionsUnitSwitch.isChecked) -140 else -20
                    val max = if (optionsUnitSwitch.isChecked) 0 else 90
                    if (v < min) v = min
                    if (v > max) v = max
                    if (v.toString() != t) {
                        edit.removeTextChangedListener(this)
                        edit.setText(v.toString())
                        edit.addTextChangedListener(this)
                    }
                }
            })
        }
    }

    private fun getCurrentColour(): ColourRef = when (colourPickerCurId) {
        1 -> optionsColour1
        2 -> optionsColour2
        3 -> optionsColour3
        4 -> optionsColour4
        else -> ColourRef(0)
    }

    fun getLineColour(db: Double): Int? {
        // safely get the view from the WeakReference
        val settingsView = settingsViewRef.get() ?: return null

        val db1 = settingsView.findViewById<EditText>(R.id.db1)
            .text.toString().toDoubleOrNull() ?: return null
        val db2 = settingsView.findViewById<EditText>(R.id.db2)
            .text.toString().toDoubleOrNull() ?: return null
        val db3 = settingsView.findViewById<EditText>(R.id.db3)
            .text.toString().toDoubleOrNull() ?: return null
        val db4 = settingsView.findViewById<EditText>(R.id.db4)
            .text.toString().toDoubleOrNull() ?: return null

        return when {
            db > db1 -> optionsColour1.value
            db > db2 -> optionsColour2.value
            db > db3 -> optionsColour3.value
            db > db4 -> optionsColour4.value
            else     -> null
        }
    }

    private fun simpleListener(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { onChange(progress) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        }

    private fun setRedChannel(c: Int, red: Int) = (c and 0xFF00FFFF.toInt()) or ((red and 0xFF) shl 16)
    private fun setGreenChannel(c: Int, g: Int) = (c and 0xFFFF00FF.toInt()) or ((g and 0xFF) shl 8)
    private fun setBlueChannel(c: Int, b: Int) = (c and 0xFFFFFF00.toInt()) or (b and 0xFF)

    class ColourRef(var value: Int)
    // endregion
}
