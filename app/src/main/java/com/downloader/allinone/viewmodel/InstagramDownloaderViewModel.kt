package com.downloader.allinone.viewmodel

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
class InstagramDownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(InstagramUiState())
    val uiState: StateFlow<InstagramUiState> = _uiState.asStateFlow()

    private val TAG = "InstagramDownloaderVM"

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(application))
        }
    }

    fun onUrlInputChange(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl) }
    }

    fun onPasteLink(link: String) {
        val cleaned = link.trim()
        _uiState.update { it.copy(urlInput = cleaned) }
        if (isValidInstagramUrl(cleaned)) {
            fetchMetadata(cleaned)
        } else {
            _uiState.update { it.copy(errorMessage = "Invalid Instagram URL") }
        }
    }

    private fun isValidInstagramUrl(url: String): Boolean {
        return url.contains("instagram.com")
    }

    fun fetchMetadata(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    successMessage = null,
                    isPreviewReady = false
                )
            }

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")
                val jsonStr = module.callAttr("get_instagram_info", url).toString()

                // 1. LOG FULL yt-dlp RESPONSE (MANDATORY)
                Log.d("INSTA_RAW", jsonStr)

                val json = JSONObject(jsonStr)

                if (json.has("error")) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = json.optString("error"))
                    }
                    return@launch
                }

                // 2. REWRITE EXTRACTION LOGIC FROM SCRATCH
                val mediaItems = extractMedia(json)

                if (mediaItems.isEmpty()) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Unable to fetch media")
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isPreviewReady = true,
                        title = json.optString("title", "Instagram Media"),
                        thumbnailUrl = json.optString("thumbnail", ""),
                        mediaItems = mediaItems
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Metadata fetch failed", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    private fun extractMedia(json: JSONObject): List<InstagramMediaItem> {
        val result = mutableListOf<InstagramMediaItem>()

        // CASE 1: Carousel
        if (json.has("entries")) {
            val entries = json.getJSONArray("entries")

            for (i in 0 until entries.length()) {
                val entry = entries.getJSONObject(i)

                val url = extractUrlFromObject(entry)
                if (url != null) {
                    result.add(buildMedia(entry, url))
                }
            }
            return result
        }

        // CASE 2: Single post
        val url = extractUrlFromObject(json)
        if (url != null) {
            result.add(buildMedia(json, url))
        }

        return result
    }

    private fun extractUrlFromObject(obj: JSONObject): String? {
        // PRIORITY 1: direct url
        if (obj.has("url")) {
            val url = obj.optString("url")
            if (url.isNotEmpty()) return url
        }

        // PRIORITY 2: formats (CRITICAL FOR IMAGES)
        if (obj.has("formats")) {
            val formats = obj.getJSONArray("formats")

            var bestUrl: String? = null
            var bestWidth = 0

            for (i in 0 until formats.length()) {
                val format = formats.getJSONObject(i)

                val url = format.optString("url")
                val width = format.optInt("width", 0)
                val ext = format.optString("ext")

                if (url.isNullOrEmpty()) continue

                // ACCEPT BOTH IMAGE + VIDEO
                if (ext in listOf("jpg", "jpeg", "png", "webp", "mp4")) {
                    if (width >= bestWidth) {
                        bestWidth = width
                        bestUrl = url
                    }
                }
            }

            if (bestUrl != null) return bestUrl
        }

        return null
    }

    private fun buildMedia(obj: JSONObject, url: String): InstagramMediaItem {
        // Fix: Remove query parameters before checking extension
        val urlWithoutParams = url.substringBefore("?")
        val ext = urlWithoutParams.substringAfterLast(".", "")

        val type = when (ext.lowercase()) {
            "mp4" -> MediaType.VIDEO
            "jpg", "jpeg", "png", "webp" -> MediaType.IMAGE
            else -> {
                // Second check based on yt-dlp metadata
                if (obj.optBoolean("is_video") || obj.optString("vcodec") != "none") {
                    MediaType.VIDEO
                } else {
                    MediaType.IMAGE
                }
            }
        }

        return InstagramMediaItem(
            url = url,
            type = type,
            thumbnail = obj.optString("thumbnail") ?: obj.optString("display_url"),
            ext = if (type == MediaType.VIDEO) "mp4" else "jpg"
        )
    }

    fun onDownloadClick() {
        val state = _uiState.value
        if (state.mediaItems.isEmpty()) return

        // Permission check for Android <= 9
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(getApplication(), permission) != PackageManager.PERMISSION_GRANTED) {
                _uiState.update { it.copy(errorMessage = "Storage permission required for Android 9 and below") }
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting download for ${state.mediaItems.size} items")
            try {
                val downloadManager = getApplication<Application>().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                state.mediaItems.forEachIndexed { index, item ->
                    val timestamp = System.currentTimeMillis()
                    // Fix: Compare enum to enum, not string
                    val fileName = if (item.type == MediaType.VIDEO) "insta_${timestamp}_$index.mp4" else "insta_${timestamp}_$index.jpg"
                    val mimeType = if (item.type == MediaType.VIDEO) "video/mp4" else "image/jpeg"

                    val subDir = "DownloaderAllInOne"
                    val fullPath = "$subDir/$fileName"

                    val request = DownloadManager.Request(Uri.parse(item.url))
                        .setTitle("Instagram Download")
                        .setDescription("Downloading ${item.type}...")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fullPath)
                        .setMimeType(mimeType)
                        .addRequestHeader("User-Agent", "Mozilla/5.0")
                        .addRequestHeader("Referer", "https://www.instagram.com/")

                    downloadManager.enqueue(request)
                    Log.d(TAG, "Enqueued download for: $fileName, URL: ${item.url.take(50)}...")
                }

                _uiState.update {
                    it.copy(
                        successMessage = "Download started. Check notifications for progress."
                    )
                }
                Log.d(TAG, "All downloads enqueued successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _uiState.update { it.copy(errorMessage = "Download failed: ${e.message}") }
            }
        }
    }
}
