package com.downloader.allinone.viewmodel

enum class FormatType {
    VIDEO, AUDIO, VIDEO_ONLY
}

data class FormatOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: FormatType,
    val ext: String = "mp4",
    val filesize: Long = 0,
    val vcodec: String? = null,
    val acodec: String? = null
)

data class YouTubeUiState(
    val urlInput: String = "",
    val detectedLink: String? = null,
    val hasVideoInfo: Boolean = false,
    val videoTitle: String = "",
    val creator: String = "",
    val views: String = "",
    val duration: String = "",
    val qualityTag: String = "",
    val thumbnailUrl: String = "",
    val formats: List<FormatOption> = emptyList(),
    val selectedFormatId: String? = null,
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadSpeed: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null
)
