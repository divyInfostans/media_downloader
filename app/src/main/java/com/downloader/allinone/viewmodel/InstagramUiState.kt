package com.downloader.allinone.viewmodel

data class InstagramMediaItem(
    val type: String, // "image" or "video"
    val url: String,
    val thumbnail: String?,
    val ext: String
)

data class InstagramUiState(
    val urlInput: String = "",
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val title: String = "",
    val thumbnailUrl: String = "",
    val mediaItems: List<InstagramMediaItem> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isPreviewReady: Boolean = false
)
