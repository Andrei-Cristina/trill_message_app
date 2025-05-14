package org.message.trill.session.sesame

import org.message.trill.encryption.double_ratchet.RatchetState

data class Session(
    val sessionId: String,
    val ratchetState: RatchetState,
    val isInitiating: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {

}
