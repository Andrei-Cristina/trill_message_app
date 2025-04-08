package org.message.trill.encryption.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

object ByteArraySerializer : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("ByteArray", PrimitiveKind.STRING)

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