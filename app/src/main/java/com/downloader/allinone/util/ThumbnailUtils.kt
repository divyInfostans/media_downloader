package com.downloader.allinone.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailUtils {
    suspend fun getVideoThumbnail(context: Context, videoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            // Extract frame at 1 second (1,000,000 microseconds)
            retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Log.e("ThumbnailUtils", "Failed to generate thumbnail for $videoUri", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.e("ThumbnailUtils", "Failed to release MediaMetadataRetriever", e)
            }
        }
    }
}
