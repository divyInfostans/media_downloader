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
            executor.initBinaries()
        }
    }

    fun onUrlInputChange(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl) }
    }

    fun onPasteLink(link: String) {
        val trimmedLink = link.trim()
        _uiState.update { it.copy(urlInput = trimmedLink) }
        fetchVideoInfo(trimmedLink)
    }

    fun fetchVideoInfo(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return

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

            val info = executor.getVideoInfo(trimmedUrl)
            if (info != null) {
                // Parse formats
                val formatsArray = info["formats"]?.jsonArray ?: emptyList()
                val parsedFormats = formatsArray.mapNotNull { element ->
                    val obj = element.jsonObject
                    val formatId = obj["format_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val ext = obj["ext"]?.jsonPrimitive?.content ?: "mp4"
                    val note = obj["format_note"]?.jsonPrimitive?.content ?: ""
                    val vcodec = obj["vcodec"]?.jsonPrimitive?.content
                    val acodec = obj["acodec"]?.jsonPrimitive?.content

                    val type = if (vcodec != "none") FormatType.VIDEO else FormatType.AUDIO

                    // Simple filtering: keep progressive or specific ones
                    if (vcodec != "none" || acodec != "none") {
                        FormatOption(
                            id = formatId,
                            title = if (type == FormatType.VIDEO) "$note ($ext)" else "Audio MP3",
                            subtitle = "Format ID: $formatId",
                            type = type,
                            ext = ext
                        )
                    } else null
                }.distinctBy { it.title }

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
            } else {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to fetch video info") }
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
            val outputPath = File(downloadDir, "$cleanTitle.%(ext)s").absolutePath

            val success = executor.download(url, formatId, outputPath) { progress, speed ->
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
