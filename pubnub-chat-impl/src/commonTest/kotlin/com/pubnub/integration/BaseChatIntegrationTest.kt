package com.pubnub.integration

import com.pubnub.chat.Channel
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.test.BaseIntegrationTest
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseChatIntegrationTest : BaseIntegrationTest() {
    lateinit var chat: ChatImpl
    lateinit var chatPam: ChatImpl
    lateinit var channel01: Channel
    lateinit var channel02: Channel
    lateinit var channelPam: Channel
    lateinit var someUser: User
    lateinit var userPam: User
    var cleanup: MutableList<suspend () -> Unit> = mutableListOf() // todo is this used?

    @BeforeTest
    override fun before() {
        super.before()
        chat = ChatImpl(ChatConfiguration(), pubnub)
        chatPam = ChatImpl(ChatConfiguration(), pubnubPam)
        channel01 = ChannelImpl(
            chat = chat,
            id = randomString(),
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        )
        channel02 = ChannelImpl(
            chat = chat,
            id = randomString(),
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        )
        channelPam = ChannelImpl(
            chat = chatPam,
            id = randomString(),
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        )
        // user has chat and chat has user they should be the same?
        someUser = chat.currentUser
        userPam = chatPam.currentUser
    }

    @AfterTest
    fun afterTest() = runTest(timeout = defaultTimeout) {
        pubnub.removeUUIDMetadata(someUser.id).await()
        pubnub.removeUUIDMetadata(userPam.id).await()
        pubnub.removeChannelMetadata(channel01.id).await()
        pubnub.removeChannelMetadata(channel02.id).await()
        pubnub.removeChannelMetadata(channelPam.id).await()
        cleanup.forEach { it.invoke() }
    }

    internal suspend fun delayInMillis(timeMillis: Long) {
        withContext(Dispatchers.Default) {
            delay(timeMillis)
        }
    }
}
