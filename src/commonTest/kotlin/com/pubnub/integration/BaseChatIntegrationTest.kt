package com.pubnub.integration

import com.pubnub.kmp.Channel
import com.pubnub.kmp.ChatConfigImpl
import com.pubnub.kmp.ChatImpl
import com.pubnub.kmp.User
import com.pubnub.kmp.channel.ChannelImpl
import com.pubnub.kmp.types.ChannelType
import com.pubnub.test.BaseIntegrationTest
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseChatIntegrationTest : BaseIntegrationTest() {

    lateinit var chat: ChatImpl
    lateinit var chatPam: ChatImpl
    lateinit var channel01: Channel
    lateinit var channelPam: Channel
    lateinit var someUser: User
    lateinit var userPam: User
    var cleanup: MutableList<suspend () -> Unit> = mutableListOf() //todo is this used?

    @BeforeTest
    override fun before() {
        super.before()
        chat = ChatImpl(ChatConfigImpl(config), pubnub)
        chatPam = ChatImpl(ChatConfigImpl(configPam), pubnubPam)
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
        someUser = User(
            chat,
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            mapOf(randomString() to randomString()),
            randomString(),
            randomString(),
            updated = null,
            lastActiveTimestamp = null
        )
        userPam = User(
            chatPam,
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            randomString(),
            mapOf(randomString() to randomString()),
            randomString(),
            randomString(),
            updated = null,
            lastActiveTimestamp = null
        )
    }

    @AfterTest
    fun afterTest() = runTest {
        pubnub.removeUUIDMetadata(someUser.id).await()
        pubnub.removeUUIDMetadata(userPam.id).await()
        pubnub.removeChannelMetadata(channel01.id).await()
        pubnub.removeChannelMetadata(channelPam.id).await()
        cleanup.forEach { it.invoke() }
    }
}