package org.message.trill.encryption.utils.models

data class DebugInfo(
    val messageKey: String,
    val headerDh: String?,
    val headerPn: Int?,
    val headerN: Int?,
    val fullAd: String,
    val ciphertext: String,
    val rootKey: String,
    val receiveChainKey: String?,
    val sendChainKey: String?
)