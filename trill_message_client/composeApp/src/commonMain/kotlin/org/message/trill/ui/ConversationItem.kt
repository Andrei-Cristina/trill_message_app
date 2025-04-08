package org.message.trill.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConversationItem(contact: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        colors = if (isSelected) ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primaryVariant)
        else ButtonDefaults.buttonColors()
    ) {
        Text(contact)
    }
}

