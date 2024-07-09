@file:OptIn(ExperimentalSerializationApi::class)

package com.pubnub.internal

import com.pubnub.api.JsonElement
import com.pubnub.api.asBoolean
import com.pubnub.api.asDouble
import com.pubnub.api.asList
import com.pubnub.api.asLong
import com.pubnub.api.asMap
import com.pubnub.api.asString
import com.pubnub.api.isNull
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

class JsonElementDecoder(
    val jsonElement: JsonElement? = null,
    private var currentMap: Map<String, JsonElement>? = null,
    private var currentList: List<JsonElement>? = null
) : Decoder, CompositeDecoder {
//    init {
//        //println("JsonElementDecoder created with JSON: $jsonElement,\n MAP: $currentMap,\n LIST: $currentList")
//    }
    private var counter = 0

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        return (
            currentMap?.get(descriptor.getElementName(index))?.asBoolean() ?: currentList?.get(index)
                ?.asBoolean()
        ) ?: error("Can't find boolean for $descriptor")
    }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        return (
            currentMap?.get(descriptor.getElementName(index))?.asLong() ?: currentList?.get(index)
                ?.asLong()
        )?.toByte() ?: error("Can't find byte for $descriptor")
    }

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
        TODO("Not yet implemented")
    }

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double {
        return (
            currentMap?.get(descriptor.getElementName(index))?.asDouble() ?: currentList?.get(index)
                ?.asDouble()
        ) ?: error("Can't find double for $descriptor")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        // println("beginStructure $descriptor")
        return when (descriptor.kind) {
            is PolymorphicKind -> {
                currentMap = jsonElement?.asMap()
                this
            }

            StructureKind.MAP -> {
                JsonElementDecoder(currentMap = jsonElement?.asMap())
            }

            StructureKind.CLASS -> {
                currentMap?.let {
                    this
                } ?: JsonElementDecoder(currentMap = jsonElement?.asMap())
            }

            StructureKind.LIST -> {
                JsonElementDecoder(currentList = jsonElement?.asList())
            }

            else -> error("Not implemented")
        }
    }

    override fun decodeBoolean(): Boolean {
        return jsonElement?.asBoolean() ?: error("Can't decode as boolean")
    }

    override fun decodeByte(): Byte {
        return jsonElement?.asLong()?.toByte() ?: error("Can't decode as byte")
    }

    override fun decodeChar(): Char {
        TODO("Not yet implemented")
    }

    override fun decodeDouble(): Double {
        return jsonElement?.asDouble() ?: error("Can't decode as double")
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        TODO("Not yet implemented")
    }

    override fun decodeFloat(): Float {
        return jsonElement?.asDouble()?.toFloat() ?: error("Can't decode as float")
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder {
        TODO("Not yet implemented")
    }

    override fun decodeInt(): Int {
        return jsonElement?.asLong()?.toInt() ?: error("Can't decode as int")
    }

    override fun decodeLong(): Long {
        return jsonElement?.asLong() ?: error("Can't decode as long")
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        return jsonElement?.isNull()?.not() ?: error("jsonElement itself is null")
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeShort(): Short {
        return jsonElement?.asLong()?.toShort() ?: error("Can't decode as short")
    }

    override fun decodeString(): String {
        return jsonElement?.asString() ?: error("Can't decode as string")
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        when (descriptor.kind) {
            is PolymorphicKind -> {
                if (counter < 2) {
                    return counter++
                }
            }

            StructureKind.MAP -> {
                val maxCounter = currentMap?.size?.times(2) ?: error("Illegal state")
                if (counter < maxCounter) {
                    return counter++
                }
            }

            StructureKind.CLASS -> {
                val maxCounter = descriptor.elementsCount
                if (counter < maxCounter) {
                    return counter++
                }
            }

            StructureKind.LIST -> {
                val maxCounter = currentList?.size ?: error("Illegal state")
                if (counter < maxCounter) {
                    return counter++
                }
            }

            else -> error("Not supported")
        }

        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
        return (
            currentMap?.get(descriptor.getElementName(index))?.asDouble() ?: currentList?.get(index)
                ?.asDouble()
        )?.toFloat() ?: error("Can't find float for $descriptor")
    }

    override fun decodeInlineElement(descriptor: SerialDescriptor, index: Int): Decoder {
        TODO("Not yet implemented")
    }

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int {
        return (
            currentMap?.get(descriptor.getElementName(index))?.asLong() ?: currentList?.get(index)
                ?.asLong()
        )?.toInt() ?: error("Can't find int for $descriptor")
    }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
        return (
            currentMap?.get(descriptor.getElementName(index))?.asLong() ?: currentList?.get(index)
                ?.asLong()
        ) ?: error("Can't find long for $descriptor")
    }

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?
    ): T? {
        val key = descriptor.getElementName(index)
        return if (currentMap?.containsKey(key) == false || currentMap?.get(key)?.isNull() == true || currentList?.elementAt(index)?.isNull() == true) {
            null
        } else {
            decodeSerializableElement(descriptor, index, deserializer)
        }
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?
    ): T {
        // println("decodeSerializableElement $descriptor $index $deserializer $currentMap $currentList")
        when (descriptor.kind) {
            is PolymorphicKind -> {
                return deserializer.deserialize(
                    JsonElementDecoder(
                        currentMap = currentMap
                    )
                )
            }

            StructureKind.MAP -> {
                currentMap?.let { map ->
                    val key = map.keys.toList()[index / 2]
                    if (index % 2 == 0) {
                        return key as T
                    } else {
                        return deserializer.deserialize(JsonElementDecoder(map[key]))
                    }
                }
            }

            StructureKind.CLASS -> {
                requireNotNull(currentMap)
                return deserializer.deserialize(JsonElementDecoder((currentMap?.get(descriptor.getElementName(index)))))
            }

            StructureKind.LIST -> {
                return deserializer.deserialize(JsonElementDecoder(currentList?.get(index)))
            }

            else -> error("Not supported")
        }
        error("Not supported")
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
        return (
            currentMap?.get(descriptor.getElementName(index))?.asLong() ?: currentList?.get(index)
                ?.asLong()
        )?.toShort() ?: error("Can't find short for $descriptor")
    }

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
        when (descriptor.kind) {
            is PolymorphicKind -> {
                val type = currentMap?.get(descriptor.getElementName(index))?.asString()
                    ?: error("Can't find class name for polymorphic decode")
                return type
            }

            StructureKind.CLASS -> {
                return currentMap?.get(descriptor.getElementName(index))?.asString()!!
            }

            StructureKind.MAP -> {
                val key = currentMap?.keys?.toList()?.get(index)
                return currentMap?.get(key)?.asString()!!
            }

            StructureKind.LIST -> {
                return currentList?.get(index)?.asString()!!
            }

            else -> error("Can't find string for $descriptor")
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {}
}
