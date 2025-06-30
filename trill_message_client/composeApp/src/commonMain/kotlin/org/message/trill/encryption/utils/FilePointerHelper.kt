package org.message.trill.encryption.utils

import kotlinx.serialization.json.Json
import org.message.trill.messaging.models.FilePointer

object FilePointerHelper {
    private const val FILE_POINTER_PREFIX = "TRILL_FILE_POINTER_V1::"
    private val json = Json { ignoreUnknownKeys = true }

    fun isFilePointer(content: String): Boolean {
        return content.startsWith(FILE_POINTER_PREFIX)
    }

    fun createFilePointerContent(pointer: FilePointer): String {
        val jsonString = json.encodeToString(pointer)
        return "$FILE_POINTER_PREFIX$jsonString"
    }

    fun parseFilePointer(content: String): FilePointer? {
        if (!isFilePointer(content)) return null
        val jsonString = content.removePrefix(FILE_POINTER_PREFIX)
        return try {
            json.decodeFromString<FilePointer>(jsonString)
        } catch (e: Exception) {
            println("Failed to parse file pointer JSON: ${e.message}")
            null
        }
    }

    fun getFileName(content: String): String {
        return parseFilePointer(content)?.fileName ?: "Attached File"
    }
}