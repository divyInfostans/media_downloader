package com.downloader.allinone.viewmodel

import com.downloader.allinone.model.DownloadTask

data class DashboardUiState(
    val urlInput: String = "",
    val activeDownloads: List<DownloadTask> = emptyList(),
    val detectedLink: String? = null
)
