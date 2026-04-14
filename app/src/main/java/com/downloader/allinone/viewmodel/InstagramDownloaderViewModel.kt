package com.downloader.allinone.viewmodel

import android.app.Application
import android.util.Log
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
import java.io.File
import java.net.URL

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
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isPreviewReady = false) }

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

                val mediaItems = info["media_items"]?.jsonArray?.map {
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
        if (state.mediaItems.isEmpty() || state.isDownloading) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null) }

            try {
                val cacheDir = getApplication<Application>().cacheDir
                val totalItems = state.mediaItems.size
                var completedItems = 0

                state.mediaItems.forEachIndexed { index, item ->
                    val fileName = "instagram_${System.currentTimeMillis()}_$index.${item.ext}"
                    val file = File(cacheDir, fileName)

                    // Download with progress for each file
                    downloadFileWithProgress(item.url, file) { itemProgress ->
                        val overallProgress = (completedItems + itemProgress) / totalItems
                        _uiState.update { it.copy(downloadProgress = overallProgress) }
                    }

                    completedItems++
                    _uiState.update { it.copy(downloadProgress = completedItems.toFloat() / totalItems) }
                }

                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        successMessage = "Downloaded $totalItems items to cache"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _uiState.update { it.copy(isDownloading = false, errorMessage = e.message) }
            }
        }
    }

    private fun downloadFileWithProgress(url: String, file: File, onProgress: (Float) -> Unit) {
        val connection = URL(url).openConnection()
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connect()

        val contentLength = connection.contentLengthLong
        connection.getInputStream().use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead: Long = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        onProgress(totalBytesRead.toFloat() / contentLength)
                    }
                }
            }
        }
    }
}
