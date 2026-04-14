package com.downloader.allinone.viewmodel

import android.net.Uri
import com.downloader.allinone.model.StatusItem

data class StatusUiState(
    val statusList: List<StatusItem> = emptyList(),
    val isLoading: Boolean = false,
    val permissionGranted: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val downloadingUris: Set<Uri> = emptySet()
)
