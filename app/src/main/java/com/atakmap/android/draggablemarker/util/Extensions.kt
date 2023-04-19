package com.atakmap.android.draggablemarker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Environment
import androidx.core.content.ContextCompat
import com.atakmap.android.draggablemarker.PluginDropDownReceiver
import com.atakmap.android.draggablemarker.models.TemplateDataModel
import com.atakmap.coremap.log.Log
import com.google.gson.Gson
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.round

private val FOLDER_PATH = Environment.getExternalStorageDirectory().toString() + "/ATAK/SOOTHSAYER/templates"

/**
 * Note - this will become a API offering in 4.5.1 and beyond.
 * @param drawableId
 * @return
 */
fun Context.getBitmap(drawableId: Int): Bitmap? {
    return when (val drawable = ContextCompat.getDrawable(this, drawableId)) {
        is BitmapDrawable -> {
            BitmapFactory.decodeResource(this.resources, drawableId)
        }
        is VectorDrawable -> {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
        else -> {
            null
        }
    }
}

fun Context.createAndStoreFiles(fileList: List<String>?) {
    val folder = File(FOLDER_PATH)
    if (!folder.exists()) {
        Log.d(PluginDropDownReceiver.TAG, "createAndStoreFiles creating  new folder....")
        folder.mkdirs()
    }
    Log.d(PluginDropDownReceiver.TAG, "createAndStoreFiles folder path : $FOLDER_PATH")
    // code to add files to folder.
    fileList?.forEach { fileName ->
//        val fileName = file
        val inputStream = assets.open(fileName)
        val outputStream = FileOutputStream(File(folder, fileName))
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
    }
}

fun getTemplatesFromFolder():ArrayList<TemplateDataModel>{
    val folder = File(FOLDER_PATH)
    val templateList: ArrayList<TemplateDataModel> = ArrayList()
    if (folder.exists()) {
        val files = folder.listFiles()?.filter { it.path.endsWith(".json") }?:ArrayList()
        Log.d(PluginDropDownReceiver.TAG, "files : ${files.size}")
        for(file in files){
            val jsonString = File(FOLDER_PATH, file.name).readText()
            try {
                val jsonData = Gson().fromJson(jsonString, TemplateDataModel::class.java)
                jsonData.transmitter?.let {
                    templateList.add(jsonData)
                    Log.d(PluginDropDownReceiver.TAG, "fileName: ${file.name} \n${JSONObject(jsonString)}")
                }
            }catch (e:Exception){
                Log.e(PluginDropDownReceiver.TAG, e.stackTrace.toString())
            }
        }
    }
    Log.d(PluginDropDownReceiver.TAG, "templateList : ${Gson().toJson(templateList)}")
    return templateList
}

fun Context.getAllTemplates(): ArrayList<TemplateDataModel> {
        val assetManager = assets
        val fileList = assetManager.list("")?.filter { it.endsWith(".json") }
    val templateList: ArrayList<TemplateDataModel> = ArrayList()
    fileList?.forEach { fileName ->
        val jsonString = assets.open(fileName).bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        templateList.add(Gson().fromJson(jsonString, TemplateDataModel::class.java))
        Log.d(PluginDropDownReceiver.TAG, "fileName: $fileName  \n$jsonObject")
    }
    return templateList
}

fun Double.roundValue():Double {
    return round(this * 100000) / 100000
}