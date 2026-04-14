package com.downloader.allinone.ui.whatsapp

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.downloader.allinone.model.StatusItem
import com.downloader.allinone.ui.whatsapp.components.*
import com.downloader.allinone.viewmodel.StatusViewModel

@ExperimentalMaterial3Api
@Composable
fun WhatsAppStatusScreen(
    viewModel: StatusViewModel,
    onBackClick: () -> Unit,
    onGrantAccessClick: () -> Unit
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    NavHost(navController = navController, startDestination = "status_list") {
        composable("status_list") {
            StatusListScreen(
                viewModel = viewModel,
                onBackClick = onBackClick,
                onGrantAccessClick = onGrantAccessClick,
                onCardClick = { item ->
                    val encodedUri = Uri.encode(item.uri.toString())
                    if (item.isVideo) {
                        navController.navigate("video_player/$encodedUri")
                    } else {
                        navController.navigate("image_preview/$encodedUri")
                    }
                },
                snackbarHostState = snackbarHostState
            )
        }
        composable(
            route = "image_preview/{imageUri}",
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri") ?: ""
            ImagePreviewScreen(
                imageUri = imageUri,
                isDownloading = uiState.downloadingUris.contains(Uri.parse(imageUri)),
                onBackClick = { navController.popBackStack() },
                onDownloadClick = {
                    val uri = Uri.parse(imageUri)
                    val item = uiState.statusList.find { it.uri == uri }
                    item?.let { viewModel.downloadStatus(it) }
                }
            )
        }
        composable(
            route = "video_player/{videoUri}",
            arguments = listOf(navArgument("videoUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val videoUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            VideoPlayerScreen(
                videoUri = videoUri,
                isDownloading = uiState.downloadingUris.contains(Uri.parse(videoUri)),
                onBackClick = { navController.popBackStack() },
                onDownloadClick = {
                    val uri = Uri.parse(videoUri)
                    val item = uiState.statusList.find { it.uri == uri }
                    item?.let { viewModel.downloadStatus(it) }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusListScreen(
    viewModel: StatusViewModel,
    onBackClick: () -> Unit,
    onGrantAccessClick: () -> Unit,
    onCardClick: (StatusItem) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Status Downloader",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.permissionGranted) {
                        IconButton(onClick = { viewModel.loadStatuses() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !uiState.permissionGranted -> {
                    PermissionState(onGrantAccessClick)
                }
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.statusList.isEmpty() -> {
                    EmptyState()
                }
                else -> {
                    StatusGrid(
                        statusList = uiState.statusList,
                        downloadingUris = uiState.downloadingUris,
                        onDownloadClick = { viewModel.downloadStatus(it) },
                        onCardClick = onCardClick
                    )
                }
            }
        }
    }
}

@Composable
fun StatusGrid(
    statusList: List<StatusItem>,
    downloadingUris: Set<Uri>,
    onDownloadClick: (StatusItem) -> Unit,
    onCardClick: (StatusItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(statusList) { item ->
            StatusCard(
                item = item,
                isDownloading = downloadingUris.contains(item.uri),
                onDownloadClick = { onDownloadClick(item) },
                onCardClick = { onCardClick(item) }
            )
        }
    }
}
