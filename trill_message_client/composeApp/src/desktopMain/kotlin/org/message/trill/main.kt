package org.message.trill

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Trill Message Beta") {
        App { email->
            MessageClient(email)
        }
    }
}