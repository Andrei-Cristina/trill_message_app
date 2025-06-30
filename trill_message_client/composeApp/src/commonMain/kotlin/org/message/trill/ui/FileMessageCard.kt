package org.message.trill.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.message.trill.messaging.models.ConversationMessage
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun FileMessageCard(
    message: ConversationMessage,
    onDownloadClick: () -> Unit
) {
    val alignment = if (message.isSent) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isSent) MaterialTheme.colors.primary.copy(alpha = 0.8f) else MaterialTheme.colors.secondary.copy(alpha = 0.8f)
    val onBackgroundColor = if (message.isSent) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSecondary

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            backgroundColor = backgroundColor,
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(8.dp).widthIn(max = 300.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = "File Icon",
                    modifier = Modifier.size(40.dp),
                    tint = onBackgroundColor
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = message.filePointer?.fileName ?: "Unknown File",
                        style = MaterialTheme.typography.body1,
                        color = onBackgroundColor
                    )
                    Text(
                        text = formatFileSize(message.filePointer?.fileSize ?: 0L),
                        style = MaterialTheme.typography.body2,
                        color = onBackgroundColor.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = onDownloadClick) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download File",
                        tint = onBackgroundColor
                    )
                }
            }
        }
        Text(
            text = message.timestamp,
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }
}

private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(sizeInBytes.toDouble()) / log10(1024.0)).toInt()
    val safeDigitGroups = digitGroups.coerceIn(0, units.size - 1)
    return String.format("%.1f %s", sizeInBytes / 1024.0.pow(safeDigitGroups.toDouble()), units[safeDigitGroups])
}