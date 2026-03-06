package moros.asf.tools.forspreadsheets.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.awt.Rectangle
import java.util.UUID

object UUIDSerializer: KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

object RectangleSerializer: KSerializer<Rectangle> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("java.awt.Rectangle") {
        element<Int>("x")
        element<Int>("y")
        element<Int>("width")
        element<Int>("height")
    }

    override fun serialize( encoder: Encoder, value: Rectangle){
        encoder.encodeStructure(descriptor){
            encodeIntElement(descriptor, 0, value.x)
            encodeIntElement(descriptor, 1, value.y)
            encodeIntElement(descriptor, 2, value.width)
            encodeIntElement(descriptor, 3, value.height)
        }
    }

    override fun deserialize( decoder: Decoder ): Rectangle {
        return decoder.decodeStructure(descriptor){
            var x = 0
            var y = 0
            var width = 0
            var height = 0
            while( true ){
                when( val index = decodeElementIndex(descriptor) ){
                    0 -> x = decodeIntElement(descriptor, 0)
                    1 -> y = decodeIntElement(descriptor, 1)
                    2 -> width = decodeIntElement(descriptor, 2)
                    3 -> height = decodeIntElement(descriptor, 3)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Rectangle(x, y, width, height)
        }
    }

}
