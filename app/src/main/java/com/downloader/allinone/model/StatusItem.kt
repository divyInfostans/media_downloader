package com.downloader.allinone.model

import android.net.Uri

data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean
)
