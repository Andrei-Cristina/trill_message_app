package org.message.trill

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import org.message.trill.ui.AppNavigation

@Composable
fun App() {
    MaterialTheme {
        val client = MessageClient()
        AppNavigation(client)
    }
}