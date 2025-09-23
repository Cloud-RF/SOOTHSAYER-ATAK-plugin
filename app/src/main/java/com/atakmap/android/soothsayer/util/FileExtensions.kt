package com.atakmap.android.soothsayer.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.google.gson.Gson
import com.google.gson.stream.JsonToken
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

fun Context.getFileNameFromUri(uri: Uri): String {
    var res: String? = null
    if ("content" == uri.scheme) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                res = cursor.getString(nameIdx)
            }
        } finally {
            cursor?.close()
        }
    }
    return res ?: ""
}

fun String.isZip(name: String): Boolean =
    this == "application/zip" ||
            this == "application/x-zip-compressed" ||
            name.endsWith(".zip", ignoreCase = true)

fun String.isJson(name: String): Boolean =
    this == "application/json" || name.endsWith(".json", true)

fun InputStream.getJsonTemplatesFromZip(): Pair<ArrayList<TemplateDataModel>, Boolean> {
    val templates = ArrayList<TemplateDataModel>()
    var someInvalid = false
    val gson = Gson()

    ZipInputStream(BufferedInputStream(this)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                // read entry without closing zis
                val jsonString = zis.reader().readText()
                try {
                    val model = gson.fromJson(jsonString, TemplateDataModel::class.java)
                    if (model.isTemplateValid()) {
                        templates.add(model)
                    } else {
                        someInvalid = true
                    }
                } catch (e: Exception) {
                    Log.e("ZIP", "Invalid JSON in ${entry.name}", e)
                    someInvalid = true
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    return templates to someInvalid
}

fun TemplateDataModel.isTemplateValid(): Boolean {
    val transmitter = this.transmitter ?: return false
    val antenna = this.antenna

    return     transmitter.lat in -89.0..89.0
            && transmitter.lon in -180.0..180.0
            && transmitter.alt in .1..120_000.0
            && transmitter.frq in 2.0..100_000.0
            && transmitter.txw in .001..2_000_000.0
            && transmitter.bwi in .001..200.0

            && antenna.txg in -10.0..60.0
            && antenna.txl in .0..60.0
            && antenna.azi.split(",").all { it.toInt() in 0..360 }
            && antenna.tlt in -90..90
            && antenna.hbw in 0..360
            && antenna.vbw in 0..360
            && antenna.fbr in .0..60.0
            && antenna.pol in arrayOf("v", "h")

}

/**
 * Parse a JSON InputStream as either an array or a single TemplateDataModel,
 * streaming each item so you don't load the whole file into memory.
 */
fun InputStream.parseTemplatesFromStream(): Pair<ArrayList<TemplateDataModel>, Boolean> {
    val templates = ArrayList<TemplateDataModel>()
    var someInvalid = false
    val gson = Gson()

    InputStreamReader(this).use { reader ->
        val jsonReader = com.google.gson.stream.JsonReader(reader)
        when (jsonReader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                jsonReader.beginArray()
                while (jsonReader.hasNext()) {
                    try {
                        val item = gson.fromJson<TemplateDataModel>(jsonReader, TemplateDataModel::class.java)
                        if (item.isTemplateValid()) templates.add(item) else someInvalid = true
                    } catch (_: Exception) {
                        // skip invalid entry but consume token
                        jsonReader.skipValue()
                        someInvalid = true
                    }
                }
                jsonReader.endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                try {
                    val item = gson.fromJson<TemplateDataModel>(
                        jsonReader,
                        TemplateDataModel::class.java
                    )
                    if (item.isTemplateValid()) templates.add(item) else someInvalid = true
                }catch (e: Exception){
                    e.printStackTrace()
//                    toast("Bad template: ${e.message}")
                }

            }
            else -> jsonReader.skipValue()
        }
    }

    return Pair(templates, someInvalid)
}

// currently not removing methods created by max
fun byteArrayToTemplateArray(bytes: ByteArray): ArrayList<TemplateDataModel> {
    if (bytes.size < 4)
        return ArrayList()

    val isZip = bytes[0] == 0x50.toByte()
            && bytes[1] == 0x4B.toByte()
            && bytes[2] == 0x03.toByte()
            && bytes[3] == 0x04.toByte()

    return if (isZip) parseZipBytes(bytes) else parseJsonBytes(bytes)?.let {arrayListOf(it)} ?: arrayListOf()
}

// currently not removing methods created by max
fun parseZipBytes(bytes: ByteArray): ArrayList<TemplateDataModel> {
    val gson = Gson()
    val results: ArrayList<TemplateDataModel> = ArrayList()

    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".json")) {
                try {
                    val reader = InputStreamReader(zip)
                    val obj = gson.fromJson(reader, TemplateDataModel::class.java)
                    results.add(obj)
                } catch (_: Exception) {}
            }
            entry = zip.nextEntry
        }
    }

    return results
}

// currently not removing methods created by max
fun parseJsonBytes(bytes: ByteArray): TemplateDataModel? {
    val gson = Gson()
    try {
        return ByteArrayInputStream(bytes).use { inputStream ->
            InputStreamReader(inputStream).use { reader ->
                gson.fromJson(reader, TemplateDataModel::class.java)
            }
        }
    } catch (_: Exception) {
        return null
    }
}

fun Context.getAllFilesFromAssets(): List<String>? {
    val assetManager = this.assets
    return assetManager.list("")?.filter { it.endsWith(Constant.TEMPLATE_FORMAT) }
}