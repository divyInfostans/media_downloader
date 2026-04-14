package com.downloader.allinone.viewmodel

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
class InstagramDownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(InstagramUiState())
    val uiState: StateFlow<InstagramUiState> = _uiState.asStateFlow()

    private val TAG = "InstagramDownloaderVM"

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(application))
        }
    }

    fun onUrlInputChange(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl) }
    }

    fun onPasteLink(link: String) {
        val cleaned = link.trim()
        _uiState.update { it.copy(urlInput = cleaned) }
        if (isValidInstagramUrl(cleaned)) {
            fetchMetadata(cleaned)
        } else {
            _uiState.update { it.copy(errorMessage = "Invalid Instagram URL") }
        }
    }

    private fun isValidInstagramUrl(url: String): Boolean {
        return url.contains("instagram.com")
    }

    fun fetchMetadata(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    successMessage = null,
                    isPreviewReady = false
                )
            }

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")
                val jsonStr = module.callAttr("get_instagram_info", url).toString()

                val info = Json.parseToJsonElement(jsonStr).jsonObject

                if (info.containsKey("error")) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = info["error"]?.jsonPrimitive?.content)
                    }
                    return@launch
                }

                val mediaItems = info["media_items"]?.jsonArray?.mapNotNull {
                    val obj = it.jsonObject
                    InstagramMediaItem(
                        type = obj["type"]?.jsonPrimitive?.content ?: "image",
                        url = obj["url"]?.jsonPrimitive?.content ?: "",
                        thumbnail = obj["thumbnail"]?.jsonPrimitive?.content,
                        ext = obj["ext"]?.jsonPrimitive?.content ?: "mp4"
                    )
                } ?: emptyList()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPreviewReady = true,
                        title = info["title"]?.jsonPrimitive?.content ?: "Instagram Media",
                        thumbnailUrl = info["thumbnail"]?.jsonPrimitive?.content ?: "",
                        mediaItems = mediaItems
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Metadata fetch failed", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun onDownloadClick() {
        val state = _uiState.value
        if (state.mediaItems.isEmpty()) return

        // Permission check for Android <= 9
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(getApplication(), permission) != PackageManager.PERMISSION_GRANTED) {
                _uiState.update { it.copy(errorMessage = "Storage permission required for Android 9 and below") }
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting download for ${state.mediaItems.size} items")
            try {
                val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                state.mediaItems.forEachIndexed { index, item ->
                    val timestamp = System.currentTimeMillis()
                    val fileName = if (item.type == "video") "insta_${timestamp}_$index.mp4" else "insta_${timestamp}_$index.jpg"
                    val mimeType = if (item.type == "video") "video/mp4" else "image/jpeg"

                    val subDir = "DownloaderAllInOne"
                    val fullPath = "$subDir/$fileName"

                    val request = DownloadManager.Request(Uri.parse(item.url))
                        .setTitle("Instagram Download")
                        .setDescription("Downloading ${item.type}...")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fullPath)
                        .setMimeType(mimeType)
                        .addRequestHeader("User-Agent", "Mozilla/5.0")
                        .addRequestHeader("Referer", "https://www.instagram.com/")

                    downloadManager.enqueue(request)
                    Log.d(TAG, "Enqueued download for: $fileName, URL: ${item.url.take(50)}...")
                }

                _uiState.update {
                    it.copy(
                        successMessage = "Download started. Check notifications for progress."
                    )
                }
                Log.d(TAG, "All downloads enqueued successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _uiState.update { it.copy(errorMessage = "Download failed: ${e.message}") }
            }
        }
    }
}
