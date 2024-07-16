package com.pubnub.kmp.utils

import com.pubnub.chat.internal.utils.cyrb53a
import kotlin.test.Test
import kotlin.test.assertEquals

class HashTest {
    @Test
    fun cyrb53a() {
        val inputs = listOf("mo3k2 ok", "Ąž< 4ó", "jio jeroiew mn\nmrie rmw\n!$:@\"")
        val expectedHashes = listOf(5914255141804885u, 5904620407185594u, 4876630631559622u)

        assertEquals(expectedHashes, inputs.map(::cyrb53a))
    }
}
