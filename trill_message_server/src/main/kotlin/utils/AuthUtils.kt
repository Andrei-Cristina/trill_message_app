package utils

import java.util.concurrent.ConcurrentHashMap

class AuthUtils {
    companion object {
        fun generateRefreshToken(userEmail: String, deviceID: String): String {
            return userEmail + deviceID + java.util.UUID.randomUUID().toString()
        }
    }

}