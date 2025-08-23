package com.atakmap.android.soothsayer.util

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.VectorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.atakmap.android.soothsayer.PluginDropDownReceiver
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.plugin.R
import com.google.gson.Gson
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.round

val FOLDER_PATH = Environment.getExternalStorageDirectory().toString() + "/atak/SOOTHSAYER"
private val TEMPLATES_PATH = "$FOLDER_PATH/templates"
const val SOOTHSAYER = "SOOTHSAYER"
const val PNG_IMAGE = ".png"

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

// Creates ./templates then loads all .json files from assets
fun Context.createAndStoreFiles(fileList: List<String>?) {
    val folder = File(TEMPLATES_PATH)
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

fun createAndStoreDownloadedFile(data: TemplateDataModel){
    val folder = File(TEMPLATES_PATH)
    if (!folder.exists()) {
        Log.d(PluginDropDownReceiver.TAG, "createAndStoreFiles creating  new folder....")
        folder.mkdirs()
    }
    val json = Gson().toJson(data)
    val file = File(folder, "${data.template.name}.json")
    val writer = FileWriter(file)
    writer.write(json)
    writer.close()
}

fun getTemplatesFromFolder(): ArrayList<TemplateDataModel> {
    val folder = File(TEMPLATES_PATH)
    val templateList: ArrayList<TemplateDataModel> = ArrayList()
    if (folder.exists()) {
        val files = folder.listFiles()?.filter { it.path.endsWith(".json") } ?: ArrayList()
//        Log.d(PluginDropDownReceiver.TAG, "files : ${files.size}")
        for (file in files) {
            val jsonString = File(TEMPLATES_PATH, file.name).readText()
            try {
                val jsonData = Gson().fromJson(jsonString, TemplateDataModel::class.java)
                jsonData.transmitter?.let {
                    templateList.add(jsonData)
                    Log.d(
                        PluginDropDownReceiver.TAG,
                        "fileName: ${file.name} \n${JSONObject(jsonString)}"
                    )
                }
            } catch (e: Exception) {
                Log.d(
                        PluginDropDownReceiver.TAG,
                        "Bad template: ${file.name}")
                Log.e(PluginDropDownReceiver.TAG, e.stackTrace.toString())
            }
        }
    }
//    Log.d(PluginDropDownReceiver.TAG, "templateList : ${Gson().toJson(templateList)}")
    return templateList
}

fun Double.roundValue(): Double {
    return round(this * 100000) / 100000
}

fun Context.toast(message: String?) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

fun Context.shortToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Context.isConnected(): Boolean {
    var result = false
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val networkCapabilities = connectivityManager.activeNetwork
        networkCapabilities?.let { capabilities ->
            val actNw = connectivityManager.getNetworkCapabilities(capabilities)
            actNw?.let {
                result = when {
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                    else -> {
                        false
                    }
                }
            }
        }
    } else {
        result = try {
            connectivityManager.activeNetworkInfo != null && connectivityManager.activeNetworkInfo!!.isConnected
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    return result
}

fun String.getFileName():String{
   return "${SimpleDateFormat("HHmm", Locale.getDefault()).format(Date())}_$SOOTHSAYER$this"
}

fun Context.getLineColor(signalValue:Double): Int?{
    //Log.d(PluginDropDownReceiver.TAG, "getLineColor : $signalValue")
    val colorId = when{
        signalValue >= 21.0 -> R.color.blue
        signalValue >= 15.0 -> R.color.green
        signalValue >= 9.0 -> R.color.yellow
        signalValue >= 3.0 -> R.color.red
        signalValue >= -3.0 -> R.color.darker_gray
        else -> null // no link!
    }
    return if(colorId == null){
        null
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        this.resources.getColor(colorId, this.theme)
    } else {
        this.resources.getColor(colorId)
    }
}

fun String.setSpannableText():SpannableStringBuilder{
    val ssb = SpannableStringBuilder(this)
    ssb.setSpan(AbsoluteSizeSpan(18, true), 0, 4, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
    ssb.setSpan(AbsoluteSizeSpan(14, true), 5, this.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
    return ssb
}

fun Context.showAlert(title:String, message:String, positiveText:String, negativeText:String, listener: () -> Unit) {
    val builder = AlertDialog.Builder(this)
    builder.setTitle(title)
    builder.setMessage(message)
    builder.setNegativeButton(negativeText, null)
    builder.setPositiveButton(positiveText) { _, _ ->
        listener()
    }
    builder.show()
    }

fun String.base64StringToBitmap():Bitmap?{
    val decodedString = android.util.Base64.decode(this.split(",")[1], android.util.Base64.DEFAULT)
    return try {
        BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
    }catch (e:Exception){
        null
    }
 }