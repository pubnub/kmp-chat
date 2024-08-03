package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.UserId
import com.pubnub.api.endpoints.FetchMessages
import com.pubnub.api.endpoints.objects.member.GetChannelMembers
import com.pubnub.api.endpoints.pubsub.Publish
import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.member.PNUUIDDetailsLevel
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.createPNConfiguration
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.PushNotificationsConfig
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.MINIMAL_TYPING_INDICATOR_TIMEOUT
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.BaseChannel
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.HistoryResponse
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.kmp.utils.BaseTest
import com.pubnub.test.await
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ChannelTest : BaseTest() {
    private lateinit var objectUnderTest: ChannelImpl

    private val chat: ChatInternal = mock(MockMode.strict)
    private lateinit var chatConfig: ChatConfiguration
    private val channelId = "testId"
    private val name = "testName"
    private val customData = mapOf("testCustom" to "custom")
    private val custom = createCustomObject(customData)
    private val description = "testDescription"
    private val status = "testStatus"
    private val type = ChannelType.DIRECT
    private val updated = "testUpdated"
    private val typingTimeout = 1001.milliseconds
    private val pubNub: PubNub = mock(MockMode.strict)

    @BeforeTest
    fun setUp() {
        chatConfig = ChatConfiguration(
            typingTimeout = this@ChannelTest.typingTimeout
        )

        every { chat.config } returns chatConfig
        every { chat.pubNub } returns pubNub
        every { pubNub.configuration } returns createPNConfiguration(UserId("demo"), "demo", "demo")
        objectUnderTest = createChannel(type)
    }

    private fun createChannel(type: ChannelType, clock: Clock = Clock.System) = ChannelImpl(
        chat = chat,
        clock = clock,
        id = channelId,
        name = name,
        custom = customData,
        description = description,
        updated = updated,
        status = status,
        type = type
    )

    @Test
    fun canUpdateChannel() {
        every { chat.updateChannel(any(), any(), any(), any(), any(), any()) } returns objectUnderTest.asFuture()

        objectUnderTest.update(
            name = name,
            custom = custom,
            description = description,
            status = status,
            type = type,
        ).async {}

        verify { chat.updateChannel(channelId, name, custom, description, status, type) }
    }

    @Test
    fun canSoftDeleteChannel() {
        val softDelete = true
        val channelFutureMock: PNFuture<Channel> = mock(MockMode.strict)
        every { chat.deleteChannel(any(), any()) } returns channelFutureMock

        val deleteChannelFuture: PNFuture<Channel> = objectUnderTest.delete(soft = softDelete)

        assertEquals(channelFutureMock, deleteChannelFuture)
        verify { chat.deleteChannel(id = channelId, soft = softDelete) }
    }

    @Test
    fun canForwardMessage() {
        val message = createMessage()
        val publishResultMock: PNFuture<PNPublishResult> = mock(MockMode.strict)
        every { chat.forwardMessage(any(), any()) } returns publishResultMock

        val forwardMessage = objectUnderTest.forwardMessage(message)

        assertEquals(publishResultMock, forwardMessage)
        verify { chat.forwardMessage(message, channelId) }
    }

    @Test
    fun whenChannelIsPublicStartTypingShouldResultFailure() {
        objectUnderTest = createChannel(ChannelType.PUBLIC)

        objectUnderTest.startTyping().async { resutl ->
            assertTrue(resutl.isFailure)
            assertEquals("Typing indicators are not supported in Public chats.", resutl.exceptionOrNull()?.message)
        }
    }

    @Test
    fun whenTypingSentAlreadyStartTypingShouldImmediatelyResultSuccess() {
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis = typingSent.plus(1.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)
        objectUnderTest.startTyping().async { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        verify(exactly(0)) { chat.emitEvent(any(), any()) }
    }

    @Test
    fun whenTypingSentAlreadyButTimeoutExpiredStartTypingShouldEmitStartTypingEvent() {
        every { chat.emitEvent(any(), any()) } returns PNPublishResult(1L).asFuture()
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis =
            typingSent.plus(typingTimeout).plus(MINIMAL_TYPING_INDICATOR_TIMEOUT).plus(1.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)
        objectUnderTest.startTyping().async { result ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        verify {
            chat.emitEvent(
                channelId = channelId,
                payload = matching { it is EventContent.Typing && it.value },
            )
        }
    }

    @Test
    fun whenTypingNotSendShouldEmitStartTypingEvent() {
        every { chat.emitEvent(any(), any()) } returns PNPublishResult(1L).asFuture()

        // when
        objectUnderTest.startTyping().async { result ->
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        // then
        verify {
            chat.emitEvent(
                channelId = channelId,
                payload = matching { it is EventContent.Typing && it.value },
            )
        }
    }

    @Test
    fun whenTypingTimoutSetToZeroShouldNotEmitSignalWithinFirstSecond() {
        every { chat.config } returns ChatConfiguration(typingTimeout = 0.milliseconds)
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis = typingSent.plus(1.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)

        objectUnderTest.startTyping().async { result ->
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        verify(exactly(0)) { chat.emitEvent(any(), any()) }
    }

    @Test
    fun whenChannelIsPublicShouldResultFailure() {
        objectUnderTest = createChannel(ChannelType.PUBLIC)

        objectUnderTest.stopTyping().async { result ->
            assertTrue(result.isFailure)
            assertEquals("Typing indicators are not supported in Public chats.", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun whenStopTypingAlreadySentStopTypingShouldImmediatelyResultSuccess() {
        objectUnderTest.stopTyping().async { result ->
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        verify(exactly(0)) { chat.emitEvent(any(), any()) }
    }

    @Test
    fun whenTimeElapsedShouldNotSendSignalButReturnImmediately() {
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis = typingSent.plus(2001.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)

        objectUnderTest.stopTyping().async { result ->
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        verify(exactly(0)) { chat.emitEvent(any(), any()) }
    }

    @Test
    fun whenTimeNotElapsedShouldSendSignal() {
        val typingSent: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val currentTimeStampInMillis = typingSent.plus(1.milliseconds)
        val customClock = object : Clock {
            override fun now(): Instant {
                return currentTimeStampInMillis
            }
        }
        objectUnderTest = createChannel(type, customClock)
        objectUnderTest.setTypingSent(typingSent)
        every { chat.emitEvent(any(), any()) } returns PNPublishResult(1L).asFuture()

        // when
        objectUnderTest.stopTyping().async { result ->
            assertTrue(result.isSuccess)
            assertEquals(Unit, result.getOrNull())
        }

        // then
        verify {
            chat.emitEvent(
                channelId = channelId,
                payload = matching { it is EventContent.Typing && !it.value },
            )
        }
    }

    @Test
    fun whenTimeoutElapseShouldRemoveExpiredTypingIndicators() {
        val typingSent1: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val now = typingSent1.plus(2.seconds)
        val typingIndicatorsForTest = mutableMapOf<String, Instant>()
        val user1 = "user1"
        val user2 = "user2"
        typingIndicatorsForTest[user1] = typingSent1
        typingIndicatorsForTest[user2] = typingSent1.plus(2.milliseconds)
        BaseChannel.removeExpiredTypingIndicators(
            objectUnderTest.chat.config.typingTimeout,
            typingIndicatorsForTest,
            now
        )

        assertFalse(typingIndicatorsForTest.contains(user1))
        assertFalse(typingIndicatorsForTest.contains(user2))
    }

    @Test
    fun whenTimeoutNotElapseShouldNotRemoveExpiredTypingIndicators() {
        val typingSent1: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val now = typingSent1.plus(50.milliseconds)
        val typingIndicatorsForTest = mutableMapOf<String, Instant>()
        val user1 = "user1"
        val user2 = "user2"
        typingIndicatorsForTest[user1] = typingSent1
        typingIndicatorsForTest[user2] = typingSent1.plus(2.milliseconds)

        BaseChannel.removeExpiredTypingIndicators(
            objectUnderTest.chat.config.typingTimeout,
            typingIndicatorsForTest,
            now
        )

        assertTrue(typingIndicatorsForTest.contains(user1))
        assertTrue(typingIndicatorsForTest.contains(user2))
    }

    @Test
    fun whenUserIsTypingAndTypingIndicatorMapDoesNotContainEntryShouldAddIt() {
        val now: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val userId = "user1"
        val isTyping = true
        val typingIndicators = mutableMapOf<String, Instant>()

        BaseChannel.updateUserTypingStatus(userId, isTyping, now, typingIndicators)

        assertTrue(typingIndicators.contains(userId))
    }

    @Test
    fun whenUserIsNotTypingAndTypingIndicatorMapContainEntryShouldRemoveIt() {
        val typingSent1: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val now = typingSent1.plus(50.milliseconds)
        val userId = "user1"
        val isTyping = false
        val typingIndicators = mutableMapOf(userId to typingSent1)

        BaseChannel.updateUserTypingStatus(userId, isTyping, now, typingIndicators)

        assertFalse(typingIndicators.contains(userId))
    }

    @Test
    fun whenUserIsTypingAndTypingIndicatorMapContainEntryShouldUpdateTime() {
        val typingSent1: Instant = Instant.fromEpochMilliseconds(1234567890000)
        val now = typingSent1.plus(50.milliseconds)
        val userId = "user1"
        val isTyping = true
        val typingIndicators = mutableMapOf(userId to typingSent1)

        BaseChannel.updateUserTypingStatus(userId, isTyping, now, typingIndicators)

        assertTrue(typingIndicators.contains(userId))
        assertEquals(now, typingIndicators[userId])
    }

    private fun createMessage(): Message {
        return MessageImpl(
            chat = chat,
            timetoken = 123345,
            content = EventContent.TextMessageContent(
                text = "justo",
                files = listOf()
            ),
            channelId = "noster",
            userId = "myUserId",
            actions = mapOf(),
            meta = mapOf()
        )
    }

    @Test
    fun canCallIsPresent() {
        every { chat.isPresent(any(), any()) } returns true.asFuture()

        objectUnderTest.isPresent(userId = "user").async { result ->
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!)
        }

        verify { chat.isPresent("user", channelId) }
    }

    @Test
    fun canCallWhoIsPresent() {
        every { chat.whoIsPresent(any()) } returns emptyList<String>().asFuture()

        objectUnderTest.whoIsPresent().async { result: Result<Collection<String>> ->
            assertTrue(result.isSuccess)
        }

        verify { chat.whoIsPresent(channelId) }
    }

    @Test
    fun getHistoryReturnsMessages() {
        // given
        val startToken = 1L
        val endToken = 2000L
        val fetchMessages: FetchMessages = mock(MockMode.strict)

        every { chat.pubNub } returns pubNub
        every {
            pubNub.fetchMessages(
                any(),
                PNBoundedPage(startToken, endToken, 25),
                any(),
                any(),
                any(),
                any()
            )
        } returns fetchMessages

        val message1 = "message text"
        val message2 = "second message"
        val user1 = "myUser"
        val user2 = "myUser2"
        val timetoken1 = 10000L
        val timetoken2 = 10001L

        every { fetchMessages.async(any()) } calls { (callback1: Consumer<Result<PNFetchMessagesResult>>) ->
            callback1.accept(
                Result.success(
                    createPnFetchMessagesResult(channelId, user1, message1, timetoken1, user2, message2, timetoken2)
                )
            )
        }

        // when
        objectUnderTest.getHistory(startToken, endToken).async {
            // then
            assertTrue { it.isSuccess }
            it.onSuccess { result: HistoryResponse<Message> ->
                assertEquals(
                    listOf(
                        MessageImpl(
                            chat,
                            timetoken1,
                            EventContent.TextMessageContent(message1),
                            channelId,
                            user1,
                            null,
                            null
                        ),
                        MessageImpl(
                            chat,
                            timetoken2,
                            EventContent.TextMessageContent(message2),
                            channelId,
                            user2,
                            null,
                            null
                        ),
                    ),
                    result.messages
                )
            }
        }
    }

    @Test
    fun sendText_failure_when_quotedmessage_from_another_channel() = runTest(timeout = 10.seconds) {
        val exception = assertFailsWith<PubNubException> {
            objectUnderTest.sendText(
                "text",
                quotedMessage = MessageImpl(
                    chat,
                    1L,
                    EventContent.TextMessageContent("text"),
                    "other_channel",
                    "other_user"
                )
            ).await()
        }
        assertEquals("You cannot quote messages from other channels", exception.message)
    }

    @Test
    fun sendTextAllParametersArePassedToPublish() {
        val publish: Publish = mock(MockMode.autofill)
        every { pubNub.publish(any(), any(), any(), any(), any(), any(), any()) } returns publish
        val messageText = "someText"
        val message =
            MessageImpl(chat, 1000L, EventContent.TextMessageContent(messageText), channelId, "some user", null, null)
        val mentionedUser1 = "mention1"
        val referencedChannel1 = "referenced1"
        val userName = "someName"
        val channelName = "someChannel"
        val link = "some link"
        val ttl = 100
        objectUnderTest.sendText(
            text = messageText,
            meta = mapOf("custom_meta" to "custom"),
            shouldStore = true,
            usePost = false,
            ttl = ttl,
            mentionedUsers = mapOf(0 to MessageMentionedUser(mentionedUser1, userName)),
            referencedChannels = mapOf(0 to MessageReferencedChannel(referencedChannel1, channelName)),
            textLinks = listOf(com.pubnub.chat.types.TextLink(1, 20, link)),
            quotedMessage = message,
            null, // todo when files work
        ).async {}

        verify {
            pubNub.publish(
                channelId,
                mapOf("type" to "text", "text" to messageText, "files" to emptyList<String>()),
                mapOf(
                    "custom_meta" to "custom",
                    "mentionedUsers" to mapOf(
                        "0" to mapOf("id" to mentionedUser1, "name" to userName)
                    ),
                    "referencedChannels" to mapOf(
                        "0" to mapOf("id" to referencedChannel1, "name" to channelName)
                    ),
                    "textLinks" to listOf(
                        mapOf(
                            "startIndex" to 1,
                            "endIndex" to 20,
                            "link" to link
                        )
                    ),
                    "quotedMessage" to mapOf(
                        "timetoken" to message.timetoken,
                        "text" to message.text,
                        "userId" to message.userId
                    )
                ),
                true,
                false,
                true,
                ttl
            )
        }
    }

    @Test
    fun canGetRestrictionsWithNullUser() {
        val limit = 1
        val page: PNPage? = PNPage.PNNext("nextPageHash")
        val sort = listOf(PNSortKey.PNAsc(PNMemberKey.UUID_ID))
        val fetChannelMembers: GetChannelMembers = mock(MockMode.strict)
        every { chat.pubNub } returns pubNub
        every {
            pubNub.getChannelMembers(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns fetChannelMembers

        objectUnderTest.getRestrictions(user = null, limit = limit, page = page, sort = sort)

        verify {
            pubNub.getChannelMembers(
                channel = "PUBNUB_INTERNAL_MODERATION_$channelId",
                limit = limit,
                page = page,
                filter = null,
                sort = sort,
                includeCount = true,
                includeCustom = true,
                includeUUIDDetails = PNUUIDDetailsLevel.UUID_WITH_CUSTOM,
                includeType = true
            )
        }
    }

    @Test
    fun canGetRestrictionsByUser() {
        val user = UserImpl(chat = chat, id = "userId")
        val limit = 1
        val page: PNPage = PNPage.PNNext("nextPageHash")
        val sort = listOf(PNSortKey.PNAsc(PNMemberKey.UUID_ID))
        val getChannelMembers: GetChannelMembers = mock(MockMode.strict)
        every { chat.pubNub } returns pubNub
        every {
            pubNub.getChannelMembers(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns getChannelMembers

        objectUnderTest.getRestrictions(user = user, limit = limit, page = page, sort = sort)

        verify {
            pubNub.getChannelMembers(
                channel = "PUBNUB_INTERNAL_MODERATION_$channelId",
                limit = limit,
                page = page,
                filter = "uuid.id == 'userId'",
                sort = sort,
                includeCount = true,
                includeCustom = true,
                includeUUIDDetails = PNUUIDDetailsLevel.UUID_WITH_CUSTOM,
                includeType = true
            )
        }
    }

    @Test
    fun shouldThrowExceptionWhenSecretKeyIsNotSet() {
        val user = UserImpl(chat = chat, id = "userId")

        objectUnderTest.setRestrictions(user).async { result: Result<Unit> ->
            assertTrue(result.isFailure)
            assertEquals(
                "Moderation restrictions can only be set by clients initialized with a Secret Key.",
                result.exceptionOrNull()?.message
            )
        }
    }

    @Test
    fun whenChannelIsPublicGetTypingShouldResultFailure() {
        objectUnderTest = createChannel(ChannelType.PUBLIC)
        val e = assertFailsWith<PubNubException> {
            objectUnderTest.getTyping {}
        }
        assertEquals("Typing indicators are not supported in Public chats.", e.message)
    }

    @Test
    fun shouldUpdateTypingTimeWhenUserIsTyping() {
        // todo whenTypingStatusIndicateThatUserIsTypingAndTypingEventReceiveGetTypingShouldUpdateTime
    }

    @Test
    fun shouldRemoveTypingStatusWhenUserStopsTyping() {
        // todo whenTypingStatusIndicateThatUserIsTypingAndNotTypingEventReceiveGetTypingShouldRemoveTypingStatus
    }

    @Test
    fun shouldCreateTypingStatusWhenUserStartsTyping() {
        // todo whenThereIsNoTypingStatusForUserAndTypingEventReceiveGetTypingShouldCreateTypingStatus
    }

    @Test
    fun getTypingShouldRemoveExpiredTypingIndicators() {
        // todo
    }

    @Test
    fun getPushPayload_empty_when_sendPushes_is_false() {
        val config = PushNotificationsConfig(false, null, PNPushType.FCM, null, PNPushEnvironment.PRODUCTION)

        val result = BaseChannel.getPushPayload(createChannel(type), "some text", config)

        assertEquals(emptyMap(), result)
    }

    @Test
    fun getPushPayload_no_apns2_topic() {
        val userId = "some_user"
        val text = "some text"
        val config = PushNotificationsConfig(true, "abc", PNPushType.FCM, null, PNPushEnvironment.PRODUCTION)

        every { chat.currentUser } returns UserImpl(chat, userId)

        val result = BaseChannel.getPushPayload(createChannel(type), text, config)

        assertEquals(objectUnderTest.name, result["pn_fcm"]["data"]["subtitle"])
        assertEquals(userId, result["pn_fcm"]["notification"]["title"])
        assertEquals(text, result["pn_fcm"]["notification"]["body"])
        assertEquals(userId, result["pn_fcm"]["android"]["notification"]["title"])
        assertEquals(text, result["pn_fcm"]["android"]["notification"]["body"])
        assertEquals("default", result["pn_fcm"]["android"]["notification"]["sound"])
    }

    @Test
    fun getPushPayload_with_apns2_topic() {
        val userId = "some_user"
        val text = "some text"
        val topic = "apns_topic"
        val config = PushNotificationsConfig(true, "abc", PNPushType.FCM, topic, PNPushEnvironment.PRODUCTION)
        every { chat.currentUser } returns UserImpl(chat, userId)

        val result = BaseChannel.getPushPayload(createChannel(type), text, config)

        assertEquals(objectUnderTest.name, result["pn_fcm"]["data"]["subtitle"])

        assertEquals(userId, result["pn_fcm"]["notification"]["title"])
        assertEquals(text, result["pn_fcm"]["notification"]["body"])
        assertEquals(userId, result["pn_fcm"]["android"]["notification"]["title"])
        assertEquals(text, result["pn_fcm"]["android"]["notification"]["body"])
        assertEquals("default", result["pn_fcm"]["android"]["notification"]["sound"])

        assertEquals(userId, result["pn_apns"]["aps"]["alert"]["title"])
        assertEquals(text, result["pn_apns"]["aps"]["alert"]["body"])
        assertEquals("default", result["pn_apns"]["aps"]["sound"])

        assertEquals(topic, result["pn_apns"]["pn_push"][0]["targets"][0]["topic"])
        assertEquals(
            PNPushEnvironment.PRODUCTION.toParamString(),
            result["pn_apns"]["pn_push"][0]["targets"][0]["environment"]
        )
        assertEquals(objectUnderTest.name, result["pn_apns"]["subtitle"])
    }

    @Test
    fun generateReceipts() {
        val result = BaseChannel.generateReceipts(mapOf("user" to 1L, "user2" to 2L, "user3" to 1L, "user4" to 3L))
        assertEquals(mapOf(1L to listOf("user", "user3"), 2L to listOf("user2"), 3L to listOf("user4")), result)
    }

    @Test
    fun registerForPush_calls_chat() {
        every { chat.registerPushChannels(any()) } returns mock()
        objectUnderTest.registerForPush()

        verify { chat.registerPushChannels(listOf(objectUnderTest.id)) }
    }

    @Test
    fun unregisterFromPush_calls_chat() {
        every { chat.unregisterPushChannels(any()) } returns mock()

        objectUnderTest.unregisterFromPush()

        verify { chat.unregisterPushChannels(listOf(objectUnderTest.id)) }
    }

    @Test
    fun update_calls_chat() {
        every { chat.updateChannel(any(), any(), any(), any(), any(), any()) } returns mock()

        objectUnderTest.update(name, custom, description, status, type)

        verify { chat.updateChannel(channelId, name, custom, description, status, type) }
    }
}

private operator fun Any?.get(s: String): Any? {
    return if (this is Map<*, *>) {
        this.get(s as Any?)
    } else {
        null
    }
}

private operator fun Any?.get(i: Int): Any? {
    return if (this is List<*>) {
        this.get(i)
    } else {
        null
    }
}
