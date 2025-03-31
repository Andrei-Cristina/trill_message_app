package org.message.trill.session.sesame

data class UserRecord(
    /**
     * for use: email
     */
    val userId: String,
    val devices: MutableMap<String, DeviceRecord> = mutableMapOf(),
    var isStale: Boolean = false,
    var staleTransitionTimestamp: Long? = null
)