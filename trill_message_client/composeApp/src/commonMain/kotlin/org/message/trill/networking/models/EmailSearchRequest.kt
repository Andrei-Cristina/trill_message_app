package org.message.trill.networking.models

import kotlinx.serialization.Serializable

@Serializable
data class EmailSearchRequest(val email: String)
