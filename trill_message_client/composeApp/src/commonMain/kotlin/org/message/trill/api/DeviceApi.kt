package org.message.trill.api

import org.message.trill.encryption.keys.PrekeyBundle

interface DeviceApi {
    suspend fun fetchPrekeyBundle(userId: String, deviceId: String): PrekeyBundle
    suspend fun fetchUserDevices(userId: String): Map<String, ByteArray>
}