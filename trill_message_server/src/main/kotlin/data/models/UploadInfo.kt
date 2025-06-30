package data.models

import kotlinx.serialization.Serializable

@Serializable
data class UploadInfo(val fileId: String, val uploadUrl: String)
