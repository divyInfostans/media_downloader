package com.downloader.allinone.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream

class YtDlpExecutor(private val context: Context) {
    private val TAG = "YtDlpExecutor"
    private val binDir = File(context.filesDir, "bin")
    private val ytDlpFile = File(binDir, "yt-dlp")
    private val ffmpegFile = File(binDir, "ffmpeg")

    suspend fun initBinaries() = withContext(Dispatchers.IO) {
        if (!binDir.exists()) binDir.mkdirs()

        copyAssetToInternal("yt-dlp", ytDlpFile)
        copyAssetToInternal("ffmpeg", ffmpegFile)

        ytDlpFile.setExecutable(true)
        ffmpegFile.setExecutable(true)
    }

    private fun copyAssetToInternal(assetName: String, targetFile: File) {
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy asset $assetName", e)
        }
    }

    suspend fun getVideoInfo(url: String): JsonObject? = withContext(Dispatchers.IO) {
        val command = listOf(
            ytDlpFile.absolutePath,
            "-J",
            "--no-playlist",
            url
        )

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()

            if (process.exitValue() == 0) {
                return@withContext Json.parseToJsonElement(output).jsonObject
            } else {
                Log.e(TAG, "yt-dlp failed with exit code ${process.exitValue()}: $output")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run yt-dlp", e)
        }
        null
    }

    suspend fun download(
        url: String,
        formatId: String,
        outputPath: String,
        onProgress: (Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val command = mutableListOf(
            ytDlpFile.absolutePath,
            "-f", formatId,
            "--ffmpeg-location", ffmpegFile.absolutePath,
            "-o", outputPath,
            url
        )

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, "yt-dlp: $line")
                    parseProgress(line!!)?.let { (progress, speed) ->
                        onProgress(progress, speed)
                    }
                }
            }

            process.waitFor()
            return@withContext process.exitValue() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            false
        }
    }

    private fun parseProgress(line: String): Pair<Float, String>? {
        // [download]  10.0% of 10.00MiB at  1.00MiB/s ETA 00:09
        if (line.contains("[download]") && line.contains("%")) {
            try {
                val percentRegex = "(\\d+\\.\\d+)%".toRegex()
                val speedRegex = "at\\s+(.*?)\\s+ETA".toRegex()

                val percentMatch = percentRegex.find(line)
                val speedMatch = speedRegex.find(line)

                val progress = percentMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                val speed = speedMatch?.groupValues?.get(1) ?: ""

                return Pair(progress / 100f, speed)
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }
        return null
    }
}
