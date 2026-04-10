package com.downloader.allinone.viewmodel

import android.app.Application
import android.util.Log
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
            // Reset state before fetching
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    hasVideoInfo = false,
                    videoTitle = "",
                    creator = "",
                    views = "",
                    duration = "",
                    thumbnailUrl = "",
                    formats = emptyList(),
                    selectedFormatId = null
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
                val parsedFormats = formatsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val formatId = obj["format_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val ext = obj["ext"]?.jsonPrimitive?.content ?: "mp4"
                    val note = obj["format_note"]?.jsonPrimitive?.content ?: ""
                    val vcodec = obj["vcodec"]?.jsonPrimitive?.content ?: "none"
                    val acodec = obj["acodec"]?.jsonPrimitive?.content ?: "none"

                    val type = if (vcodec != "none") FormatType.VIDEO else FormatType.AUDIO

                    FormatOption(
                        id = formatId,
                        title = note,
                        subtitle = "Format ID: $formatId",
                        type = type,
                        ext = ext
                    )
                }.distinctBy { it.title }.sortedByDescending { it.type }

                val bestVideo = parsedFormats.firstOrNull { it.type == FormatType.VIDEO }
                val qualityTag = when {
                    bestVideo?.title?.contains("2160p") == true -> "4K"
                    bestVideo?.title?.contains("1440p") == true -> "2K"
                    bestVideo?.title?.contains("1080p") == true -> "FHD"
                    bestVideo?.title?.contains("720p") == true -> "HD"
                    else -> ""
                }

                _uiState.update {
                    it.copy(
                        hasVideoInfo = true,
                        videoTitle = info["title"]?.jsonPrimitive?.content ?: "Unknown Title",
                        creator = info["uploader"]?.jsonPrimitive?.content ?: "Unknown Creator",
                        views = info["view_count"]?.jsonPrimitive?.content ?: "0",
                        duration = info["duration_string"]?.jsonPrimitive?.content ?: "0:00",
                        qualityTag = qualityTag,
                        thumbnailUrl = info["thumbnail"]?.jsonPrimitive?.content ?: "",
                        formats = parsedFormats,
                        selectedFormatId = parsedFormats.firstOrNull()?.id,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e("YouTubeDownloaderVM", "Fetch failed", e)
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

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null) }

            val downloadDir = File(getApplication<Application>().getExternalFilesDir(null), "Downloads/YouTube")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val cleanTitle = state.videoTitle.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val selectedFormat = state.formats.find { it.id == formatId }
            val isAudio = selectedFormat?.type == FormatType.AUDIO
            val ext = if (isAudio) "mp3" else (selectedFormat?.ext ?: "mp4")
            val outputPath = File(downloadDir, "$cleanTitle.$ext").absolutePath

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")

                val callback = object {
                    @Suppress("unused")
                    fun onProgress(progress: Float, speed: String) {
                        updateDownloadProgress(progress, speed)
                    }
                }

                module.callAttr("download_video", url, formatId, outputPath, isAudio, callback)
                _uiState.update { it.copy(isDownloading = false, downloadProgress = 100f) }
            } catch (e: Exception) {
                Log.e("YouTubeDownloaderVM", "Download failed", e)
                _uiState.update { it.copy(isDownloading = false, errorMessage = "Download failed: ${e.localizedMessage}") }
            }
        }
    }

    private fun updateDownloadProgress(progress: Float, speed: String) {
        _uiState.update { it.copy(downloadProgress = progress, downloadSpeed = speed) }
    }
}
