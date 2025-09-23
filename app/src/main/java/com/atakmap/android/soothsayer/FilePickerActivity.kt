package com.atakmap.android.soothsayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.atakmap.android.soothsayer.models.request.TemplateDataModel
import com.atakmap.android.soothsayer.util.Constant
import com.atakmap.android.soothsayer.util.getFileNameFromUri
import com.atakmap.android.soothsayer.util.getJsonTemplatesFromZip
import com.atakmap.android.soothsayer.util.isJson
import com.atakmap.android.soothsayer.util.isZip
import com.atakmap.android.soothsayer.util.parseTemplatesFromStream
import com.atakmap.android.soothsayer.util.toast

class FilePickerActivity : Activity() {

    companion object {
        const val PICK_FILE_REQUEST = 1001
        const val FILE_NAME = "file_name"
        const val JSON_LIST = "json_list"
        const val SOME_INVALID = "some_invalid"
        const val INTENT_ACTION = "com.soothsayer.FILE_SELECTED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/plain", "application/zip")
            )
        }
        startActivityForResult(pickIntent, PICK_FILE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != PICK_FILE_REQUEST || resultCode != RESULT_OK) {
            finish()
            return
        }

        val fileUri = data?.data ?: return
        val fileName = getFileNameFromUri(fileUri)
        val type = contentResolver.getType(fileUri)

        if(type==null){
            finish()
            return
        }

        // parse file off main thread
        Thread {
            try {
                contentResolver.openInputStream(fileUri)?.use { input ->
                    val parsedResult = when {
                        type.isZip(fileName) -> input.getJsonTemplatesFromZip()
                        type.isJson(fileName) -> input.parseTemplatesFromStream()
                        else -> Pair(ArrayList<TemplateDataModel>(), false)
                    }

                    // broadcast parsed JSON list
                    val intent = Intent(INTENT_ACTION).apply {
                        putExtra(FILE_NAME, fileName)
                        putExtra(JSON_LIST, ArrayList(parsedResult.first))
                        putExtra(SOME_INVALID, parsedResult.second)
                        `package` = Constant.PACKAGE_NAME
                    }
                    sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Log.e("FilePickerActivity", "Failed to parse file", e)
                runOnUiThread { toast("Error parsing file: ${e.message}") }
            } finally {
                finish()
            }
        }.start()
    }
}
