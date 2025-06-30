package org.message.trill.messaging.models

import kotlinx.serialization.Serializable
import org.message.trill.encryption.utils.ByteArraySerializer


@Serializable
data class FilePointer(
    val fileId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    @Serializable(with = ByteArraySerializer::class)
    val key: ByteArray,
    @Serializable(with = ByteArraySerializer::class)
    val iv: ByteArray,
    @Serializable(with = ByteArraySerializer::class)
    val hmac: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FilePointer

        if (fileId != other.fileId) return false
        if (fileName != other.fileName) return false
        if (fileSize != other.fileSize) return false
        if (mimeType != other.mimeType) return false
        if (!key.contentEquals(other.key)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!hmac.contentEquals(other.hmac)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + hmac.contentHashCode()
        return result
    }
}
