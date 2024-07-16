package com.pubnub.internal

import com.pubnub.internal.serialization.PNDataEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

private val stringValue = "myString"
private val intValue = 5
private val longValue = 5L
private val boolValue = true
private val doubleValue = 5.0
private val floatValue = 5.0f
private val mapValue = mapOf("key_1" to intValue, "key_2" to null)
private val listValue = listOf(intValue, null)

@Serializable
private class SerializableClass2(
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
private sealed class SealedClass2 {
    @Serializable
    data class Sealed1(val aaa: Float, val bbb: Boolean?) : SealedClass2()

    @Serializable
    @SerialName("sealed2")
    data class Sealed2(val bbb: Int, val ccc: String?) : SealedClass2()
}

class AnyEncoderTest {
    @Test
    fun testEncodeClass() {
        // given
        val instance = SerializableClass2(
            null, null, intValue, longValue, boolValue, doubleValue, floatValue, mapValue, listValue
        )

        // when
        val encodedMap: Any? = PNDataEncoder.encode(instance)

        // then
        assertEquals(
            mapOf(
                "nullable" to null,
                "nullablePrimitive" to null,
                "int" to intValue,
                "long" to longValue,
                "bool" to boolValue,
                "double" to doubleValue,
                "float" to floatValue,
                "map" to mapValue,
                "list" to listValue
            ),
            encodedMap
        )
    }

    @Test
    fun testEncodeSealedClass() {
        // given
        val instance: SealedClass2 = SealedClass2.Sealed1(
            floatValue,
            null
        )

        // when
        val encodedMap: Any? = PNDataEncoder.encode(instance)

        // then
        assertEquals(
            mapOf(
                "type" to "com.pubnub.internal.SealedClass2.Sealed1",
                "aaa" to floatValue,
                "bbb" to null
            ),
            encodedMap
        )
    }

    @Test
    fun testEncodeSealedClassWithSerialName() {
        // given
        val instance: SealedClass2 = SealedClass2.Sealed2(
            intValue,
            null
        )

        // when
        val encodedMap: Any? = PNDataEncoder.encode(instance)

        // then
        assertEquals(
            mapOf(
                "type" to "sealed2",
                "bbb" to intValue,
                "ccc" to null
            ),
            encodedMap
        )
    }

    @Test
    fun testEncodeListOfClasses() {
        // given
        val instance: SealedClass2 = SealedClass2.Sealed1(
            floatValue,
            null
        )

        val list = listOf(instance, instance)

        // when
        val encodedList: Any? = PNDataEncoder.encode(list)

        // then
        assertEquals(
            listOf(
                mapOf(
                    "type" to "com.pubnub.internal.SealedClass2.Sealed1",
                    "aaa" to floatValue,
                    "bbb" to null
                ),
                mapOf(
                    "type" to "com.pubnub.internal.SealedClass2.Sealed1",
                    "aaa" to floatValue,
                    "bbb" to null
                )
            ),
            encodedList
        )
    }

    @Test
    fun testEncodeMapOfClasses() {
        // given
        val instance: SealedClass2 = SealedClass2.Sealed1(
            floatValue,
            null
        )

        val map = mapOf("a" to instance, "b" to instance)

        // when
        val encodedMap: Any? = PNDataEncoder.encode(map)

        // then
        assertEquals(
            mapOf(
                "a" to mapOf(
                    "type" to "com.pubnub.internal.SealedClass2.Sealed1", "aaa" to floatValue, "bbb" to null
                ),
                "b" to mapOf(
                    "type" to "com.pubnub.internal.SealedClass2.Sealed1", "aaa" to floatValue, "bbb" to null
                )
            ),
            encodedMap
        )
    }
}
