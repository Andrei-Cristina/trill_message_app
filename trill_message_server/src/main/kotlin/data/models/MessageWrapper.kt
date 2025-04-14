package data.models

import kotlinx.serialization.Serializable

@Serializable
data class MessageWrapper(
    val message: Message
)
