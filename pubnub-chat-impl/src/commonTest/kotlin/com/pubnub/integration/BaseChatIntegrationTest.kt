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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest

internal const val CHANNEL_ID_OF_PARENT_MESSAGE_PREFIX = "channelIdOfParentMessage_"
internal const val THREAD_CHANNEL_ID_PREFIX = "threadChannel_id_"

abstract class BaseChatIntegrationTest : BaseIntegrationTest() {
    private val channel01Id = randomString() + "!_=-@"
    private val usersToRemove = mutableSetOf<String>()
    private val usersToRemovePam = mutableSetOf<String>()
    private val channelsToRemove = mutableSetOf<String>()
    private val channelsToRemovePam = mutableSetOf<String>()

    fun createChat(action: () -> ChatImpl): ChatImpl {
        return action().also {
            usersToRemove.add(it.currentUser.id)
        }
    }

    fun createPamChat(action: () -> ChatImpl): ChatImpl {
        return action().also {
            usersToRemovePam.add(it.currentUser.id)
        }
    }

    val chat: ChatImpl by lazy(LazyThreadSafetyMode.NONE) {
        ChatImpl(ChatConfiguration(), pubnub).also { usersToRemove.add(it.currentUser.id) }
    }
    val chat02: ChatImpl by lazy(LazyThreadSafetyMode.NONE) {
        ChatImpl(ChatConfiguration(), pubnub02).also { usersToRemove.add(it.currentUser.id) }
    }
    val chatPamServer: ChatImpl by lazy(LazyThreadSafetyMode.NONE) {
        ChatImpl(ChatConfiguration(), pubnubPamServer).also { usersToRemovePam.add(it.currentUser.id) }
    }
    val chatPamClient: ChatImpl by lazy(LazyThreadSafetyMode.NONE) {
        ChatImpl(ChatConfiguration(), pubnubPamClient).also { usersToRemovePam.add(it.currentUser.id) }
    }
    val channel01: Channel by lazy(LazyThreadSafetyMode.NONE) {
        ChannelImpl(
            chat = chat,
            id = channel01Id,
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        ).also { channelsToRemove.add(it.id) }
    }
    val channel01Chat02: Channel by lazy(LazyThreadSafetyMode.NONE) {
        ChannelImpl(
            chat = chat02,
            id = channel01Id,
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        ).also { channelsToRemove.add(it.id) }
    }
    val channel02: Channel by lazy(LazyThreadSafetyMode.NONE) {
        ChannelImpl(
            chat = chat,
            id = randomString() + "!_=-@",
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        ).also { channelsToRemove.add(it.id) }
    }
    val threadChannel: ThreadChannel by lazy(LazyThreadSafetyMode.NONE) {
        ThreadChannelImpl(
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
    }
    val channelPam: Channel by lazy(LazyThreadSafetyMode.NONE) {
        ChannelImpl(
            chat = chatPamServer,
            id = randomString() + "!_=-@",
            name = randomString(),
            custom = mapOf(randomString() to randomString()),
            description = randomString(),
            updated = randomString(),
            status = randomString(),
            type = ChannelType.DIRECT
        ).also { channelsToRemovePam.add(it.id) }
    }
    val someUser: User by lazy(LazyThreadSafetyMode.NONE) { chat.currentUser }
    val someUser02: User by lazy(LazyThreadSafetyMode.NONE) { chat02.currentUser }
    val userPamServer: User by lazy(LazyThreadSafetyMode.NONE) { chatPamServer.currentUser }
    val userPamClient: User by lazy(LazyThreadSafetyMode.NONE) { chatPamClient.currentUser }

    @AfterTest
    override fun after() {
        runTest {
            val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
            try {
                supervisorScope {
                    usersToRemove.forEach {
                        launch(exceptionHandler) {
                            pubnub.removeUUIDMetadata(it).await()
                        }
                    }
                    if (PLATFORM != "iOS") {
                        usersToRemovePam.forEach {
                            launch(exceptionHandler) {
                                pubnubPamServer.removeUUIDMetadata(it).await()
                            }
                        }
                    }
                    channelsToRemove.forEach {
                        launch(exceptionHandler) {
                            pubnub.removeChannelMetadata(it).await()
                        }
                    }
                    if (PLATFORM != "iOS") {
                        channelsToRemovePam.forEach {
                            launch(exceptionHandler) {
                                pubnubPamServer.removeChannelMetadata(it).await()
                            }
                        }
                    }
                }
            } finally {
                chat.destroy()
                chat02.destroy()
                chatPamClient.destroy()
                chatPamServer.destroy()
            }
        }
        super.after()
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
