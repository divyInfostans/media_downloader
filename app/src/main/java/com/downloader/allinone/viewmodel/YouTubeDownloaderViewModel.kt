package com.downloader.allinone.viewmodel

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class YouTubeDownloaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    private val TAG = "YouTubeDownloaderVM"

    init {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(application))
        }
        setupPythonStdout()
    }

    private fun setupPythonStdout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val sys = py.getModule("sys")

                val callback = object {
                    @Suppress("unused")
                    fun write(data: String) {
                        data.split("\n").forEach { line ->
                            if (line.contains("PROGRESS:")) {
                                val percentStr = line.substringAfter("PROGRESS:").trim()
                                percentStr.toIntOrNull()?.let { percent ->
                                    _uiState.update {
                                        it.copy(downloadProgress = percent / 100f)
                                    }
                                }
                            }
                        }
                    }

                    @Suppress("unused")
                    fun flush() {}
                }

                sys.put("stdout", callback)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to setup Python stdout", e)
            }
        }
    }

    fun onUrlInputChange(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl) }
    }

    private fun cleanUrl(url: String): String {
        var cleaned = url.trim()
        if (cleaned.contains("?")) {
            if (cleaned.contains("youtu.be/")) {
                cleaned = cleaned.split("?")[0]
            } else if (cleaned.contains("watch?v=")) {
                val parts = cleaned.split("?")
                val baseUrl = parts[0]
                val query = parts[1]
                val vParam = query.split("&").find { it.startsWith("v=") }
                cleaned = if (vParam != null) "$baseUrl?$vParam" else cleaned
            }
        }
        return cleaned
    }

    fun onPasteLink(link: String) {
        val cleaned = cleanUrl(link)
        _uiState.update { it.copy(urlInput = cleaned) }
        fetchVideoInfo(cleaned)
    }

    fun fetchVideoInfo(url: String) {
        val cleanedUrl = cleanUrl(url)
        if (cleanedUrl.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {

            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, formats = emptyList())
            }

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")
                val jsonStr = module.callAttr("get_video_info", cleanedUrl).toString()

                val info = Json.parseToJsonElement(jsonStr).jsonObject

                if (info.containsKey("error")) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = info["error"]?.jsonPrimitive?.content)
                    }
                    return@launch
                }

                val videoFormats = info["video_formats"]?.jsonArray ?: emptyList()
                val audioFormats = info["audio_formats"]?.jsonArray ?: emptyList()

                val parsedVideoFormats = videoFormats.mapNotNull {
                    val obj = it.jsonObject
                    FormatOption(
                        id = obj["format_id"]!!.jsonPrimitive.content,
                        title = obj["resolution"]?.jsonPrimitive?.content ?: "",
                        subtitle = "Format ID: ${obj["format_id"]!!.jsonPrimitive.content}",
                        type = FormatType.VIDEO,
                        ext = obj["ext"]?.jsonPrimitive?.content ?: "mp4",
                        filesize = obj["filesize"]?.jsonPrimitive?.longOrNull ?: 0L,
                        isProgressive = obj["is_progressive"]?.jsonPrimitive?.booleanOrNull ?: false
                    )
                }

                val parsedAudioFormats = audioFormats.mapNotNull {
                    val obj = it.jsonObject
                    FormatOption(
                        id = obj["format_id"]!!.jsonPrimitive.content,
                        title = obj["bitrate"]?.jsonPrimitive?.content ?: "",
                        subtitle = "Format ID: ${obj["format_id"]!!.jsonPrimitive.content}",
                        type = FormatType.AUDIO,
                        ext = obj["ext"]?.jsonPrimitive?.content ?: "m4a",
                        filesize = obj["filesize"]?.jsonPrimitive?.longOrNull ?: 0L
                    )
                }

                val allFormats = parsedVideoFormats + parsedAudioFormats

                _uiState.update {
                    it.copy(
                        hasVideoInfo = true,
                        videoTitle = info["title"]?.jsonPrimitive?.content ?: "",
                        creator = info["uploader"]?.jsonPrimitive?.content ?: "",
                        views = info["view_count"]?.jsonPrimitive?.content ?: "",
                        duration = info["duration"]?.jsonPrimitive?.content ?: "",
                        thumbnailUrl = info["thumbnail"]?.jsonPrimitive?.content ?: "",
                        formats = allFormats,
                        selectedFormatId = allFormats.firstOrNull()?.id,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun onFormatSelected(formatId: String) {
        _uiState.update { it.copy(selectedFormatId = formatId) }
    }

    fun onDownloadClick() {

        val state = _uiState.value
        val url = state.urlInput
        val formatId = state.selectedFormatId ?: return

        if (url.isBlank() || state.isDownloading) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(getApplication(), permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                _uiState.update { it.copy(errorMessage = "Storage permission required") }
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {

            _uiState.update {
                it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null)
            }

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")

                val selectedFormat = state.formats.find { it.id == formatId }
                val isAudio = selectedFormat?.type == FormatType.AUDIO
                val isProgressive = selectedFormat?.isProgressive ?: false

                val callback = object {
                    @Suppress("unused")
                    fun onProgress(progress: Float, speed: String) {
                        _uiState.update {
                            it.copy(downloadProgress = progress, downloadSpeed = speed)
                        }
                    }
                }

                val resultJson = module.callAttr(
                    "download_video",
                    url,
                    formatId,
                    getApplication<Application>().cacheDir.absolutePath,
                    isAudio,
                    isProgressive,
                    callback
                ).toString()

                val result = Json.parseToJsonElement(resultJson).jsonObject

                if (result["status"]?.jsonPrimitive?.content == "success") {

                    val path = result["file_path"]?.jsonPrimitive?.content ?: return@launch
                    val file = File(path)

                    if (file.exists()) {
                        val uri = saveToDownloads(getApplication(), file, isAudio == true)

                        if (uri != null) {
                            file.delete()
                            _uiState.update {
                                it.copy(
                                    isDownloading = false,
                                    downloadProgress = 1f,
                                    successMessage = "Download completed"
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(isDownloading = false, errorMessage = "Save failed")
                            }
                        }
                    }

                } else {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            errorMessage = result["error"]?.jsonPrimitive?.content
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _uiState.update {
                    it.copy(isDownloading = false, errorMessage = e.message)
                }
            }
        }
    }

    private fun saveToDownloads(context: Context, file: File, isAudio: Boolean): Uri? {

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                if (isAudio) "audio/mpeg" else "video/mp4"
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, values, null, null)
                }

            } catch (e: Exception) {
                resolver.delete(it, null, null)
                return null
            }
        }

        return uri
    }
}