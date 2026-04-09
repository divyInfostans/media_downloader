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

        ensureExecutable(ytDlpFile)
        ensureExecutable(ffmpegFile)
    }

    private fun ensureExecutable(file: File) {
        try {
            file.apply {
                setExecutable(true, false)
                setReadable(true, false)
                setWritable(true, false)
            }
            // Also run explicit chmod command for extra reliability
            ProcessBuilder("chmod", "755", file.absolutePath).start().waitFor()

            Log.d(TAG, "Binary status: ${file.name} | Path: ${file.absolutePath} | Exists: ${file.exists()} | canExecute: ${file.canExecute()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set executable permissions for ${file.name}", e)
        }
    }

    private fun copyAssetToInternal(assetName: String, targetFile: File) {
        try {
            // Only copy if it doesn't exist to save time/resource
            if (!targetFile.exists()) {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
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
            url.trim()
        )

        Log.d(TAG, "Executing command: ${command.joinToString(" ")}")

        try {
            if (!ytDlpFile.canExecute()) {
                Log.w(TAG, "yt-dlp not executable, retrying chmod...")
                ensureExecutable(ytDlpFile)
            }

            val process = try {
                ProcessBuilder(command).start()
            } catch (e: java.io.IOException) {
                if (e.message?.contains("Permission denied") == true) {
                    Log.w(TAG, "Permission denied, one last chmod retry...")
                    ensureExecutable(ytDlpFile)
                    ProcessBuilder(command).start()
                } else throw e
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val error = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()

            Log.d(TAG, "yt-dlp raw output: $output")
            if (error.isNotEmpty()) {
                Log.e(TAG, "yt-dlp stderr: $error")
            }

            if (process.exitValue() == 0) {
                return@withContext runCatching {
                    Json.parseToJsonElement(output).jsonObject
                }.onFailure {
                    Log.e(TAG, "Failed to parse JSON output", it)
                }.getOrNull()
            } else {
                Log.e(TAG, "yt-dlp failed with exit code ${process.exitValue()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during yt-dlp execution", e)
        }
        null
    }

    suspend fun download(
        url: String,
        formatId: String,
        outputPath: String,
        isAudio: Boolean = false,
        onProgress: (Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val command = if (isAudio) {
            mutableListOf(
                ytDlpFile.absolutePath,
                "-x",
                "--audio-format", "mp3",
                "--ffmpeg-location", ffmpegFile.absolutePath,
                "-o", outputPath,
                url
            )
        } else {
            mutableListOf(
                ytDlpFile.absolutePath,
                "-f", formatId,
                "--ffmpeg-location", ffmpegFile.absolutePath,
                "-o", outputPath,
                url
            )
        }

        Log.d(TAG, "Executing download command: ${command.joinToString(" ")}")

        try {
            if (!ytDlpFile.canExecute()) {
                Log.w(TAG, "yt-dlp not executable, retrying chmod...")
                ensureExecutable(ytDlpFile)
            }

            val process = try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (e: java.io.IOException) {
                if (e.message?.contains("Permission denied") == true) {
                    Log.w(TAG, "Permission denied, one last chmod retry...")
                    ensureExecutable(ytDlpFile)
                    ProcessBuilder(command).redirectErrorStream(true).start()
                } else throw e
            }

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
