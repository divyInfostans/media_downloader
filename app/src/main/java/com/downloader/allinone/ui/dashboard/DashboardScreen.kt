package com.downloader.allinone.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.downloader.allinone.ui.dashboard.components.*
import com.downloader.allinone.ui.theme.PrimaryAccent
import com.downloader.allinone.ui.theme.TextSecondary
import com.downloader.allinone.viewmodel.DashboardViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.downloader.allinone.ui.theme.DownloaderAllInOneTheme

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    DashboardScreenContent(uiState, viewModel, modifier)
}

@Composable
fun DashboardScreenContent(
    uiState: com.downloader.allinone.viewmodel.DashboardUiState,
    viewModel: DashboardViewModel?,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DashboardHeader(onSettingsClick = { /* Handle settings */ })
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Hero Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ready to collect?",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                    )
                    Text(
                        text = "Paste a link below to start your high-speed download.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // Input Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinkInputSection(
                        value = uiState.urlInput,
                        onValueChange = { viewModel?.onUrlInputChange(it) },
                        onPasteClick = { viewModel?.onPasteLink("https://example.com/video") }, // Mock paste
                        onDownloadClick = { viewModel?.onDownloadStart() }
                    )

                    // Detected Link Chip
                    uiState.detectedLink?.let { link ->
                        Surface(
                            onClick = { viewModel?.onPasteLink(link) },
                            color = Color(0xFF00A2F4).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(percent = 50),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = Color(0xFF00A2F4).copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF00A2F4)
                                )
                                Text(
                                    text = "Detected link: $link",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color(0xFF00A2F4),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Service Shortcuts Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ServiceShortcut(
                            name = "YouTube",
                            icon = Icons.Default.PlayCircle,
                            iconColor = Color(0xFFFF0000),
                            onClick = { viewModel?.onServiceClick("YouTube") },
                            modifier = Modifier.weight(1f)
                        )
                        ServiceShortcut(
                            name = "Instagram",
                            icon = Icons.Default.PhotoCamera,
                            iconColor = Color(0xFFE4405F),
                            onClick = { viewModel?.onServiceClick("Instagram") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ServiceShortcut(
                            name = "WhatsApp",
                            icon = Icons.Default.Chat,
                            iconColor = Color(0xFF25D366),
                            onClick = { viewModel?.onServiceClick("WhatsApp") },
                            modifier = Modifier.weight(1f)
                        )
                        ServiceShortcut(
                            name = "Universal",
                            icon = Icons.Default.Language,
                            iconColor = Color(0xFF2196F3),
                            onClick = { viewModel?.onServiceClick("Universal") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Active Downloads Section
            if (uiState.activeDownloads.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Downloads",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Surface(
                                color = PrimaryAccent.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(percent = 50)
                            ) {
                                Text(
                                    text = "${uiState.activeDownloads.size} TASKS",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = PrimaryAccent,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(end = 24.dp)
                        ) {
                            items(uiState.activeDownloads) { task ->
                                DownloadItem(
                                    task = task,
                                    onPauseResume = { viewModel?.onPauseResume(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1115)
@Composable
fun DashboardScreenPreview() {
    DownloaderAllInOneTheme {
        DashboardScreenContent(
            uiState = com.downloader.allinone.viewmodel.DashboardUiState(
                activeDownloads = listOf(
                    com.downloader.allinone.model.DownloadTask(
                        id = "1",
                        fileName = "cinematic_sequence_4k.mp4",
                        progress = 0.38f,
                        sizeInfo = "45.2 MB / 120 MB"
                    )
                ),
                detectedLink = "youtu.be/dQw4w9WgXcQ"
            ),
            viewModel = null
        )
    }
}
