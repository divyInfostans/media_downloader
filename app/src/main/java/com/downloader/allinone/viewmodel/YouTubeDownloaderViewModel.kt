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
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    successMessage = null,
                    hasVideoInfo = false,
                    formats = emptyList()
                )
            }

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")
                val jsonStr = module.callAttr("get_video_info", cleanedUrl).toString()

                val info = Json.parseToJsonElement(jsonStr).jsonObject

                if (info.containsKey("error")) {
                    val error = info["error"]?.jsonPrimitive?.content ?: "Unknown error"
                    _uiState.update { it.copy(isLoading = false, errorMessage = error) }
                    return@launch
                }

                val videoFormats = info["video_formats"]?.jsonArray ?: emptyList()
                val audioFormats = info["audio_formats"]?.jsonArray ?: emptyList()

                val parsedVideoFormats = videoFormats.mapNotNull { element ->
                    val obj = element.jsonObject
                    val formatId = obj["format_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val ext = obj["ext"]?.jsonPrimitive?.content ?: "mp4"
                    val resolution = obj["resolution"]?.jsonPrimitive?.content ?: ""
                    val filesize = obj["filesize"]?.jsonPrimitive?.longOrNull ?: 0L
                    val isProgressive = obj["is_progressive"]?.jsonPrimitive?.booleanOrNull ?: false

                    FormatOption(
                        id = formatId,
                        title = resolution,
                        subtitle = "Format ID: $formatId",
                        type = FormatType.VIDEO,
                        ext = ext,
                        filesize = filesize,
                        isProgressive = isProgressive
                    )
                }

                val parsedAudioFormats = audioFormats.mapNotNull { element ->
                    val obj = element.jsonObject
                    val formatId = obj["format_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val ext = obj["ext"]?.jsonPrimitive?.content ?: "m4a"
                    val bitrate = obj["bitrate"]?.jsonPrimitive?.content ?: ""
                    val filesize = obj["filesize"]?.jsonPrimitive?.longOrNull ?: 0L

                    FormatOption(
                        id = formatId,
                        title = bitrate,
                        subtitle = "Format ID: $formatId",
                        type = FormatType.AUDIO,
                        ext = ext,
                        filesize = filesize
                    )
                }

                val allFormats = parsedVideoFormats + parsedAudioFormats

                val bestVideo = parsedVideoFormats.firstOrNull()
                val qualityTag = when {
                    bestVideo?.title?.contains("2160p") == true -> "4K"
                    bestVideo?.title?.contains("1440p") == true -> "2K"
                    bestVideo?.title?.contains("1080p") == true -> "FHD"
                    bestVideo?.title?.contains("720p") == true -> "HD"
                    else -> ""
                }

                _uiState.update {
                    it.copy(
                        hasVideoInfo = true,
                        videoTitle = info["title"]?.jsonPrimitive?.content ?: "Unknown Title",
                        creator = info["uploader"]?.jsonPrimitive?.content ?: "Unknown Creator",
                        views = info["view_count"]?.jsonPrimitive?.content ?: "0",
                        duration = info["duration"]?.jsonPrimitive?.content ?: "0",
                        qualityTag = qualityTag,
                        thumbnailUrl = info["thumbnail"]?.jsonPrimitive?.content ?: "",
                        formats = allFormats,
                        selectedFormatId = allFormats.firstOrNull()?.id,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch failed", e)
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.localizedMessage}") }
            }
        }
    }

    fun onFormatSelected(formatId: String) {
        _uiState.update { it.copy(selectedFormatId = formatId) }
    }

    fun onDownloadClick() {
        val state = _uiState.value
        val url = state.urlInput
        val formatId = state.selectedFormatId

        if (url.isBlank() || formatId == null || state.isDownloading) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(getApplication(), permission) != PackageManager.PERMISSION_GRANTED) {
                _uiState.update { it.copy(errorMessage = "Storage permission required") }
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null, successMessage = null) }

            val cacheDir = getApplication<Application>().cacheDir

            try {
                val py = Python.getInstance()
                val module = py.getModule("yt_dlp_helper")

                val selectedFormat = state.formats.find { it.id == formatId }
                val isAudio = selectedFormat?.type == FormatType.AUDIO
                val isProgressive = selectedFormat?.isProgressive ?: false
                val resolution = selectedFormat?.title ?: "N/A"

                Log.d(TAG, "Download Triggered - ID: $formatId, Type: ${if(isAudio) "Audio" else "Video"}, Res: $resolution, Progressive: $isProgressive")

                val callback = object {
                    @Suppress("unused")
                    fun onProgress(progress: Float, speed: String) {
                        _uiState.update { it.copy(downloadProgress = progress.coerceIn(0f, 1f), downloadSpeed = speed) }
                    }
                }

                val ffmpegFile = ensureFfmpeg(getApplication())
                val resultJson = module.callAttr("download_video", url, formatId, cacheDir.absolutePath, isAudio, isProgressive, ffmpegFile.absolutePath, callback).toString()
                val result = Json.parseToJsonElement(resultJson).jsonObject

                if (result["status"]?.jsonPrimitive?.content == "success") {
                    val tempFilePathStr = result["file_path"]?.jsonPrimitive?.content
                    if (tempFilePathStr != null) {
                        val tempFile = File(tempFilePathStr)
                        if (tempFile.exists()) {
                            val uri = saveToDownloads(getApplication(), tempFile, isAudio)
                            if (uri != null) {
                                tempFile.delete()
                                _uiState.update { it.copy(isDownloading = false, downloadProgress = 1.0f, successMessage = "Download completed: ${tempFile.name}") }
                            } else {
                                Log.e(TAG, "MediaStore save failed for: ${tempFile.absolutePath}")
                                _uiState.update { it.copy(isDownloading = false, errorMessage = "Failed to save file to Downloads") }
                            }
                        } else {
                            Log.e(TAG, "Temp file missing at: $tempFilePathStr")
                            _uiState.update { it.copy(isDownloading = false, errorMessage = "Download error: File processing failed") }
                        }
                    }
                } else {
                    val error = result["error"]?.jsonPrimitive?.content ?: "Unknown download error"
                    val tb = result["traceback"]?.jsonPrimitive?.content
                    Log.e(TAG, "Download failed: $error\n$tb")
                    _uiState.update { it.copy(isDownloading = false, errorMessage = error) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                _uiState.update { it.copy(isDownloading = false, errorMessage = "Download failed: ${e.localizedMessage}") }
            }
        }
    }

    private fun ensureFfmpeg(context: Context): File {
        val binDir = File(context.filesDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        val ffmpegFile = File(binDir, "ffmpeg")

        if (!ffmpegFile.exists() || ffmpegFile.length() == 0L) {
            context.assets.open("ffmpeg").use { input ->
                ffmpegFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        ffmpegFile.setExecutable(true, false)
        ffmpegFile.setReadable(true, false)
        ffmpegFile.setWritable(true, true)

        try {
            Runtime.getRuntime()
                .exec(arrayOf("chmod", "755", ffmpegFile.absolutePath))
                .waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Log.d(TAG, "FFMPEG_PATH: ${ffmpegFile.absolutePath}")
        Log.d(TAG, "FFMPEG_EXEC: ${ffmpegFile.canExecute()}")

        return ffmpegFile
    }

    private fun saveToDownloads(context: Context, file: File, isAudio: Boolean): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio) "audio/mpeg" else "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying file", e)
                resolver.delete(it, null, null)
                return null
            }
        }

        return uri
    }
}
