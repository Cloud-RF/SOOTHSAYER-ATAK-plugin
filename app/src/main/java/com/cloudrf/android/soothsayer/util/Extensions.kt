package com.cloudrf.android.soothsayer.util

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.util.Base64
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.cloudrf.android.soothsayer.PluginDropDownReceiver
import com.cloudrf.android.soothsayer.models.request.TemplateDataModel
import com.google.gson.Gson
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.round
import kotlin.math.sqrt


val FOLDER_PATH = Environment.getExternalStorageDirectory().toString() + "/atak/SOOTHSAYER"
val TEMPLATES_PATH = "$FOLDER_PATH/templates"
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
            val bitmap = createBitmap(drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
                drawable.intrinsicHeight.takeIf { it > 0 } ?: 1,  Bitmap.Config.ARGB_8888)

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

fun ImageView.getBitmapFromImageView(): Bitmap? {
    val drawable = this.drawable ?: return null

    return if (drawable is BitmapDrawable) {
        drawable.bitmap
    } else {
        val bitmap = createBitmap(drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 1)

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    }
}


// Creates ./templates then loads all .json files from assets
//fun Context.createAndStoreFiles(fileList: List<String>?) {
//    val folder = File(TEMPLATES_PATH)
//    if (!folder.exists()) {
//        Log.d(PluginDropDownReceiver.TAG, "createAndStoreFiles creating  new folder....")
//        folder.mkdirs()
//    }
//    Log.d(PluginDropDownReceiver.TAG, "createAndStoreFiles folder path : $FOLDER_PATH")
//    // code to add files to folder.
//    fileList?.forEach { fileName ->
////        val fileName = file
//        val inputStream = assets.open(fileName)
//        val outputStream = FileOutputStream(File(folder, fileName))
//        inputStream.copyTo(outputStream)
//        inputStream.close()
//        outputStream.close()
//    }
//}

fun Context.createAndStoreFiles(fileList: List<String>?) {
    val folder = File(TEMPLATES_PATH)
    if (!folder.exists()) {
        Log.d(PluginDropDownReceiver.TAG, "createAndStoreFiles creating new folder....")
        folder.mkdirs()
    }

    val gson = Gson() // or reuse your existing gson instance

    fileList?.forEach { assetFileName ->
        // open asset
        assets.open(assetFileName).use { inputStream ->
            // parse JSON to get template.name
            val jsonText = inputStream.bufferedReader().use { it.readText() }

            val template = try {
                gson.fromJson(jsonText, TemplateDataModel::class.java)
            } catch (e: Exception) {
                Log.e("createAndStoreFiles", "Invalid JSON in $assetFileName", e)
                return@forEach // skip this file if JSON is invalid
            }

            // pick name from template
            val newFileName = template.template.name + ".json"

            // write file to disk
            val outFile = File(folder, newFileName)
            outFile.writeText(jsonText) // saves with the new name

            Log.d(
                "createAndStoreFiles",
                "Saved asset $assetFileName as ${outFile.absolutePath}"
            )
        }
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
    if(!file.exists()) {
        val writer = FileWriter(file)
        writer.write(json)
        writer.close()
    }
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
                Log.e(PluginDropDownReceiver.TAG, ("${e.stackTrace} \n ${e.message}"))
            }
        }
    }
//    Log.d(PluginDropDownReceiver.TAG, "templateList : ${Gson().toJson(templateList)}")
    return templateList
}

/**
 * Delete files whose parsed TemplateDataModel matches any in matchingTemplates.
 * Returns the boolean value if files deleted file successfully.
 */
fun ArrayList<TemplateDataModel>.deleteFilesMatchingTemplates(): Boolean {
    var deleted = false
    val folder = File(TEMPLATES_PATH)
    if (!folder.exists()) return deleted

    val gson = Gson()
    // Precompute normalized JSON strings for faster fallback lookup
    val normalizedTargets = this.map { gson.toJson(it) }.toSet()

    val files = folder.listFiles { f -> f.extension.equals("json", ignoreCase = true) } ?: arrayOf()
    for (file in files) {
        try {
            val jsonString = file.readText()
            val model = gson.fromJson(jsonString, TemplateDataModel::class.java)

            var shouldDelete = this.any { it == model }

            if (!shouldDelete) {
                val normalized = gson.toJson(model)
                if (normalizedTargets.contains(normalized)) shouldDelete = true
            }

            if (shouldDelete) {
                val ok = file.delete()
                deleted= ok
                if (ok) {
                    Log.d("DELETE", "Deleted ${file.name}")
                } else {
                    Log.e("DELETE", "Failed to delete ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e("DELETE", "Error processing file ${file.name}", e)
            deleted = false
        }
    }
    return deleted
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

fun String.setSpannableText():SpannableStringBuilder{
    val ssb = SpannableStringBuilder(this)
    ssb.setSpan(AbsoluteSizeSpan(18, true), 0, 4, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
    ssb.setSpan(AbsoluteSizeSpan(14, true), 5, this.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
    return ssb
}

fun Context.showAlert(title:String,
                      message:String?,
                      positiveText:String? = null,
                      negativeText:String? = null,
                      icon: Drawable? = null,
                      positiveListener: (() -> Unit)? = null,
                      negativeListener: (() -> Unit)? = null) {
    val builder = AlertDialog.Builder(this)
    builder.setTitle(title)
    message?.let{builder.setMessage(message)}
    icon?.let { builder.setIcon(it) }
    builder.setNegativeButton(negativeText) { _, _ ->
        negativeListener?.invoke()
    }
    builder.setPositiveButton(positiveText) { _, _ ->
        positiveListener?.invoke()
    }
    if (positiveText != null) {
        builder.setPositiveButton(positiveText) { _, _ ->
            positiveListener?.invoke()
        }
    }

    if (negativeText != null) {
        builder.setNegativeButton(negativeText) { _, _ ->
            negativeListener?.invoke()
        }
    }
    builder.show()
    }

fun String.base64StringToBitmap(): Bitmap? {
    return try {
        val base64Data = if (this.contains(",")) {
            this.substringAfter(",") // take part after the comma
        } else {
            this
        }
        val decodedString = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


fun Bitmap.toBase64String(): String {
    val byteArrayOutputStream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
    val byteArray = byteArrayOutputStream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

//fun Bitmap.toDataUri(format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
//                     quality: Int = 100): String {
//    ByteArrayOutputStream().use { stream ->
//        // compress the bitmap to chosen format
//        this.compress(format, quality, stream)
//        val bytes = stream.toByteArray()
//        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP) // no newlines
//        // prepend the MIME type and base64 indicator
//        val mimeType = when (format) {
//            Bitmap.CompressFormat.JPEG -> "image/jpeg"
//            Bitmap.CompressFormat.WEBP -> "image/webp"
//            else -> "image/png"
//        }
//        return "data:$mimeType;base64,$base64"
//    }
//}

fun ImageView.toDataUri(
    width: Int = 48,
    height: Int = 48,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 100
): String? {

    val originalBitmap = (drawable as? BitmapDrawable)?.bitmap ?: return null

// Create a bitmap with alpha
    val scaledBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(scaledBitmap)
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    // Compress to ByteArray
    val outputStream = ByteArrayOutputStream()
    scaledBitmap.compress(format, quality, outputStream)
    val bytes = outputStream.toByteArray()

    // Encode to Base64 without line breaks
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

    // Build data URI with MIME type
    val mimeType = when (format) {
        Bitmap.CompressFormat.JPEG -> "image/jpeg"
        Bitmap.CompressFormat.WEBP -> "image/webp"
        else -> "image/png"
    }

    return "data:$mimeType;base64,$base64"
}



// Auto-scale the resolution to match the desired megapixel.
// The API does this automatically to enforce different plans but those MP limits are higher for laptops etc

fun megapixelCalculator(radius: Double, megapixels:Double): Double {
    // Calculate the resolution based upon the desired radius
    val diameter_m = (radius * 2) * 1e3 //eg. 4000m
    var res = diameter_m / sqrt(megapixels*1e6)  //eg. 4000 / 1000 = 4

    if(res < 1){
        res = 1.0
    }

    if(res > 180){
        res = 180.0
    }

    return res
}
