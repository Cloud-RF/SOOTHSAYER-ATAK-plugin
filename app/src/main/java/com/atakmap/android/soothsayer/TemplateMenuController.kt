package com.atakmap.android.soothsayer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.atakmap.android.preference.AtakPreferences
import com.atakmap.android.soothsayer.TemplateRecyclerViewAdapter.Companion.MAX_ITEMS
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.plugin.R
import com.atakmap.android.soothsayer.util.createAndStoreDownloadedFile
import com.atakmap.android.soothsayer.util.getBitmap
import com.atakmap.android.soothsayer.util.saveSettingTemplateListToPref
import com.atakmap.android.soothsayer.util.toDataUri
import com.atakmap.android.soothsayer.util.toast
import java.lang.ref.WeakReference


class TemplateMenuController(
    pluginContext: Context,
    settingView: View,
    templatesMenuView: View,
    newTemplateMenuView: LinearLayout,
    pickIconMenuView: LinearLayout,
    cbSelectAllTemplates: CheckBox,
    templateRecyclerView: RecyclerView?,
    private val templateAdapter: TemplateRecyclerViewAdapter?,
    private val templateItems: MutableList<TemplateDataModel>,
    private val settingTemplateList: MutableList<MutableTuple<TemplateDataModel, Boolean, String?>>,
    private val sharedPrefs: AtakPreferences?,
    private val onDeleteTemplate:()->Unit
) {

    private val contextRef = WeakReference(pluginContext)
    private val settingViewRef = WeakReference(settingView)
    private val templatesMenuViewRef = WeakReference(templatesMenuView)
    private val newTemplateMenuViewRef = WeakReference(newTemplateMenuView)
    private val pickIconMenuViewRef = WeakReference(pickIconMenuView)
    private val cbSelectAllTemplatesRef = WeakReference(cbSelectAllTemplates)
    private val templateRecyclerViewRef = WeakReference(templateRecyclerView)

    private fun context(): Context? = contextRef.get()
    private fun settingsView(): View? = settingViewRef.get()
    private fun templatesMenuView(): View? = templatesMenuViewRef.get()
    private fun newTemplateMenuView(): LinearLayout? = newTemplateMenuViewRef.get()
    private fun pickIconMenuView(): LinearLayout? = pickIconMenuViewRef.get()
    private fun cbSelectAllTemplates(): CheckBox? = cbSelectAllTemplatesRef.get()
    private fun templateRecyclerView(): RecyclerView? = templateRecyclerViewRef.get()

    private val preImportTemplates: ArrayList<TemplateDataModel> = ArrayList()

    fun setPreImportTemplates(templates: ArrayList<TemplateDataModel>) {
        preImportTemplates.clear()
        preImportTemplates.addAll(templates)
    }
    // fields you already have
    private var pickingFileForTemplate = false

    init {
        bindViews()
    }

    private fun bindViews() {
        // Open template menu
        settingsView()?.findViewById<Button>(R.id.btnOpenTemplateManager)?.setOnClickListener {
            settingsView()?.visibility = View.GONE
            templatesMenuView()?.visibility = View.VISIBLE
            setEmptySettingView(templateAdapter?.itemCount == 0)
        }

        // Back from templates menu
        templatesMenuView()?.findViewById<ImageView>(R.id.templateMenuBack)?.setOnClickListener {
            settingsView()?.visibility = View.VISIBLE
            templatesMenuView()?.visibility = View.GONE
        }

        // New template button
        templatesMenuView()?.findViewById<ImageButton>(R.id.btnNewTemplate)?.setOnClickListener {
            templatesMenuView()?.visibility = View.GONE
            newTemplateMenuView()?.visibility = View.VISIBLE
            setEmptySettingView(false)
        }

        // Pick file button
        newTemplateMenuView()?.findViewById<Button>(R.id.btnNewTemplatePickFile)?.setOnClickListener {
            pickingFileForTemplate = true
            val intent = Intent(context(), FilePickerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context()?.startActivity(intent)
        }

        // Pick icon button
        newTemplateMenuView()?.findViewById<Button>(R.id.btnNewTemplatePickIcon)?.setOnClickListener {
            newTemplateMenuView()?.visibility = View.GONE
            pickIconMenuView()?.visibility = View.VISIBLE
        }

        // Cancel new template
        newTemplateMenuView()?.findViewById<Button>(R.id.btnCancelNewTemplate)?.setOnClickListener {
            setNewTemplateViews("", null)
        }

        // Import new template
        newTemplateMenuView()?.findViewById<Button>(R.id.btnConfirmNewTemplate)?.setOnClickListener {
            handleImportTemplate()
        }

        // Select all templates
        cbSelectAllTemplates()?.setOnCheckedChangeListener { _, isChecked ->
            templateAdapter?.selectAll(isChecked)
        }

        // Delete templates
        templatesMenuView()?.findViewById<ImageButton>(R.id.btnDeleteTemplates)?.setOnClickListener {
            onDeleteTemplate()
            templateAdapter?.selectAll(false)
            cbSelectAllTemplates()?.isChecked = false
            setEmptySettingView(templateAdapter?.itemCount == 0)
            sharedPrefs.saveSettingTemplateListToPref(settingTemplateList)
        }

        // Back from new template view
        newTemplateMenuView()?.findViewById<ImageView>(R.id.newTemplateBackBtn)?.setOnClickListener {
            setNewTemplateViews("", null)
        }

        // Setup icon RecyclerView
        val templateIconRecyclerView =
            pickIconMenuView()?.findViewById<RecyclerView>(R.id.rvNewTemplateIcons)
        val templateIconAdapter = TemplateIconsRecyclerViewAdapter(
            mutableListOf(
                context()?.getBitmap(R.drawable.automobile),
                context()?.getBitmap(R.drawable.balloon),
                context()?.getBitmap(R.drawable.bat),
                context()?.getBitmap(R.drawable.bolt),
                context()?.getBitmap(R.drawable.broadcast_tower),
                context()?.getBitmap(R.drawable.drone_alt),
                context()?.getBitmap(R.drawable.game_console_handheld),
                context()?.getBitmap(R.drawable.helicopter),
                context()?.getBitmap(R.drawable.horse),
                context()?.getBitmap(R.drawable.male),
                context()?.getBitmap(R.drawable.mobile_retro),
                context()?.getBitmap(R.drawable.plane),
                context()?.getBitmap(R.drawable.radio),
                context()?.getBitmap(R.drawable.robot_astromech),
                context()?.getBitmap(R.drawable.signal_stream),
                context()?.getBitmap(R.drawable.tower_cell),
                context()?.getBitmap(R.drawable.walkie_talkie),
                context()?.getBitmap(R.drawable.wifi)
            ),
            newTemplateMenuView()?.findViewById(R.id.ivNewTemplateIcon),
            pickIconMenuView(),
            newTemplateMenuView()
        )

        templateIconRecyclerView?.adapter = templateIconAdapter

        templateIconRecyclerView?.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val rvWidth = templateIconRecyclerView.width
                if (rvWidth > 0) {
                    val density = templateIconRecyclerView.resources.displayMetrics.density
                    val itemWidthPx = (50 * density).toInt()

                    val spanCount = 1.coerceAtLeast(rvWidth / itemWidthPx)
                    templateIconRecyclerView.layoutManager =
                        GridLayoutManager(templateIconRecyclerView.context, spanCount)

                    templateIconRecyclerView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })

        templateIconRecyclerView?.setOnClickListener {
            newTemplateMenuView()?.visibility = View.VISIBLE
            pickIconMenuView()?.visibility = View.GONE
        }

        pickIconMenuView()?.findViewById<ImageView>(R.id.pickIconBackBtn)?.setOnClickListener {
            pickIconMenuView()?.visibility = View.GONE
            newTemplateMenuView()?.visibility = View.VISIBLE
        }
    }

    private fun handleImportTemplate() {
        val iconBase64 =
            newTemplateMenuView()?.findViewById<ImageView>(R.id.ivNewTemplateIcon)?.toDataUri()
//              val iconBitmap = newTemplateMenuView()?.findViewById<ImageView>(R.id.ivNewTemplateIcon)?.getBitmapFromImageView()
        if (iconBase64 == null || preImportTemplates.isEmpty()) {
            context()?.toast(context()?.getString(R.string.choose_file))
            return
        }

//        val iconBase64 = iconBitmap.toBase64String()
//        val iconBase64 = iconBitmap.toDataUri()
        val templatesToImport = preImportTemplates.toList()
        val freeSlots = MAX_ITEMS - settingTemplateList.size
        if (freeSlots <= 0) {
            context()?.toast(context()?.getString(R.string.template_limit, MAX_ITEMS.toString()))
            return
        }

        Thread {
            val newItems: MutableList<MutableTuple<TemplateDataModel, Boolean, String?>> = mutableListOf()

            templatesToImport.take(freeSlots).forEach { template ->
                if (template.customIcon == null) template.customIcon = iconBase64
                createAndStoreDownloadedFile(template)
                newItems.add(MutableTuple(template, false, iconBase64))
            }

            Handler(Looper.getMainLooper()).post {
                val existingNames = templateItems.map { it.template.name }.toHashSet()
                val toAdd = newItems.filterNot { it.first.template.name in existingNames }

                if (toAdd.isEmpty()) {
                    context()?.toast("Template Already Exists")
                } else {
                    settingTemplateList.addAll(toAdd.map { it.second = false; it })
                    templateAdapter?.notifyDataSetChanged()
                    templateItems.addAll(toAdd.map { it.first })
                    sharedPrefs.saveSettingTemplateListToPref(settingTemplateList)
                }
                setNewTemplateViews("", null)
            }
        }.start()
    }

    private fun setNewTemplateViews(fileName: String, bitmap: Bitmap?) {
        newTemplateMenuView()?.findViewById<TextView>(R.id.tvNewTemplateFileName)?.text = fileName
        newTemplateMenuView()?.findViewById<ImageView>(R.id.ivNewTemplateIcon)?.setImageBitmap(bitmap)
        preImportTemplates.clear()
        newTemplateMenuView()?.visibility = View.GONE
        templatesMenuView()?.visibility = View.VISIBLE
    }

    private fun setEmptySettingView(isEmpty: Boolean) {
        val emptyView = templatesMenuView()?.findViewById<TextView>(R.id.tvEmptyView)
        templateRecyclerView()?.visibility = if(isEmpty) View.GONE else View.VISIBLE
        emptyView?.visibility = if(isEmpty) View.VISIBLE else View.GONE
    }

    private fun addSettingTemplate(template: TemplateDataModel, icon: String?) {
        if (settingTemplateList.size >= MAX_ITEMS) {
            context()?.toast(context()?.getString(R.string.template_limit, MAX_ITEMS.toString()))
            return
        }
        settingTemplateList.add(MutableTuple(template, false, icon))
        templateAdapter?.notifyItemInserted(settingTemplateList.size - 1)
    }
}
