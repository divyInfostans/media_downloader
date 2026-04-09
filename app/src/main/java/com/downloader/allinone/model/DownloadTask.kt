package com.downloader.allinone.model

data class DownloadTask(
    val id: String,
    val fileName: String,
    val progress: Float, // 0.0 to 1.0
    val sizeInfo: String,
    val isPaused: Boolean = false,
    val thumbnailUrl: String? = null
)
