package data.models

import data.models.MessageWrapper
import kotlinx.serialization.Serializable

@Serializable
data class MessageRequest(
    val messages: List<MessageWrapper>
)


