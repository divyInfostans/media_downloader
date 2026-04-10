package com.downloader.allinone.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.downloader.allinone.utils.YtDlpExecutor
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class YouTubeDownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    private val executor = YtDlpExecutor(application)

    init {
        viewModelScope.launch {
            val success = executor.initBinaries()
            if (!success) {
                _uiState.update { it.copy(errorMessage = "yt-dlp binary not supported on this device") }
            }
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

        viewModelScope.launch {
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

            val info = executor.getVideoInfo(cleanedUrl)
            if (info != null) {
                runCatching {
                    // Parse formats
                    val formatsArray = info["formats"]?.jsonArray ?: emptyList()
                    val parsedFormats = formatsArray.mapNotNull { element ->
                        val obj = element.jsonObject
                        val formatId = obj["format_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val ext = obj["ext"]?.jsonPrimitive?.content ?: "mp4"
                        val note = obj["format_note"]?.jsonPrimitive?.content ?: ""
                        val vcodec = obj["vcodec"]?.jsonPrimitive?.content ?: "none"
                        val acodec = obj["acodec"]?.jsonPrimitive?.content ?: "none"

                        val type = if (vcodec != "none") FormatType.VIDEO else FormatType.AUDIO

                        // Filter: progressive video (prefer mp4) or any audio
                        val isVideo = type == FormatType.VIDEO && acodec != "none"
                        val isAudio = type == FormatType.AUDIO

                        if (isVideo || isAudio) {
                            FormatOption(
                                id = formatId,
                                title = if (type == FormatType.VIDEO) "$note ($ext)" else "Audio MP3 ($ext)",
                                subtitle = "Format ID: $formatId",
                                type = type,
                                ext = ext
                            )
                        } else null
                    }.distinctBy { it.title }.sortedByDescending { it.type }

                    _uiState.update {
                        it.copy(
                            hasVideoInfo = true,
                            videoTitle = info["title"]?.jsonPrimitive?.content ?: "",
                            creator = info["uploader"]?.jsonPrimitive?.content ?: "",
                            views = info["view_count"]?.jsonPrimitive?.content ?: "",
                            duration = info["duration_string"]?.jsonPrimitive?.content ?: "",
                            thumbnailUrl = info["thumbnail"]?.jsonPrimitive?.content ?: "",
                            formats = parsedFormats,
                            selectedFormatId = parsedFormats.firstOrNull()?.id,
                            isLoading = false
                        )
                    }
                }.onFailure { e ->
                    android.util.Log.e("YouTubeDownloaderVM", "Parsing failed", e)
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Error parsing video info") }
                }
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to fetch video info. Check logs.") }
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

        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null) }

            val downloadDir = File(getApplication<Application>().getExternalFilesDir(null), "Downloads/YouTube")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val cleanTitle = state.videoTitle.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
            val selectedFormat = state.formats.find { it.id == formatId }
            val isAudio = selectedFormat?.type == FormatType.AUDIO
            val ext = if (isAudio) "mp3" else (selectedFormat?.ext ?: "mp4")
            val outputPath = File(downloadDir, "$cleanTitle.$ext").absolutePath

            val success = executor.download(url, formatId, outputPath, isAudio) { progress, speed ->
                updateDownloadProgress(progress, speed)
            }

            if (!success) {
                _uiState.update { it.copy(errorMessage = "Download failed") }
            }

            _uiState.update { it.copy(isDownloading = false) }
        }
    }

    private fun updateDownloadProgress(progress: Float, speed: String) {
        _uiState.update { it.copy(downloadProgress = progress, downloadSpeed = speed) }
    }
}
