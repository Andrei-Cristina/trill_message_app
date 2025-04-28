package org.message.trill.encryption.utils.models

import kotlinx.serialization.Serializable

@Serializable
data class User(val email: String, val nickname: String, val isOnline: Boolean, val lastOnline: String?)
