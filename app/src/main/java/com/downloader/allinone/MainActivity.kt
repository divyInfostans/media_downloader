package com.downloader.allinone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.downloader.allinone.ui.dashboard.DashboardScreen
import com.downloader.allinone.ui.theme.DownloaderAllInOneTheme
import com.downloader.allinone.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {

    private val dashboardViewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DownloaderAllInOneTheme {
                DashboardScreen(viewModel = dashboardViewModel)
            }
        }
    }
}
