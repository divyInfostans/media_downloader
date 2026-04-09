package com.downloader.allinone.viewmodel

enum class FormatType {
    VIDEO, AUDIO
}

data class FormatOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: FormatType
)

data class YouTubeUiState(
    val urlInput: String = "",
    val detectedLink: String? = null,
    val videoTitle: String = "",
    val creator: String = "",
    val views: String = "",
    val duration: String = "",
    val qualityTag: String = "",
    val thumbnailUrl: String = "",
    val formats: List<FormatOption> = emptyList(),
    val selectedFormatId: String? = null,
    val isLoading: Boolean = false
)
