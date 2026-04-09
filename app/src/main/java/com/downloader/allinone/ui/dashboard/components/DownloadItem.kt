package com.downloader.allinone.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.downloader.allinone.model.DownloadTask
import com.downloader.allinone.ui.theme.PrimaryAccent
import com.downloader.allinone.ui.theme.TextSecondary

@Composable
fun DownloadItem(
    task: DownloadTask,
    onPauseResume: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(260.dp) // Reduced width
            .padding(end = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp) // Updated to 16dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp) // Reduced padding
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mock thumbnail placeholder
                Box(
                    modifier = Modifier
                        .size(40.dp) // Reduced size
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = task.sizeInfo,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = "${(task.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = PrimaryAccent,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 9.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp) // Reduced height
                    .clip(RoundedCornerShape(percent = 50)),
                color = PrimaryAccent,
                trackColor = Color(0xFF0C0E12)
            )
        }
    }
}
