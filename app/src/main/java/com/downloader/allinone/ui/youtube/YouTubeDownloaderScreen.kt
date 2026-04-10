package com.downloader.allinone.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.downloader.allinone.ui.dashboard.components.LinkInputSection
import com.downloader.allinone.ui.theme.PrimaryAccent
import com.downloader.allinone.ui.theme.TextSecondary
import com.downloader.allinone.ui.youtube.components.FormatOptionItem
import com.downloader.allinone.ui.youtube.components.VideoPreviewCard
import com.downloader.allinone.viewmodel.YouTubeDownloaderViewModel
import com.downloader.allinone.viewmodel.FormatType

import androidx.compose.ui.platform.LocalClipboardManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeDownloaderScreen(
    viewModel: YouTubeDownloaderViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "YouTube Downloader",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryAccent
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Input Section
                item {
                    LinkInputSection(
                        value = uiState.urlInput,
                        onValueChange = { viewModel.onUrlInputChange(it) },
                        onPasteClick = {
                            clipboardManager.getText()?.text?.let { viewModel.onPasteLink(it) }
                        },
                        onDownloadClick = { viewModel.onDownloadClick() },
                        placeholder = "Paste YouTube link here"
                    )
                }

                // Video Content (Preview and Formats) - Only show if info is available
                if (uiState.hasVideoInfo && !uiState.isLoading) {
                    // Video Preview Section
                    item {
                        VideoPreviewCard(
                            title = uiState.videoTitle,
                            creator = uiState.creator,
                            views = uiState.views,
                            duration = uiState.duration,
                            qualityTag = uiState.qualityTag,
                            thumbnailUrl = uiState.thumbnailUrl
                        )
                    }

                    // Video Formats Section
                    val videoFormats = uiState.formats.filter { it.type == FormatType.VIDEO }
                    if (videoFormats.isNotEmpty()) {
                        item {
                            Text(
                                text = "🎥 VIDEO",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = TextSecondary,
                                    letterSpacing = 2.sp
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(videoFormats) { option ->
                            FormatOptionItem(
                                option = option,
                                isSelected = uiState.selectedFormatId == option.id,
                                onClick = { viewModel.onFormatSelected(option.id) }
                            )
                        }
                    }

                    // Audio Formats Section
                    val audioFormats = uiState.formats.filter { it.type == FormatType.AUDIO }
                    if (audioFormats.isNotEmpty()) {
                        item {
                            Text(
                                text = "🎵 AUDIO",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = TextSecondary,
                                    letterSpacing = 2.sp
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(audioFormats) { option ->
                            FormatOptionItem(
                                option = option,
                                isSelected = uiState.selectedFormatId == option.id,
                                onClick = { viewModel.onFormatSelected(option.id) }
                            )
                        }
                    }
                }

                // Download Progress
                if (uiState.isDownloading) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading... ${uiState.downloadSpeed}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryAccent
                                )
                                Text(
                                    text = "${(uiState.downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = PrimaryAccent
                                )
                            }
                            LinearProgressIndicator(
                                progress = uiState.downloadProgress,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                color = PrimaryAccent,
                                trackColor = Color(0xFF1E2024)
                            )
                        }
                    }
                }

                // Success Message
                uiState.successMessage?.let { success ->
                    item {
                        Surface(
                            color = Color(0xFF2E7D32).copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = success,
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }

                // Error Message
                uiState.errorMessage?.let { error ->
                    item {
                        Surface(
                            color = Color.Red.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error,
                                color = Color.Red,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }

            // Loading Overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryAccent)
                }
            }

            // Bottom Download Button - Only show if info is available
            if (uiState.hasVideoInfo && !uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Button(
                        onClick = { viewModel.onDownloadClick() },
                        enabled = !uiState.isDownloading && uiState.selectedFormatId != null,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null
                        )
                        val buttonText = when {
                            uiState.isDownloading -> "Downloading... ${(uiState.downloadProgress * 100).toInt()}%"
                            uiState.successMessage != null -> "Downloaded"
                            uiState.errorMessage != null -> "Retry Download"
                            else -> "Download Now"
                        }
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                    }
                }
            }
        }
    }
}
