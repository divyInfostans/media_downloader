package com.downloader.allinone.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downloader.allinone.model.StatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    private val TAG = "StatusViewModel"
    private val PREFS_NAME = "whatsapp_status_prefs"
    private val KEY_STATUS_URI = "status_folder_uri"

    init {
        checkPermission()
    }

    private fun checkPermission() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_STATUS_URI, null)
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            val hasPermission = try {
                getApplication<Application>().contentResolver.persistedUriPermissions.any {
                    it.uri == uri
                }
            } catch (e: Exception) {
                false
            }

            if (hasPermission) {
                _uiState.update { it.copy(permissionGranted = true) }
                loadStatuses(uri)
            } else {
                _uiState.update { it.copy(permissionGranted = false) }
            }
        }
    }

    fun onPermissionGranted(uri: Uri) {
        val context = getApplication<Application>()
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, takeFlags)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATUS_URI, uri.toString()).apply()

        _uiState.update { it.copy(permissionGranted = true) }
        loadStatuses(uri)
    }

    fun loadStatuses() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriString = prefs.getString(KEY_STATUS_URI, null)
        if (uriString != null) {
            loadStatuses(Uri.parse(uriString))
        }
    }

    private fun loadStatuses(treeUri: Uri) {
        Log.d("STATUS_DEBUG", "Selected URI: $treeUri")
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val root = DocumentFile.fromTreeUri(getApplication(), treeUri)
                if (root != null && root.isDirectory) {
                    val statusFolder = findStatusFolder(root)
                    if (statusFolder != null) {
                        Log.d("STATUS_DEBUG", "Status folder found: ${statusFolder.uri}")
                        val statusItems = statusFolder.listFiles()
                            .filter { file ->
                                val name = file.name?.lowercase() ?: ""
                                val isMedia = name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                                              name.endsWith(".png") || name.endsWith(".mp4")
                                if (isMedia) {
                                    Log.d("STATUS_DEBUG", "Found status: $name")
                                }
                                isMedia
                            }
                            .sortedByDescending { it.lastModified() }
                            .map { file ->
                                val name = file.name ?: ""
                                StatusItem(
                                    uri = file.uri,
                                    name = name,
                                    isVideo = name.endsWith(".mp4")
                                )
                            }
                        _uiState.update { it.copy(statusList = statusItems, isLoading = false) }
                    } else {
                        Log.d("STATUS_DEBUG", "Status folder not found")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                permissionGranted = false,
                                errorMessage = "Status folder not found. Please select Android/media folder"
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Could not access folder") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading statuses", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun findStatusFolder(root: DocumentFile): DocumentFile? {
        // WhatsApp path: com.whatsapp -> WhatsApp -> Media -> .Statuses
        val whatsappPaths = arrayOf("com.whatsapp", "WhatsApp", "Media", ".Statuses")
        val whatsappFolder = navigateToFolder(root, whatsappPaths)
        if (whatsappFolder != null) return whatsappFolder

        // WhatsApp Business path: com.whatsapp.w4b -> WhatsApp Business -> Media -> .Statuses
        val businessPaths = arrayOf("com.whatsapp.w4b", "WhatsApp Business", "Media", ".Statuses")
        return navigateToFolder(root, businessPaths)
    }

    private fun navigateToFolder(root: DocumentFile, paths: Array<String>): DocumentFile? {
        var current: DocumentFile? = root
        for (folderName in paths) {
            current = current?.findFile(folderName)
            Log.d("STATUS_DEBUG", "Checking folder: $folderName -> Found: ${current != null}")
            if (current == null) break
        }
        return if (current != null && current.isDirectory) current else null
    }

    fun downloadStatus(item: StatusItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val resolver = context.contentResolver

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (item.isVideo) "video/mp4" else "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    // Fallback for older versions if necessary, but requirements say Android 11+
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI
                }

                val uri = resolver.insert(collection, values)

                uri?.let { destinationUri ->
                    resolver.openInputStream(item.uri)?.use { input ->
                        resolver.openOutputStream(destinationUri)?.use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(destinationUri, values, null, null)
                    }

                    _uiState.update { it.copy(successMessage = "Saved to Downloads") }
                } ?: run {
                    _uiState.update { it.copy(errorMessage = "Failed to create file in Downloads") }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error downloading status", e)
                _uiState.update { it.copy(errorMessage = "Download failed: ${e.message}") }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
