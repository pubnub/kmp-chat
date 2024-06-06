@file:OptIn(ExperimentalSerializationApi::class)

package com.pubnub.internal

import com.pubnub.api.JsonElement
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class AnyEncoder : Encoder, CompositeEncoder {
    var returnValue: Any? = null
    private var startedPolymorphicStructure = false
    private var nextSerialNameValue: Pair<String, String>? = null
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        returnValue = value
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
        returnValue = value.toInt()
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        TODO("Not yet implemented")
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        returnValue = value
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        returnValue = value
    }

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
        TODO("Not yet implemented")
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        returnValue = value
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        returnValue = value
    }

    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T?
    ) {
        if (value == null) {
            returnValue = null
        } else {
            serializer.serialize(this, value)
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.MAP -> MapEncoder(this.nextSerialNameValue).apply {
                returnValue = this.map
            }

            StructureKind.LIST -> ListEncoder().apply {
                returnValue = this.list
            }

            is PolymorphicKind -> {
                startedPolymorphicStructure = true
                this
            }

            else -> {
                throw IllegalStateException("Only class instance, map or list expected here")
            }
        }
    }

    override fun encodeBoolean(value: Boolean) {
        returnValue = value
    }

    override fun encodeByte(value: Byte) {
        returnValue = value.toInt()
    }

    override fun encodeChar(value: Char) {
        TODO("Not yet implemented")
    }

    override fun encodeDouble(value: Double) {
        returnValue = value
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        TODO("Not yet implemented")
    }

    override fun encodeFloat(value: Float) {
        returnValue = value
    }

    override fun encodeInline(descriptor: SerialDescriptor): Encoder {
        TODO("Not yet implemented")
    }

    override fun encodeInt(value: Int) {
        returnValue = value
    }

    override fun encodeLong(value: Long) {
        returnValue = value
    }

    @ExperimentalSerializationApi
    override fun encodeNull() {
        returnValue = null
    }

    override fun encodeShort(value: Short) {
        returnValue = value.toInt()
    }

    override fun encodeString(value: String) {
        returnValue = value
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T
    ) {
        serializer.serialize(this, value)
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        returnValue = value.toInt()
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        if (descriptor.kind is PolymorphicKind) {
            require(startedPolymorphicStructure)
            nextSerialNameValue = descriptor.getElementName(index) to value
        } else {
            returnValue = value
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        if (startedPolymorphicStructure) {
            require(descriptor.kind is PolymorphicKind)
            nextSerialNameValue = null
            startedPolymorphicStructure = false
        }
    }
}

object PNDataEncoder {
    fun <T> encode(serializer: SerializationStrategy<T>, value: T): Any? {
        val encoder = AnyEncoder()
        encoder.encodeSerializableValue(serializer, value)
        return encoder.returnValue
    }

    inline fun <reified T> encode(value: T) = encode(serializer(), value)

    fun <T> decode(deserializer: DeserializationStrategy<T>, value: JsonElement): T {
        val encoder = JsonElementDecoder(value)
        return encoder.decodeSerializableValue(deserializer)
    }

    inline fun <reified T> decode(value: JsonElement): T = decode(serializer(), value)
}

class MapEncoder(polymorphicType: Pair<String, String>? = null) : CompositeEncoder {
    var lastKey: String? = null
    val map: MutableMap<String, Any?> = mutableMapOf<String, Any?>().apply {
        if (polymorphicType != null) {
            put(polymorphicType.first, polymorphicType.second)
        }
    }
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        encodeValue(descriptor, index, value)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
        encodeValue(descriptor, index, value.toInt())
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        TODO("Not yet implemented")
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        encodeValue(descriptor, index, value)
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        encodeValue(descriptor, index, value)
    }

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
        TODO("Not yet implemented")
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        encodeValue(descriptor, index, value)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        encodeValue(descriptor, index, value)
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T?
    ) {
        if (value != null) {
            encodeSerializableElement(descriptor, index, serializer, value)
        } else {
            encodeValue(descriptor, index, null)
        }
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T
    ) {
        encodeValue(descriptor, index, PNDataEncoder.encode(serializer, value))
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        encodeValue(descriptor, index, value.toInt())
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        encodeValue(descriptor, index, value)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
    }

    private fun encodeValue(descriptor: SerialDescriptor, index: Int, value: Any?) {
        if (descriptor.kind == StructureKind.CLASS) {
            map[descriptor.getElementName(index)] = value
        } else if (descriptor.kind == StructureKind.MAP) {
            if (index % 2 == 0) {
                lastKey = value.toString()
            } else {
                map[lastKey!!] = value
                lastKey = null
            }
        } else {
            error("Illegal state")
        }
    }
}

class ListEncoder : CompositeEncoder {
    val list: MutableList<Any?> = mutableListOf()
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        list.add(value)
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
        list.add(value.toInt())
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        TODO("Not yet implemented")
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        list.add(value)
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        list.add(value)
    }

    override fun encodeInlineElement(descriptor: SerialDescriptor, index: Int): Encoder {
        TODO("Not yet implemented")
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        list.add(value)
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        list.add(value)
    }

    @ExperimentalSerializationApi
    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T?
    ) {
        if (value != null) {
            list.add(PNDataEncoder.encode(serializer, value))
        } else {
            list.add(null)
        }
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor, index: Int, serializer: SerializationStrategy<T>, value: T
    ) {
        list.add(PNDataEncoder.encode(serializer, value))
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        list.add(value.toInt())
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        list.add(value)
    }

    override fun endStructure(descriptor: SerialDescriptor) {
    }
}