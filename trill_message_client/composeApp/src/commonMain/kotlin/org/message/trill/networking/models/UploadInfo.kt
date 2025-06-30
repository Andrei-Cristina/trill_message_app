package org.message.trill.networking.models

import kotlinx.serialization.Serializable

@Serializable
data class UploadInfo(val fileId: String, val uploadUrl: String)
