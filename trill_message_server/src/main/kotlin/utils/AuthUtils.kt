package utils

import java.util.concurrent.ConcurrentHashMap

class AuthUtils {
    //TODO:Implement OTP proper storage
    private val otpStore = ConcurrentHashMap<String, String>()

    fun sendOtp(email: String) {
        val otp = (100000..999999).random().toString()
        otpStore[email] = otp

        //TODO: Implement OTP sending
    }

    fun verifyOtp(email: String, otp: String): Boolean {
        return otpStore[email] == otp
    }

    fun deleteOtp(email: String) {
        otpStore.remove(email)
    }

}