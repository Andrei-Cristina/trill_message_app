package org.message.trill.networking

sealed class NetworkResponse {
    data object Success : NetworkResponse()
    data object UserNotFound : NetworkResponse()
    data class DeviceMismatch(val oldDevices: List<String>, val newDevices: Map<String, ByteArray>) : NetworkResponse()
}