package com.pubnub.integration

import com.pubnub.chat.Channel
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.channel.ThreadChannelImpl
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.internal.PLATFORM
import com.pubnub.test.BaseIntegrationTest
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

internal const val CHANNEL_ID_OF_PARENT_MESSAGE_PREFIX = "channelIdOfParentMessage_"
internal const val THREAD_CHANNEL_ID_PREFIX = "threadChannel_id_"

abstract class BaseChatIntegrationTest : BaseIntegrationTest() {
    lateinit var chat: ChatImpl
    lateinit var chat02: ChatImpl
    lateinit var chatPamServer: ChatImpl
    lateinit var chatPamClient: ChatImpl
    lateinit var channel01: Channel // this simulates first user in channel01
    lateinit var channel01Chat02: Channel // this simulates second user in channel01
    lateinit var channel02: Channel
    lateinit var threadChannel: ThreadChannel
    lateinit var channelPam: Channel
    lateinit var someUser: User
    lateinit var someUser02: User
    lateinit var userPamServer: User
    lateinit var userPamClient: User

    @BeforeTest
    override fun before() {
        super.before()
        chat = ChatImpl(ChatConfiguration(), pubnub)
        chat02 = ChatImpl(ChatConfiguration(), pubnub02)
        chatPamServer = ChatImpl(ChatConfiguration(), pubnubPamServer)
        chatPamClient = ChatImpl(ChatConfiguration(), pubnubPamClient)
        val channel01Id = randomString() + "!_=-@"
        channel01 = ChannelImpl(
            chat = chat,
            id = channel01Id,
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        )
        channel01Chat02 = ChannelImpl(
            chat = chat02,
            id = channel01Id,
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        )
        channel02 = ChannelImpl(
            chat = chat,
            id = randomString() + "!_=-@",
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        )
        threadChannel = ThreadChannelImpl(
            parentMessage = MessageImpl(
                chat = chat,
                timetoken = 123345,
                content = EventContent.TextMessageContent(
                    text = "TextMessageContent",
                    files = listOf()
                ),
                channelId = "$CHANNEL_ID_OF_PARENT_MESSAGE_PREFIX${randomString()}",
                userId = "myUserId",
            ),
            chat = chat,
            id = "$THREAD_CHANNEL_ID_PREFIX${randomString()}",
            name = "threadChannel name ${randomString()}",
            custom = mapOf(randomString() to randomString()),
            description = "threadChannel description ${randomString()}",
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT,
        )
        channelPam = ChannelImpl(
            chat = chatPamServer,
            id = randomString() + "!_=-@",
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        )
        // user has chat and chat has user they should be the same?
        someUser = chat.currentUser
        someUser02 = chat02.currentUser
        userPamServer = chatPamServer.currentUser
        userPamClient = chatPamClient.currentUser
    }

    @AfterTest
    fun afterTest() = runTest {
        try {
            pubnub.removeUUIDMetadata(someUser.id).await()
            if (PLATFORM != "iOS") {
                pubnubPamServer.removeUUIDMetadata(userPamServer.id).await()
                pubnubPamServer.removeUUIDMetadata(userPamClient.id).await()
            }
            pubnub.removeChannelMetadata(channel01.id).await()
            pubnub.removeChannelMetadata(channel01Chat02.id).await()
            pubnub.removeChannelMetadata(channel02.id).await()
            pubnub.removeChannelMetadata(threadChannel.id).await()
            pubnub.removeChannelMetadata(channelPam.id).await()
        } finally {
            chat.destroy()
            chat02.destroy()
            chatPamClient.destroy()
            chatPamServer.destroy()
        }
    }

    internal suspend fun delayInMillis(timeMillis: Long) {
        withContext(Dispatchers.Default) {
            delay(timeMillis)
        }
    }

    internal fun isIos(): Boolean {
        return PLATFORM == "iOS"
    }
}
