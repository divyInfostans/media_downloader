package com.downloader.allinone.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.downloader.allinone.ui.theme.PrimaryAccent
import com.downloader.allinone.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkInputSection(
    value: String,
    onValueChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "https://..."
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color = Color(0xFF0C0E12), shape = RoundedCornerShape(16.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(18.dp),
            tint = TextSecondary.copy(alpha = 0.5f)
        )

        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    color = TextSecondary.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            },
            modifier = Modifier.weight(1f),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
        )

        TextButton(
            onClick = onPasteClick,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                "PASTE",
                color = PrimaryAccent,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
        }

        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier
                .size(40.dp)
                .background(PrimaryAccent, RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
