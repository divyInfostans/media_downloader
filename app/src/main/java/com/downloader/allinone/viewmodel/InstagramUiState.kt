package com.downloader.allinone.viewmodel

enum class MediaType {
    IMAGE, VIDEO
}

data class InstagramMediaItem(
    val type: MediaType,
    val url: String,
    val thumbnail: String? = null,
    val ext: String = "jpg"
)

data class InstagramUiState(
    val urlInput: String = "",
    val isLoading: Boolean = false,
    val title: String = "",
    val thumbnailUrl: String = "",
    val mediaItems: List<InstagramMediaItem> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isPreviewReady: Boolean = false
)
