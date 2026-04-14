package com.downloader.allinone.ui.whatsapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import com.downloader.allinone.ui.theme.DownloaderAllInOneTheme
import com.downloader.allinone.viewmodel.StatusViewModel

class WhatsAppStatusActivity : ComponentActivity() {
    private val viewModel: StatusViewModel by viewModels()

    private val openDocumentTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.onPermissionGranted(it)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DownloaderAllInOneTheme {
                WhatsAppStatusScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onGrantAccessClick = { launchSAF() }
                )
            }
        }
    }

    private fun launchSAF() {
        // Try to pre-set the path to WhatsApp statuses for convenience
        val whatsappPath = "Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
        val authority = "com.android.externalstorage.documents"
        val documentId = "primary:$whatsappPath"
        val uri = DocumentsContract.buildTreeDocumentUri(authority, documentId)

        openDocumentTree.launch(uri)
    }
}
