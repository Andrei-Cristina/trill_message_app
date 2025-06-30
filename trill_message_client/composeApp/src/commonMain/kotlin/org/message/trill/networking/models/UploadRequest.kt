package org.message.trill.networking.models

import kotlinx.serialization.Serializable

@Serializable
data class UploadRequest(val fileName: String, val fileSize: Long)
