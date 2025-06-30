package org.message.trill.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import javax.swing.JFileChooser

@Composable
fun MessageInput(
    onSendText: (String) -> Unit,
    onSendFile: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            val fileChooser = JFileChooser()
            val result = fileChooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFile = fileChooser.selectedFile
                onSendFile(selectedFile.absolutePath)
            }
        }) {
            Icon(Icons.Filled.Info, contentDescription = "Attach File")
        }

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Type a message") },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                if (message.isNotBlank()) {
                    onSendText(message)
                    message = ""
                }
            },
            enabled = message.isNotBlank()
        ) {
            Text("Send")
        }
    }
}

