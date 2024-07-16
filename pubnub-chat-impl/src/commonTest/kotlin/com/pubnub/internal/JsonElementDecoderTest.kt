package com.pubnub.internal

import com.pubnub.api.createJsonElement
import com.pubnub.internal.serialization.PNDataEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val stringValue = "myString"
private val intValue = 5
private val longValue = 5L
private val boolValue = true
private val doubleValue = 5.0
private val floatValue = 5.0f
private val mapValue = mapOf("key_1" to intValue, "key_2" to null)
private val listValue = listOf(intValue, null)

@Serializable
private class SerializableClass(
    val nullable: String?,
    val nullablePrimitive: Int?,
    val int: Int,
    val long: Long,
    val bool: Boolean,
    val double: Double,
    val float: Float,
    val map: Map<String, Int?>,
    val list: List<Int?>
)

@Serializable
private sealed class SealedClass {
    @Serializable
    data class Sealed1(val aaa: Float, val bbb: Boolean?) : SealedClass()

    @Serializable
    @SerialName("sealed2")
    data class Sealed2(val bbb: Int, val ccc: String?) : SealedClass()
}

class JsonElementDecoderTest {
    @Test
    fun testDecodePrimitives() {
        // given
        val boolJson = createJsonElement(boolValue)
        val intJson = createJsonElement(intValue)
        val longJson = createJsonElement(longValue)
        val stringJson = createJsonElement(stringValue)
        val nullJson = createJsonElement(null)
        val doubleJson = createJsonElement(doubleValue)
        val floatJson = createJsonElement(floatValue)

        // when
        val bool: Boolean = PNDataEncoder.decode(boolJson)
        val int: Int = PNDataEncoder.decode(intJson)
        val long: Long = PNDataEncoder.decode(longJson)
        val string: String = PNDataEncoder.decode(stringJson)
        val nullable: String? = PNDataEncoder.decode(nullJson)
        val nullablePrimitive: Int? = PNDataEncoder.decode(nullJson)
        val double: Double = PNDataEncoder.decode(doubleJson)
        val float: Float = PNDataEncoder.decode(floatJson)

        // then
        assertEquals(boolValue, bool)
        assertEquals(intValue, int)
        assertEquals(longValue, long)
        assertEquals(stringValue, string)
        assertNull(nullable)
        assertNull(nullablePrimitive)
        assertEquals(doubleValue, double)
        assertEquals(floatValue, float)
    }

    @Test
    fun testDecodeLists() {
        // given
        val boolJson = createJsonElement(listOf(createJsonElement(boolValue)))
        // println(boolJson)
        val intJson = createJsonElement(listOf(createJsonElement(intValue)))
        val longJson = createJsonElement(listOf(createJsonElement(longValue)))
        val stringJson = createJsonElement(listOf(createJsonElement(stringValue)))
        val nullJson = createJsonElement(listOf(createJsonElement(null)))
        val doubleJson = createJsonElement(listOf(createJsonElement(doubleValue)))
        val floatJson = createJsonElement(listOf(createJsonElement(floatValue)))

        // when
        val bool: List<Boolean> = PNDataEncoder.decode(boolJson)
        val int: List<Int> = PNDataEncoder.decode(intJson)
        val long: List<Long> = PNDataEncoder.decode(longJson)
        val string: List<String> = PNDataEncoder.decode(stringJson)
        val nullable: List<String?> = PNDataEncoder.decode(nullJson)
        val nullablePrimitive: List<Int?> = PNDataEncoder.decode(nullJson)
        val double: List<Double> = PNDataEncoder.decode(doubleJson)
        val float: List<Float> = PNDataEncoder.decode(floatJson)

        // then
        assertEquals(listOf(boolValue), bool)
        assertEquals(listOf(intValue), int)
        assertEquals(listOf(longValue), long)
        assertEquals(listOf(stringValue), string)
        assertEquals(listOf(null), nullable)
        assertEquals(listOf(null), nullablePrimitive)
        assertEquals(listOf(doubleValue), double)
        assertEquals(listOf(floatValue), float)
    }

    @Test
    fun testDecodeMaps() {
        // given
        val boolJson = createJsonElement(mapOf("abc" to createJsonElement(boolValue)))
        val intJson = createJsonElement(mapOf("abc" to createJsonElement(intValue)))
        val longJson = createJsonElement(mapOf("abc" to createJsonElement(longValue)))
        val stringJson = createJsonElement(mapOf("abc" to createJsonElement(stringValue)))
        val nullJson = createJsonElement(mapOf("abc" to createJsonElement(null)))
        val doubleJson = createJsonElement(mapOf("abc" to createJsonElement(doubleValue)))
        val floatJson = createJsonElement(mapOf("abc" to createJsonElement(floatValue)))

        // when
        val bool: Map<String, Boolean> = PNDataEncoder.decode(boolJson)
        val int: Map<String, Int> = PNDataEncoder.decode(intJson)
        val long: Map<String, Long> = PNDataEncoder.decode(longJson)
        val string: Map<String, String> = PNDataEncoder.decode(stringJson)
        val nullable: Map<String, String?> = PNDataEncoder.decode(nullJson)
        val nullablePrimitive: Map<String, Int?> = PNDataEncoder.decode(nullJson)
        val double: Map<String, Double> = PNDataEncoder.decode(doubleJson)
        val float: Map<String, Float> = PNDataEncoder.decode(floatJson)

        // then
        assertEquals(mapOf("abc" to boolValue), bool)
        assertEquals(mapOf("abc" to intValue), int)
        assertEquals(mapOf("abc" to longValue), long)
        assertEquals(mapOf("abc" to stringValue), string)
        assertEquals(mapOf("abc" to null), nullable)
        assertEquals(mapOf("abc" to null), nullablePrimitive)
        assertEquals(mapOf("abc" to doubleValue), double)
        assertEquals(mapOf("abc" to floatValue), float)
    }

    @Test
    fun testDecodeSerializableClass() {
        // given
        val instance = createJsonElement(
            mapOf(
                "nullable" to createJsonElement(null),
                "nullablePrimitive" to createJsonElement(null),
                "int" to createJsonElement(intValue),
                "long" to createJsonElement(longValue),
                "bool" to createJsonElement(boolValue),
                "double" to createJsonElement(doubleValue),
                "float" to createJsonElement(floatValue),
                "map" to createJsonElement(mapValue.mapValues { createJsonElement(it.value) }),
                "list" to createJsonElement(listValue.map { createJsonElement(it) }),
            )
        )

        // when
        val decodedInstance: SerializableClass = PNDataEncoder.decode(instance)

        // then
        assertNull(decodedInstance.nullablePrimitive)
        assertNull(decodedInstance.nullable)
        assertEquals(intValue, decodedInstance.int)
        assertEquals(longValue, decodedInstance.long)
        assertEquals(boolValue, decodedInstance.bool)
        assertEquals(doubleValue, decodedInstance.double)
        assertEquals(floatValue, decodedInstance.float)
        assertEquals(mapValue, decodedInstance.map)
        assertEquals(listValue, decodedInstance.list)
    }

    @Test
    fun testDecodeSealedClass() {
        // given
        val instanceJson = createJsonElement(
            mapOf(
                "type" to createJsonElement("com.pubnub.internal.SealedClass.Sealed1"),
                "aaa" to createJsonElement(floatValue),
                "bbb" to createJsonElement(null)
            )
        )

        // when
        val decoded: SealedClass = PNDataEncoder.decode(instanceJson)

        // then
        assertEquals(SealedClass.Sealed1(floatValue, null), decoded)
    }

    @Test
    fun testDecodeSealedClassWithSerialName() {
        // given
        val instanceJson = createJsonElement(
            mapOf(
                "type" to createJsonElement("sealed2"),
                "bbb" to createJsonElement(intValue),
                "ccc" to createJsonElement(null)
            )
        )

        // when
        val decoded: SealedClass = PNDataEncoder.decode(instanceJson)

        // then
        assertEquals(SealedClass.Sealed2(intValue, null), decoded)
    }

    @Test
    fun testDecodeCollectionOfSealedClass() {
        // given
        val instanceJson = createJsonElement(
            mapOf(
                "type" to createJsonElement("sealed2"),
                "bbb" to createJsonElement(intValue),
                "ccc" to createJsonElement(null)
            )
        )
        val list = createJsonElement(listOf(instanceJson, instanceJson))

        // when
        val decoded: List<SealedClass> = PNDataEncoder.decode(list)

        // then
        assertEquals(listOf(SealedClass.Sealed2(intValue, null), SealedClass.Sealed2(intValue, null)), decoded)
    }

    @Test
    fun testDecodeMapOfSealedClass() {
        // given
        val instanceJson = createJsonElement(
            mapOf(
                "type" to createJsonElement("sealed2"),
                "bbb" to createJsonElement(intValue),
                "ccc" to createJsonElement(null)
            )
        )
        val map = createJsonElement(mapOf("aaa" to instanceJson))

        // when
        val decoded: Map<String, SealedClass> = PNDataEncoder.decode(map)

        // then
        assertEquals(mapOf("aaa" to SealedClass.Sealed2(intValue, null)), decoded)
    }
}
