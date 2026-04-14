package com.downloader.allinone.ui.instagram

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.downloader.allinone.ui.dashboard.components.LinkInputSection
import com.downloader.allinone.ui.theme.PrimaryAccent
import com.downloader.allinone.ui.theme.TextSecondary
import com.downloader.allinone.viewmodel.InstagramDownloaderViewModel
import com.downloader.allinone.viewmodel.InstagramUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramDownloaderScreen(
    viewModel: InstagramDownloaderViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Instagram Downloader",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                LinkInputSection(
                    value = uiState.urlInput,
                    onValueChange = { viewModel.onUrlInputChange(it) },
                    onPasteClick = {
                        clipboardManager.getText()?.text?.let { viewModel.onPasteLink(it) }
                    },
                    onDownloadClick = { viewModel.fetchMetadata(uiState.urlInput) }
                )
            }

            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PrimaryAccent)
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                item {
                    Text(error, color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (uiState.isPreviewReady) {
                item {
                    InstagramPreviewCard(uiState = uiState, onDownloadClick = { viewModel.onDownloadClick() })
                }
            }

            if (uiState.isDownloading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = uiState.downloadProgress,
                            modifier = Modifier.fillMaxWidth(),
                            color = PrimaryAccent
                        )
                        Text(
                            "Downloading... ${(uiState.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            uiState.successMessage?.let { success ->
                item {
                    Text(success, color = Color.Green, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun InstagramPreviewCard(
    uiState: InstagramUiState,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1D24))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = uiState.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 2
            )

            if (uiState.mediaItems.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(uiState.mediaItems) { item ->
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = item.thumbnail ?: item.url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (item.type == "video") {
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "VIDEO",
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (uiState.mediaItems.isNotEmpty()) {
                val item = uiState.mediaItems.first()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = item.thumbnail ?: item.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Button(
                onClick = onDownloadClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download All")
            }
        }
    }
}
