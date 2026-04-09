package com.downloader.allinone.ui.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.downloader.allinone.ui.theme.DownloaderAllInOneTheme
import com.downloader.allinone.viewmodel.YouTubeDownloaderViewModel

class YouTubeDownloaderActivity : ComponentActivity() {
    private val viewModel: YouTubeDownloaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DownloaderAllInOneTheme {
                YouTubeDownloaderScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}
