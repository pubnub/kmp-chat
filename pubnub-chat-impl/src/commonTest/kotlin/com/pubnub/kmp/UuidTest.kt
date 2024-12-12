package com.pubnub.kmp

import com.pubnub.chat.internal.generateRandomUuid
import kotlin.test.Test
import kotlin.test.assertTrue

class UuidTest {
    @Test
    fun generateUuid() {
        val uuid = generateRandomUuid()
        assertTrue { uuid.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) }
    }
}
