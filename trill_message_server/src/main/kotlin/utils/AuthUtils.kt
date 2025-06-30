package utils

import java.util.concurrent.ConcurrentHashMap

class AuthUtils {
    private val otpStore = ConcurrentHashMap<String, String>()

    fun sendOtp(email: String) {
        val otp = (100000..999999).random().toString()
        otpStore[email] = otp

    }

    fun verifyOtp(email: String, otp: String): Boolean {
        return otpStore[email] == otp
    }

    fun deleteOtp(email: String) {
        otpStore.remove(email)
    }

}