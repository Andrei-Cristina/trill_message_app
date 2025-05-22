package org.message.trill

import androidx.compose.runtime.*
import org.message.trill.ui.AppNavigation
import org.message.trill.ui.TrillAppTheme

@Composable
fun App() {
    TrillAppTheme {
        val client = remember { MessageClient() }
        AppNavigation(client)
    }
}
