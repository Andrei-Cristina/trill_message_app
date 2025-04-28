package data.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.*

@Serializable
data class DeviceRegistrationBundle(
    val userEmail: String,
    @Serializable(with = ByteArraySerializer::class) val identityKey: ByteArray,
    @Serializable(with = ByteArraySerializer::class) val signedPreKey: ByteArray,
    @Serializable(with = ByteArraySerializer::class) val preKeySignature: ByteArray,
    @Serializable(with = ByteArrayListSerializer::class) val onetimePreKeys: List<ByteArray>
)

object ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("CustomByteArray", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.getDecoder().decode(decoder.decodeString())
    }
}

object ByteArrayListSerializer : KSerializer<List<ByteArray>> {
    private val listSerializer = ListSerializer(ByteArraySerializer)
    override val descriptor = listSerializer.descriptor
    override fun serialize(encoder: Encoder, value: List<ByteArray>) = listSerializer.serialize(encoder, value)
    override fun deserialize(decoder: Decoder) = listSerializer.deserialize(decoder)
}
