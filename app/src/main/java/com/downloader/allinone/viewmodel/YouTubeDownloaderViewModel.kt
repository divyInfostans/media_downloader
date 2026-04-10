package com.downloader.allinone.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class YouTubeDownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    private val TAG = "YouTubeDownloaderVM"

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(application))
        }
    }

    fun onUrlInputChange(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl) }
    }

    private fun cleanUrl(url: String): String {
        var cleaned = url.trim()
        if (cleaned.contains("?")) {
            if (cleaned.contains("youtu.be/")) {
                cleaned = cleaned.split("?")[0]
            } else if (cleaned.contains("watch?v=")) {
                val parts = cleaned.split("?")
                val baseUrl = parts[0]
                val query = parts[1]
                val vParam = query.split("&").find { it.startsWith("v=") }
                cleaned = if (vParam != null) "$baseUrl?$vParam" else cleaned
            }
        }
        return cleaned
    }

    fun onPasteLink(link: String) {
        val cleaned = cleanUrl(link)
        _uiState.update { it.copy(urlInput = cleaned) }
        fetchVideoInfo(cleaned)
    }

    fun fetchVideoInfo(url: String) {
        val cleanedUrl = cleanUrl(url)
        if (cleanedUrl.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    successMessage = null,
                    hasVideoInfo = false,
                    formats = emptyList()
                )
            }

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")
                val jsonStr = module.callAttr("get_video_info", cleanedUrl).toString()

                val info = Json.parseToJsonElement(jsonStr).jsonObject

                if (info.containsKey("error")) {
                    val error = info["error"]?.jsonPrimitive?.content ?: "Unknown error"
                    _uiState.update { it.copy(isLoading = false, errorMessage = error) }
                    return@launch
                }

                val formatsArray = info["formats"]?.jsonArray ?: emptyList()
                Log.d(TAG, "Total formats fetched: ${formatsArray.size}")

                val parsedFormats = formatsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val formatId = obj["format_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val ext = obj["ext"]?.jsonPrimitive?.content ?: "mp4"
                    val resolution = obj["resolution"]?.jsonPrimitive?.content ?: ""
                    val filesize = obj["filesize"]?.jsonPrimitive?.longOrNull ?: 0L
                    val vcodec = obj["vcodec"]?.jsonPrimitive?.content ?: "none"
                    val acodec = obj["acodec"]?.jsonPrimitive?.content ?: "none"

                    val type = when {
                        vcodec != "none" && acodec != "none" -> FormatType.VIDEO
                        vcodec != "none" -> FormatType.VIDEO_ONLY
                        else -> FormatType.AUDIO
                    }

                    FormatOption(
                        id = formatId,
                        title = resolution,
                        subtitle = "Format ID: $formatId",
                        type = type,
                        ext = ext,
                        filesize = filesize,
                        vcodec = vcodec,
                        acodec = acodec
                    )
                }.sortedWith(compareByDescending<FormatOption> { it.type }.thenByDescending { it.filesize })

                _uiState.update {
                    it.copy(
                        hasVideoInfo = true,
                        videoTitle = info["title"]?.jsonPrimitive?.content ?: "Unknown Title",
                        creator = info["uploader"]?.jsonPrimitive?.content ?: "Unknown Creator",
                        views = info["view_count"]?.jsonPrimitive?.content ?: "0",
                        duration = info["duration"]?.jsonPrimitive?.content ?: "0",
                        thumbnailUrl = info["thumbnail"]?.jsonPrimitive?.content ?: "",
                        formats = parsedFormats,
                        selectedFormatId = parsedFormats.firstOrNull()?.id,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.localizedMessage}") }
            }
        }
    }

    fun onFormatSelected(formatId: String) {
        _uiState.update { it.copy(selectedFormatId = formatId) }
    }

    fun onDownloadClick() {
        val state = _uiState.value
        val url = state.urlInput
        val formatId = state.selectedFormatId

        if (url.isBlank() || formatId == null || state.isDownloading) return

        // Permission check for Android < 13
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(getApplication(), permission) != PackageManager.PERMISSION_GRANTED) {
                _uiState.update { it.copy(errorMessage = "Storage permission required") }
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null, successMessage = null) }

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val targetFolder = File(downloadDir, "YouTubeDownloader")
            if (!targetFolder.exists()) targetFolder.mkdirs()

            Log.d(TAG, "Download started. URL: $url, Format: $formatId, Path: ${targetFolder.absolutePath}")

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")

                val selectedFormat = state.formats.find { it.id == formatId }
                val isAudio = selectedFormat?.type == FormatType.AUDIO

                val callback = object {
                    @Suppress("unused")
                    fun onProgress(progress: Float, speed: String) {
                        _uiState.update { it.copy(downloadProgress = progress, downloadSpeed = speed) }
                    }
                }

                val success = module.callAttr("download_video", url, formatId, targetFolder.absolutePath, isAudio, callback).toBoolean()

                if (success) {
                    Log.d(TAG, "Download completed successfully.")
                    _uiState.update { it.copy(isDownloading = false, downloadProgress = 1.0f, successMessage = "Download completed: ${targetFolder.absolutePath}") }
                } else {
                    Log.e(TAG, "Download failed reported by Python.")
                    _uiState.update { it.copy(isDownloading = false, errorMessage = "Download failed") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed with exception", e)
                _uiState.update { it.copy(isDownloading = false, errorMessage = "Download failed: ${e.localizedMessage}") }
            }
        }
    }
}
