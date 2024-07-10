package com.pubnub.kmp

import com.pubnub.kmp.channel.ChannelImpl
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals

class MembershipTest {
    private val chat: Chat = mock(MockMode.strict)

    @Test
    fun lastReadMessageTimetoken() {
        val lastMessageTimetoken = 123L
        val user = User(chat, "user")
        val channel = ChannelImpl(chat, id = "abc")

        val membership =
            Membership(
                chat,
                channel,
                user,
                mapOf("lastReadMessageTimetoken" to lastMessageTimetoken, "other_stuff" to "some string"),
                null,
                null
            )

        assertEquals(lastMessageTimetoken, membership.lastReadMessageTimetoken)
    }
}
