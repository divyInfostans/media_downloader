package com.downloader.allinone.ui.instagram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.downloader.allinone.ui.theme.DownloaderAllInOneTheme
import com.downloader.allinone.viewmodel.InstagramDownloaderViewModel

class InstagramDownloaderActivity : ComponentActivity() {
    private val viewModel: InstagramDownloaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DownloaderAllInOneTheme {
                InstagramDownloaderScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}
