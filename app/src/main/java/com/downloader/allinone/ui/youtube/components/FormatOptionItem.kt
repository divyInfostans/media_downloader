package com.downloader.allinone.ui.youtube.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.downloader.allinone.ui.theme.PrimaryAccent
import com.downloader.allinone.ui.theme.SurfaceColor
import com.downloader.allinone.ui.theme.TextSecondary
import com.downloader.allinone.viewmodel.FormatOption
import com.downloader.allinone.viewmodel.FormatType

@Composable
fun FormatOptionItem(
    option: FormatOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color(0xFF333539) else Color(0xFF1A1C20)
    val borderColor = if (isSelected) PrimaryAccent else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = if (isSelected) BorderStroke(2.dp, borderColor) else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Surface(
                modifier = Modifier.size(48.dp),
                color = if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else TextSecondary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (option.type == FormatType.VIDEO) Icons.Default.Movie else Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = if (isSelected) PrimaryAccent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = option.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Selection Indicator
            if (isSelected) {
                Surface(
                    modifier = Modifier.size(24.dp),
                    color = PrimaryAccent,
                    shape = RoundedCornerShape(percent = 50)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.size(24.dp),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(percent = 50),
                    border = BorderStroke(2.dp, Color(0xFF584237))
                ) {}
            }
        }
    }
}
