package org.message.trill.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.message.trill.messaging.models.ConversationMessage
import org.message.trill.messaging.models.FilePointer

@Composable
fun ChatArea(
    messages: List<ConversationMessage>,
    listState: LazyListState,
    onDownloadClick: (FilePointer) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            state = listState,
            reverseLayout = false
        ) {
            items(messages, key = { it.id }) { message ->
                if (message.filePointer != null) {
                    FileMessageCard(
                        message = message,
                        onDownloadClick = { onDownloadClick(message.filePointer) }
                    )
                } else {
                    TextMessageBubble(message = message)
                }
            }
        }

        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState)
        )
    }
}
