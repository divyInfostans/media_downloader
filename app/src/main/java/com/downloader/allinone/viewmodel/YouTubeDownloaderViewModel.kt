package com.downloader.allinone.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class YouTubeDownloaderViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        _uiState.update {
            it.copy(
                urlInput = "https://youtu.be/dQw4w9WgXcQ",
                detectedLink = "youtu.be/dQw4w9WgXcQ",
                videoTitle = "Cinematic Journey through Tokyo | 4K HDR",
                creator = "Tokyo Visuals",
                views = "2.4M views",
                duration = "12:45",
                qualityTag = "4K",
                thumbnailUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCdRm4If3pVOQ5z7IY2ujMWuD9pCl1eyTKHblh8Q0ittv8Lp-LRM_IodoxeD2FC8jlBQrVWupYwXGWJHny7sIQTVNi47iin08Gqk0yN8x_WhOw7WDoEBiyOFNJ0lEsWWsmbvWAZZ_rLzEp5Fq3SlnrJIZS4DPSMf8GAgIPyhkun5CPYJdlri12NQfiZRIHymJQRxGz9HKei67mYAdB6b6dbBUv7rXVXV_NwTxsC8ggBYC-eSGZTd5IoKy9fgWMMfb_f5MoLIXBzg-xk",
                formats = listOf(
                    FormatOption("1", "1080p MP4 VIDEO", "approx. 142.5 MB", FormatType.VIDEO),
                    FormatOption("2", "720p MP4 VIDEO", "approx. 84.2 MB", FormatType.VIDEO),
                    FormatOption("3", "Audio MP3 320KBPS", "approx. 18.1 MB", FormatType.AUDIO)
                ),
                selectedFormatId = "1"
            )
        }
    }

    fun onUrlInputChange(newUrl: String) {
        _uiState.update { it.copy(urlInput = newUrl) }
    }

    fun onPasteLink(link: String) {
        _uiState.update { it.copy(urlInput = link) }
    }

    fun onFormatSelected(formatId: String) {
        _uiState.update { it.copy(selectedFormatId = formatId) }
    }

    fun onDownloadClick() {
        // Implement later
    }
}
