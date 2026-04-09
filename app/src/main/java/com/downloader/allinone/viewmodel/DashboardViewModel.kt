package com.downloader.allinone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.downloader.allinone.model.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        val mockDownloads = listOf(
            DownloadTask(
                id = "1",
                fileName = "cinematic_sequence_4k.mp4",
                progress = 0.38f,
                sizeInfo = "45.2 MB / 120 MB"
            ),
            DownloadTask(
                id = "2",
                fileName = "midnight_lofi_mix.mp3",
                progress = 0.67f,
                sizeInfo = "8.1 MB / 12 MB"
            )
        )
        _uiState.update { it.copy(activeDownloads = mockDownloads, detectedLink = "youtu.be/dQw4w9WgXcQ") }
    }

    fun onUrlInputChange(newValue: String) {
        _uiState.update { it.copy(urlInput = newValue) }
    }

    fun onPasteLink(link: String) {
        _uiState.update { it.copy(urlInput = link) }
    }

    fun onDownloadStart() {
        // Implementation for later
    }

    fun onPauseResume(taskId: String) {
        _uiState.update { state ->
            val updatedList = state.activeDownloads.map {
                if (it.id == taskId) it.copy(isPaused = !it.isPaused) else it
            }
            state.copy(activeDownloads = updatedList)
        }
    }

    fun onServiceClick(service: String) {
        // Handle service shortcut click
    }
}
