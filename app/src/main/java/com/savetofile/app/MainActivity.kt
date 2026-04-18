package com.savetofile.app

import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SaveToFile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        intent?.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent?.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrEmpty()) {
                        saveTextDirectly(text)
                    } else {
                        getSharedUri(intent)?.let { saveFileDirectly(it) }
                            ?: finish()
                    }
                } else {
                    getSharedUri(intent)?.let { saveFileDirectly(it) }
                        ?: finish()
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                uris?.firstOrNull()?.let { saveFileDirectly(it) }
            }
            else -> finish()
        }
    }

    private fun getSharedUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }

    private fun saveTextDirectly(text: String) {
        try {
            val fileName = "SaveTo_${System.currentTimeMillis()}.txt"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { os ->
                        os.write(text.toByteArray())
                    }
                    
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(it, values, null, null)
                    
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, fileName).outputStream().use { os ->
                    os.write(text.toByteArray())
                }
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun saveFileDirectly(sourceUri: Uri) {
        try {
            val originalName = getFileName(sourceUri)
            val fileName = originalName ?: "SaveTo_${System.currentTimeMillis()}"
            val mimeType = contentResolver.getType(sourceUri) ?: "application/octet-stream"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                
                val destUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                destUri?.let { dest ->
                    contentResolver.openInputStream(sourceUri)?.use { input ->
                        contentResolver.openOutputStream(dest)?.use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(dest, values, null, null)
                    
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, fileName)
                
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}