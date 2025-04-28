package org.message.trill.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatArea(messages: List<ConversationMessage>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        items(messages) { message ->
            val alignment = if (message.isSent) Alignment.End else Alignment.Start
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = alignment
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (message.isSent) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = message.content,
                        color = if (message.isSent) Color.White else Color.Black
                    )
                }
                Text(
                    text = message.timestamp,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}