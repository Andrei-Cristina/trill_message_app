package org.message.trill.session.sesame

data class DeviceRecord(
    val deviceId: String,
    var publicKey: ByteArray,
    var activeSession: Session?,
    val inactiveSessions: MutableList<Session> = mutableListOf(),
    var isStale: Boolean = false,
    var staleTransitionTimestamp: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceRecord

        if (deviceId != other.deviceId) return false
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (activeSession != other.activeSession) return false
        if (inactiveSessions != other.inactiveSessions) return false
        if (isStale != other.isStale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()

        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + (activeSession?.hashCode() ?: 0)
        result = 31 * result + inactiveSessions.hashCode()
        result = 31 * result + isStale.hashCode()

        return result
    }
}