package com.pubnub.kmp

import com.pubnub.api.endpoints.MessageCounts
import com.pubnub.api.models.consumer.history.PNMessageCountResult
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.test.await
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class MembershipTest {
    private val pubNub: PubNub = mock(MockMode.strict)
    private val chat: ChatInternal = mock<ChatInternal>(MockMode.strict).also {
        every { it.pubNub } returns pubNub
    }
    private val user = UserImpl(chat, "user")
    private val channel = ChannelImpl(chat, id = "abc")
    private val lastMessageTimetoken = 123L

    @Test
    fun lastReadMessageTimetoken() {
        val membership =
            MembershipImpl(
                chat,
                channel,
                user,
                mapOf("lastReadMessageTimetoken" to lastMessageTimetoken, "other_stuff" to "some string"),
                null,
                null
            )

        assertEquals(lastMessageTimetoken, membership.lastReadMessageTimetoken)
    }

    @Test
    fun getUnreadMessagesCount_when_no_lastReadMessage() = runTest(timeout = 10.seconds) {
        val membership =
            MembershipImpl(
                chat,
                channel,
                user,
                mapOf(),
                null,
                null
            )
        assertNull(membership.getUnreadMessagesCount().await())
    }

    @Test
    fun getUnreadMessagesCount() = runTest(timeout = 10.seconds) {
        val membership =
            MembershipImpl(
                chat,
                channel,
                user,
                mapOf("lastReadMessageTimetoken" to lastMessageTimetoken),
                null,
                null
            )

        val messageCounts: MessageCounts = mock()
        every { messageCounts.async(any()) } calls { (callback: Consumer<Result<PNMessageCountResult>>) ->
            callback.accept(
                Result.success(
                    PNMessageCountResult(
                        mapOf(
                            channel.id to 5,
                        )
                    )
                )
            )
        }
        every { pubNub.messageCounts(listOf(channel.id), listOf(lastMessageTimetoken)) } returns messageCounts

        assertEquals(5L, membership.getUnreadMessagesCount().await())
    }
}
