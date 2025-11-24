package com.pubnub.integration

import com.pubnub.api.PubNubException
import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.access_manager.v3.ChannelGrant
import com.pubnub.api.models.consumer.access_manager.v3.UUIDGrant
import com.pubnub.api.models.consumer.objects.membership.ChannelMembershipInput
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.Membership
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.PushNotificationsConfig
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.INTERNAL_USER_MODERATION_CHANNEL_PREFIX
import com.pubnub.chat.internal.SUFFIX_MUTE_1
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.internal.generateRandomUuid
import com.pubnub.chat.internal.utils.cyrb53a
import com.pubnub.chat.listenForEvents
import com.pubnub.chat.membership.MembershipsResponse
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.message.MarkAllMessageAsReadResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.restrictions.RestrictionType
import com.pubnub.chat.types.ChannelMentionData
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetCurrentUserMentionsResult
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.types.JoinResult
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.ThreadMentionData
import com.pubnub.internal.PLATFORM
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.createPubNub
import com.pubnub.test.await
import com.pubnub.test.randomString
import com.pubnub.test.test
import delayForHistory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import tryLong
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ChatIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun initializeShouldThrow403WhenPamEnableAndNoToken() = runTest {
        val exception = assertFailsWith<PubNubException> {
            chatPamClient.initialize().await()
        }

        assertEquals(403, exception.statusCode)
    }

    @Test
    fun test_storeUserActivityInterval_and_storeUserActivityTimestamps() = runTest {
        val chat =
            createChat {
                ChatImpl(
                    config = ChatConfiguration(
                        storeUserActivityInterval = 100.seconds,
                        storeUserActivityTimestamps = true
                    ),
                    pubNub = pubnub,
                )
            }
        chat.initialize().await()

        val user: User = chat.getUser(chat.currentUser.id).await()!!

        assertTrue(user.custom?.get("lastActiveTimestamp") != null)
    }

    @Test
    fun initializeShouldPassWhenPamEnableAndTokenProvided() = runTest {
        if (PLATFORM == "iOS") {
            return@runTest
        }

        pubnubPamClient = createPubNub(configPamClient)
        val token = chatPamServer.pubNub.grantToken(
            ttl = 1,
            channels = listOf(
                ChannelGrant.name(get = true, name = "any", read = true, write = true, manage = true),
                ChannelGrant.name(
                    name = "PN_PRV.${pubnubPamClient.configuration.userId.value}.$SUFFIX_MUTE_1",
                    read = true,
                )
            ), // get = true
            uuids = listOf(
                UUIDGrant.id(id = pubnubPamClient.configuration.userId.value, get = true, update = true),
                UUIDGrant.id(
                    id = "PN_PRV.${pubnubPamClient.configuration.userId.value}.$SUFFIX_MUTE_1",
                    update = true,
                    delete = true,
                    get = true,
                )
            ) // this is important
        ).await().token

        pubnubPamClient.setToken(token)
        val chatPamClient = createPamChat { ChatImpl(ChatConfiguration(), pubnubPamClient) }
        val initializeChat = chatPamClient.initialize().await()

        assertEquals(token, initializeChat.pubNub.getToken())
    }

    @Test
    fun createUser() = runTest {
        val user = chat.createUser(someUser).await()

        assertEquals(someUser, user.asImpl().copy(updated = null, lastActiveTimestamp = null, eTag = null))
        assertNotNull(user.updated)
    }

    @Test
    fun updateUser() = runTest {
        val user = chat.createUser(someUser).await()
        val expectedUser = user.asImpl().copy(
            name = randomString(),
            externalId = randomString(),
            profileUrl = randomString(),
            email = randomString(),
            custom = mapOf(
                randomString() to randomString()
            ),
            type = randomString(),
            updated = null,
            eTag = null
        )

        val updatedUser = chat.updateUser(
            expectedUser.id,
            expectedUser.name,
            expectedUser.externalId,
            expectedUser.profileUrl,
            expectedUser.email,
            expectedUser.custom?.let { createCustomObject(it) },
            expectedUser.status,
            expectedUser.type
        ).await()

        assertEquals(expectedUser, updatedUser.asImpl().copy(updated = null, lastActiveTimestamp = null, eTag = null))
        assertNotNull(updatedUser.updated)
    }

    @Test
    fun updateUser_doesntExist() = runTest {
        val e = assertFailsWith<PubNubException> {
            chat.updateUser(someUser.id, name = randomString()).await()
        }

        assertEquals(PubNubErrorMessage.USER_NOT_EXIST, e.message)
    }

    @Test
    fun createDirectConversation() = runTest {
        chat.initialize().await()
        // when
        val result = chat.createDirectConversation(someUser).await()

        // then
        val sortedUsers = listOf(chat.currentUser.id, someUser.id).sorted()
        assertEquals("direct.${cyrb53a("${sortedUsers[0]}&${sortedUsers[1]}")}", result.channel.id)

        assertEquals(
            chat.currentUser.asImpl().copy(updated = null, lastActiveTimestamp = null),
            result.hostMembership.user.asImpl().copy(updated = null, lastActiveTimestamp = null)
        )
        assertEquals(someUser, result.inviteeMembership.user.asImpl())

        assertEquals(result.channel, result.hostMembership.channel)
        assertEquals(result.channel, result.inviteeMembership.channel)
    }

    @Test
    fun createGroupConversation() = runTest {
        val otherUsers = listOf(UserImpl(chat, randomString()), UserImpl(chat, randomString()))

        // when
        val result = chat.createGroupConversation(otherUsers).await()

        // then
        assertEquals(
            chat.currentUser,
            result.hostMembership.user.asImpl().copy(updated = null, lastActiveTimestamp = null)
        )
        assertEquals(otherUsers.size, result.inviteeMemberships.size)
        result.inviteeMemberships.forEach { inviteeMembership ->
            assertEquals(
                otherUsers.first { it.id == inviteeMembership.user.id },
                inviteeMembership.user.asImpl().copy(updated = null, lastActiveTimestamp = null)
            )
            assertEquals(result.channel, inviteeMembership.channel)
        }

        assertEquals(result.channel, result.hostMembership.channel)
    }

    @Test
    fun createGroupConversation_withEmptyUserList_shouldSucceedWithOnlyHost() = runTest {
        // given
        val emptyUserList = emptyList<User>()

        // when
        val result = chat.createGroupConversation(emptyUserList).await()

        // then
        assertNotNull(result.channel)
        assertNotNull(result.channel.id)

        assertEquals(
            chat.currentUser,
            result.hostMembership.user.asImpl().copy(updated = null, lastActiveTimestamp = null)
        )
        assertEquals(result.channel, result.hostMembership.channel)

        assertTrue(
            result.inviteeMemberships.isEmpty(),
            "inviteeMemberships should be empty when no users are invited"
        )

        // verify the channel exists and is accessible
        val fetchedChannel = chat.getChannel(result.channel.id).await()
        assertNotNull(fetchedChannel, "Created channel should be fetchable")
        assertEquals(result.channel.id, fetchedChannel?.id)
    }

    @Test
    fun can_markAllMessagesAsRead() = runTest {
        // create two membership for user one with "lastReadMessageTimetoken" and second without.
        val lastReadMessageTimetokenValue: Long = 17195737006492403
        val custom: CustomObject =
            createCustomObject(mapOf("lastReadMessageTimetoken" to lastReadMessageTimetokenValue))
        val channelId01 = channel01.id
        val channelId02 = channel02.id
        val membership01: ChannelMembershipInput = PNChannelMembership.Partial(channelId01, custom)
        val membership02: ChannelMembershipInput = PNChannelMembership.Partial(channelId02)
        chat.pubNub.setMemberships(channels = listOf(membership01, membership02), userId = chat.currentUser.id).await()

        // to each channel add two messages(we want to check if last message will be taken by fetchMessages with limit = 1)
        channel01.sendText("message01In$channelId01").await()
        val lastPublishToChannel01 = channel01.sendText("message02In$channelId01").await()
        channel02.sendText("message01In$channelId02").await()
        val lastPublishToChannel02 = channel02.sendText("message02In$channelId02").await()

        // register lister of "Receipt" event
        val assertionErrorInListener01 = CompletableDeferred<AssertionError?>()
        val assertionErrorInListener02 = CompletableDeferred<AssertionError?>()

        var removeListenerAndUnsubscribe01: AutoCloseable? = null
        var removeListenerAndUnsubscribe02: AutoCloseable? = null
        pubnub.test(backgroundScope, checkAllEvents = false) {
            pubnub.awaitSubscribe(listOf(channel01.id, channel02.id)) {
                removeListenerAndUnsubscribe01 = chat.listenForEvents<EventContent.Receipt>(
                    channelId = channelId01
                ) { event: Event<EventContent.Receipt> ->
                    try {
                        // we need to have try/catch here because assertion error will not cause test to fail
                        assertEquals(channelId01, event.channelId)
                        assertEquals(chat.currentUser.id, event.userId)
                        assertNotEquals(lastReadMessageTimetokenValue, event.payload.messageTimetoken)
                        assertEquals(lastPublishToChannel01.timetoken, event.payload.messageTimetoken)
                        assertionErrorInListener01.complete(null)
                    } catch (e: AssertionError) {
                        assertionErrorInListener01.complete(e)
                    }
                }

                removeListenerAndUnsubscribe02 = chat.listenForEvents(
                    type = EventContent.Receipt::class,
                    channelId = channelId02
                ) { event: Event<EventContent.Receipt> ->
                    try {
                        // we need to have try/catch here because assertion error will not cause test to fail
                        assertEquals(channelId02, event.channelId)
                        assertEquals(chat.currentUser.id, event.userId)
                        assertNotEquals(lastReadMessageTimetokenValue, event.payload.messageTimetoken)
                        assertEquals(lastPublishToChannel02.timetoken, event.payload.messageTimetoken)
                        assertionErrorInListener02.complete(null)
                    } catch (e: AssertionError) {
                        assertionErrorInListener02.complete(e)
                    }
                }
            }

            // then
            delayForHistory()
            val markAllMessageAsReadResponse: MarkAllMessageAsReadResponse = chat.markAllMessagesAsRead().await()

            // verify response contains updated "lastReadMessageTimetoken"
            markAllMessageAsReadResponse.memberships.forEach { membership: Membership ->
                // why membership.custom!!["lastReadMessageTimetoken"] returns double? <--this is default behaviour of GSON
                assertNotEquals(
                    lastReadMessageTimetokenValue,
                    membership.custom!!["lastReadMessageTimetoken"].tryLong()
                )
            }

            // verify each Membership has updated custom value for "lastReadMessageTimetoken"
            val userMembership: MembershipsResponse = chat.currentUser.getMemberships().await()
            userMembership.memberships.forEach { membership: Membership ->
                assertNotEquals(
                    lastReadMessageTimetokenValue,
                    membership.custom!!["lastReadMessageTimetoken"].tryLong()
                )
            }

            // verify assertion inside listeners
            assertionErrorInListener01.await()?.let { throw it }
            assertionErrorInListener02.await()?.let { throw it }

            // remove messages
            chat.pubNub.deleteMessages(listOf(channelId01, channelId02))

            // remove listeners and unsubscribe
            removeListenerAndUnsubscribe01?.close()
            removeListenerAndUnsubscribe02?.close()

            // remove memberships (user). This will be done in tearDown method
        }
    }

    @Test
    fun can_getUnreadMessagesCount_onMembership() = runTest {
        val channelId01 = channel01.id

        // send message
        channel01.sendText("message01In$channelId01").await()
        // join (implicitly setLastReadMessageTimetoken)
        val joinResult: JoinResult = channel01.join { }.await()
        val membership = joinResult.membership
        delayForHistory()
        val unreadMessageCount: Long? = membership.getUnreadMessagesCount().await()
        assertEquals(0, unreadMessageCount)

        // send message
        channel01.sendText("message02In$channelId01").await()
        delayForHistory()

        val unreadMessageCount02: Long? = membership.getUnreadMessagesCount().await()
        assertEquals(1L, unreadMessageCount02)

        // markAllMessagesAsRead
        delayForHistory()
        val markAllMessageAsReadResponse: MarkAllMessageAsReadResponse = chat.markAllMessagesAsRead().await()
        val membershipWithUpgradeLastReadMessageTimetoken = markAllMessageAsReadResponse.memberships.first()

        delayForHistory()
        val unreadMessageCount03: Long? = membershipWithUpgradeLastReadMessageTimetoken.getUnreadMessagesCount().await()
        assertEquals(0, unreadMessageCount03)

        // remove messages
        chat.pubNub.deleteMessages(listOf(channelId01)).await()
    }

    @Test
    fun can_getUnreadMessageCounts_global() = runTest {
        val channelId01 = channel01.id
        val channelId02 = channel02.id

        // join two channels
        channel01.join { }.await()
        channel02.join { }.await()

        // send message
        channel01.sendText("message01In$channelId01").await()
        channel02.sendText("message01In$channelId02").await()
        delayForHistory()

        // read message count
        var unreadMessagesCounts = chat.getUnreadMessagesCounts().await()
        var unreadMessagesCountsForChannel01: Long =
            unreadMessagesCounts.find { unreadMessagesCount -> unreadMessagesCount.channel.id == channelId01 }?.count
                ?: 0
        var unreadMessagesCountsForChannel02: Long =
            unreadMessagesCounts.find { unreadMessagesCount -> unreadMessagesCount.channel.id == channelId02 }?.count
                ?: 0
        assertEquals(1, unreadMessagesCountsForChannel01)
        assertEquals(1, unreadMessagesCountsForChannel02)

        // markUnread
        delayForHistory()
        chat.markAllMessagesAsRead().await()

        delayForHistory()
        // read message count
        unreadMessagesCounts = chat.getUnreadMessagesCounts().await()
        unreadMessagesCountsForChannel01 =
            unreadMessagesCounts.find { unreadMessagesCount: GetUnreadMessagesCounts ->
                unreadMessagesCount.channel.id == channelId01
            }?.count
                ?: 0
        unreadMessagesCountsForChannel02 =
            unreadMessagesCounts.find { unreadMessagesCount: GetUnreadMessagesCounts ->
                unreadMessagesCount.channel.id == channelId02
            }?.count
                ?: 0
        assertEquals(0, unreadMessagesCountsForChannel01)
        assertEquals(0, unreadMessagesCountsForChannel02)

        // remove messages
        chat.pubNub.deleteMessages(listOf(channelId01, channelId02))
    }

    @Test
    fun shouldReturnNoChannelSuggestions_whenNoDataInCacheAndNoChannelsInChat() = runTest {
        val channelSuggestions = chat.getChannelSuggestions("sas#las").await()
        assertEquals(0, channelSuggestions.size)
    }

    @Test
    fun shouldReturnChannelSuggestions_whenNoDataInCacheButChannelAvailableInChat() =
        runTest {
            val channelName = "channelName_${channel01.id}"
            chat.createChannel(id = channel01.id, name = channelName).await()

            val channelSuggestions = chat.getChannelSuggestions(channelName).await()

            assertEquals(1, channelSuggestions.size)
            assertEquals(channel01.id, channelSuggestions.first().id)
            assertEquals(channelName, channelSuggestions.first().name)
        }

    @Test
    fun shouldReturnNoUserSuggestions_whenNoDatInCacheAndNoChannelsInChat() = runTest {
        val userSuggestions = chat.getUserSuggestions("las").await()
        assertEquals(0, userSuggestions.size)
    }

    @Test
    fun shouldReturnUserSuggestions_whenNoDataInCacheButUserAvailableInChat() = runTest {
        val userName = "userName_${someUser.id}"
        chat.createUser(id = someUser.id, name = userName).await()

        val userSuggestions = chat.getUserSuggestions(userName).await()

        assertEquals(1, userSuggestions.size)
        assertEquals(someUser.id, userSuggestions.first().id)
        assertEquals(userName, userSuggestions.first().name)
    }

    @Test
    fun register_unregister_list_pushNotificationOnChannel() = runTest {
        // set up push config
        val chatConfig = ChatConfiguration(
            pushNotifications = PushNotificationsConfig(
                sendPushes = false,
                deviceToken = "myDeviceId",
                deviceGateway = PNPushType.FCM,
                apnsTopic = null,
                apnsEnvironment = PNPushEnvironment.PRODUCTION
            )
        )
        val chat = createChat { ChatImpl(chatConfig, pubnub) }

        // remove all pushNotificationChannels
        chat.unregisterAllPushChannels().await()
        delayInMillis(1500)

        // list pushNotification
        assertPushChannels(chat, 0)

        // register 3 channels
        val channel01 = "channel01"
        val channel02 = "channel02"
        val channel03 = "channel03"
        chat.registerPushChannels(listOf(channel01, channel02, channel03)).await()
        delayInMillis(1500)

        // list pushNotification
        assertPushChannels(chat, 3)

        // remove 1 channel
        chat.unregisterPushChannels(listOf(channel03)).await()
        delayInMillis(1500)

        // list pushNotification
        assertPushChannels(chat, 2)

        // removeAll
        chat.unregisterAllPushChannels().await()
        delayInMillis(1500)

        // list pushNotification
        assertPushChannels(chat, 0)
    }

    @Test
    fun can_getEventsHistory() = runTest {
        // given
        val channelId01 = channel01.id
        val userId = someUser.id
        val userName: String = someUser.name ?: ""
        val count = 2
        channel01.invite(someUser).await()
        channel01.sendText(
            text = "message01In$channelId01",
            mentionedUsers = mapOf(1 to MessageMentionedUser(userId, userName))
        ).await()
        channel01.sendText("message02In$channelId01").await()
        channel01.sendText("message03In$channelId01").await()

        // when
        delayForHistory()
        val eventsForUser: GetEventsHistoryResult = chat.getEventsHistory(channelId = userId, count = count).await()
        val messageEvents = chat.getEventsHistory(channelId = channelId01, count = count).await()

        // then
        assertNotNull(eventsForUser.events.find { it.payload is EventContent.Invite })
        assertNotNull(eventsForUser.events.find { it.payload is EventContent.Mention })
        assertNull(messageEvents.events.find { it.payload !is EventContent.TextMessageContent })
        assertEquals(2, messageEvents.events.size)
        assertEquals(channelId01, messageEvents.events.first().channelId)
        assertEquals(channelId01, messageEvents.events.last().channelId)
        assertEquals(messageEvents.events.first().userId, messageEvents.events.last().userId)
        assertTrue(messageEvents.isMore)

        // remove messages
        chat.pubNub.deleteMessages(listOf(channelId01)).await()
    }

    @Test
    fun can_getCurrentUserMentions_userWasMentionedInChannel() = runTest {
        val channelId01 = channel01.id
        val userId = someUser.id
        val message = "myMessage_${randomString()}"
        val messageMentionedUser = MessageMentionedUser(id = userId, name = someUser.name ?: "userName")
        val messageMentionedUsers: MessageMentionedUsers = mapOf(1 to messageMentionedUser)

        // send messages with user mentions
        channel01.sendText(text = message, mentionedUsers = messageMentionedUsers).await()
        delayForHistory()

        // when
        val currentUserMentionsResult: GetCurrentUserMentionsResult = chat.getCurrentUserMentions().await()

        // then
        val userMentionData = currentUserMentionsResult.enhancedMentionsData.first() as ChannelMentionData
        assertFalse(currentUserMentionsResult.isMore)
        assertEquals(1, currentUserMentionsResult.enhancedMentionsData.size)
        assertEquals(userId, userMentionData.userId)
        assertEquals(channelId01, userMentionData.channelId)
        assertEquals(message, userMentionData.message?.content?.text)

        // remove messages
        chat.pubNub.deleteMessages(listOf(channelId01, userId))
    }

    @Test
    fun can_getCurrentUserMentions_userWasMentionedInThreadChannel() = runTest {
        val channelId01 = channel01.id
        val userId = someUser.id
        val message = "myMessage_${randomString()}"
        val messageMentionedUser = MessageMentionedUser(id = userId, name = someUser.name ?: "userName")
        val messageMentionedUsers: MessageMentionedUsers = mapOf(1 to messageMentionedUser)

        // send messages with user mentions
        threadChannel.sendText(text = message, mentionedUsers = messageMentionedUsers).await()
        delayForHistory()
        // when
        val currentUserMentionsResult: GetCurrentUserMentionsResult = chat.getCurrentUserMentions().await()

        // then
        assertFalse(currentUserMentionsResult.isMore)
        assertEquals(1, currentUserMentionsResult.enhancedMentionsData.size)
        val userMentionData = currentUserMentionsResult.enhancedMentionsData.first() as ThreadMentionData
        assertEquals(userId, userMentionData.userId)
        assertEquals(true, userMentionData.parentChannelId.contains(CHANNEL_ID_OF_PARENT_MESSAGE_PREFIX))
        assertEquals(true, userMentionData.threadChannelId.contains(THREAD_CHANNEL_ID_PREFIX))
        assertEquals(message, userMentionData.message?.content?.text)

        // remove messages
        chat.pubNub.deleteMessages(listOf(channelId01, userId))
    }

    @Test
    fun emitEvent_with_custom() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val event = CompletableDeferred<Event<EventContent.Custom>>()
        var tt: Long = 0
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel.id)) {
                unsubscribe = chat.listenForEvents<EventContent.Custom>(channel.id) {
                    event.complete(it)
                }
            }
            tt = chat.emitEvent(channel.id, EventContent.Custom(mapOf("abc" to "def"), EmitEventMethod.PUBLISH))
                .await().timetoken
            assertEquals(mapOf("abc" to "def"), event.await().payload.data)
            assertFalse(channel.getMembers().await().members.any { it.user.id == chat.currentUser.id })
            unsubscribe?.close()
        }
        delayForHistory()
        val eventFromHistory = chat.getEventsHistory(channel.id, tt + 1, tt).await().events.first()
        require(eventFromHistory.payload is EventContent.Custom)
        assertEquals(mapOf("abc" to "def"), (eventFromHistory.payload as EventContent.Custom).data)
    }

    @Test
    fun emitEvent_with_custom_signal() = runTest {
        val channel = chat.createChannel(randomString()).await()
        val event = CompletableDeferred<Event<EventContent.Custom>>()
        var tt: Long = 0
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var unsubscribe: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(channel.id)) {
                unsubscribe = chat.listenForEvents<EventContent.Custom>(channel.id, EmitEventMethod.SIGNAL) {
                    event.complete(it)
                }
            }
            tt = chat.emitEvent(channel.id, EventContent.Custom(mapOf("abc" to "def"), EmitEventMethod.SIGNAL))
                .await().timetoken
            assertEquals(mapOf("abc" to "def"), event.await().payload.data)
            assertFalse(channel.getMembers().await().members.any { it.user.id == chat.currentUser.id })
            unsubscribe?.close()
        }
    }

    @Test
    fun canEmitEventCustomEvent() = runTest {
        val element1 = "element1"
        val element1Value = "element1Value"
        val additionalElement = "additionalElement"
        val additionalElementValue = "element2Value"
        var tt: Long = 0
        val channelId = channel01.id
        val customPayloads = EventContent.Custom(
            data = mapOf(
                element1 to element1Value,
            )
        )

        // mergePayloadWith is not needed in case of EventContent.Custom but come in handy e.g. for EventContent.TextMessageContent
        val mergePayloadWith: Map<String, Any> = mapOf(additionalElement to additionalElementValue)

        chat.emitEvent(channelId = channelId, payload = customPayloads, mergePayloadWith = mergePayloadWith)
            .async { result: Result<PNPublishResult> ->
                result.onSuccess { pnPublishResult: PNPublishResult ->
                    tt = pnPublishResult.timetoken
                }.onFailure { ex: PubNubException ->
                    throw IllegalStateException(ex)
                }
            }

        delayForHistory()
        delayForHistory()
        val eventFromHistory = chat.getEventsHistory(channelId, tt + 1, tt).await().events.first()
        val payload: EventContent.Custom = eventFromHistory.payload as EventContent.Custom
        val customEventData = payload.data

        assertEquals(2, customEventData.size)
        assertEquals(element1Value, customEventData[element1])
        assertEquals(additionalElementValue, customEventData[additionalElement])
    }

    @Test
    fun destroy_completes_successfully() {
        chat.getChannel("abc").async {}
        channel01.streamUpdates { }
        chat.destroy()
    }

    @Test
    fun setRestrictionThenUnset() = runTest(timeout = 10.seconds) {
        if (PLATFORM == "iOS") {
            return@runTest
        }
        val userId = someUser.id
        val channelId = channel01.id
        val banned = CompletableDeferred<Unit>()
        val unbanned = CompletableDeferred<Unit>()
        val restrictionBan = Restriction(userId = userId, channelId = channelId, ban = true, reason = "rude")
        val restrictionUnban =
            Restriction(userId = userId, channelId = channelId, ban = false, mute = false, reason = "ok")
        pubnubPamServer.test(backgroundScope, checkAllEvents = false) {
            var removeListenerAndUnsubscribe: AutoCloseable? = null
            pubnubPamServer.awaitSubscribe(channels = listOf(INTERNAL_USER_MODERATION_CHANNEL_PREFIX + userId)) {
                removeListenerAndUnsubscribe = chatPamServer.listenForEvents(
                    type = EventContent.Moderation::class,
                    channelId = INTERNAL_USER_MODERATION_CHANNEL_PREFIX + userId
                ) { event: Event<EventContent.Moderation> ->
                    val restrictionType: RestrictionType = event.payload.restriction
                    if (restrictionType == RestrictionType.BAN) {
                        banned.complete(Unit)
                    } else {
                        unbanned.complete(Unit)
                    }
                }
            }

            chatPamServer.setRestrictions(restrictionBan).await()
            banned.await()
            chatPamServer.setRestrictions(restrictionUnban).await()
            unbanned.await()

            removeListenerAndUnsubscribe?.close()
        }
    }

    @Test
    fun getMessageFromReport() = runTest {
        val messageText = "Test message to be reported"
        val modId = generateRandomUuid()
        val reportChannel = INTERNAL_MODERATION_PREFIX + channel01.id

        val report = EventContent.Report(
            text = messageText,
            reason = "auto moderated",
            reportedMessageTimetoken = null,
            reportedMessageChannelId = channel01.id,
            reportedUserId = null,
            autoModerationId = modId
        )
        chat.emitEvent(reportChannel, report).await()
        channel01.sendText(messageText, meta = mapOf("pn_mod_id" to modId)).await()
        delayForHistory()
        val history = channel01.getHistory(count = 1).await()
        val reportedMessage = history.messages[0]
        val reportEvents = chat.getEventsHistory(reportChannel, count = 1).await()

        @Suppress("UNCHECKED_CAST")
        val reportEvent = reportEvents.events[0] as Event<EventContent.Report>

        val message = (chat as ChatInternal).getMessageFromReport(reportEvent).await()

        assertEquals(reportedMessage.timetoken, message?.timetoken)
    }

    @Test
    fun findMessageBetween() = runTest {
        val tts = (0..20).map {
            channel01.sendText("$it", ttl = 1).await()
        }
        val message = (chat as ChatImpl).findMessageBetween(
            channel01,
            tts.maxOf {
                it.timetoken
            },
            end = tts.minOf { it.timetoken },
            lastStart = Long.MAX_VALUE,
            iterationNumber = 0,
            countPerRequest = 3
        ) {
            it.text == "11"
        }.await()
        assertNotNull(message)
        assertEquals("11", message.text)
    }

    @Test
    fun createDirectConversation_withDuplicateUsers_shouldReturnExisting() = runTest {
        // given - initialize and create first direct conversation
        chat.initialize().await()
        val firstResult = chat.createDirectConversation(someUser).await()
        val firstChannelId = firstResult.channel.id

        // when - create direct conversation again with same user
        val secondResult = chat.createDirectConversation(someUser).await()
        val secondChannelId = secondResult.channel.id

        // then - should return the same channel ID (idempotent operation)
        assertEquals(firstChannelId, secondChannelId, "Creating direct conversation twice should return same channel")

        // verify memberships are consistent
        assertEquals(
            chat.currentUser.asImpl().copy(updated = null, lastActiveTimestamp = null),
            secondResult.hostMembership.user.asImpl().copy(updated = null, lastActiveTimestamp = null)
        )
        assertEquals(someUser, secondResult.inviteeMembership.user.asImpl())
        assertEquals(secondResult.channel, secondResult.hostMembership.channel)
        assertEquals(secondResult.channel, secondResult.inviteeMembership.channel)

        // verify no duplicate channels created - fetch channel directly
        val fetchedChannel = chat.getChannel(firstChannelId).await()
        assertNotNull(fetchedChannel, "Original channel should still exist")
        assertEquals(firstChannelId, fetchedChannel.id)
    }

    @Test
    fun isPresent_withStateTransitions_shouldReflectCorrectState() = runTest {
        // given - create a test channel
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()

        // initially, current user should not be present (not subscribed)
        val initiallyPresent = chat.isPresent(chat.currentUser.id, testChannelId).await()
        assertFalse(initiallyPresent, "User should not be present before subscribing")

        // when - user subscribes to the channel
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var subscription: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(testChannelId)) {
                subscription = testChannel.connect { }
            }

            // wait for presence to update
            delayInMillis(2000)

            // then - user should be present
            val nowPresent = chat.isPresent(chat.currentUser.id, testChannelId).await()
            assertTrue(nowPresent, "User should be present after subscribing")

            // when - user unsubscribes
            subscription?.close()

            // wait for presence to update
            delayInMillis(2000)

            // then - user should not be present anymore
            val afterUnsubscribe = chat.isPresent(chat.currentUser.id, testChannelId).await()
            assertFalse(afterUnsubscribe, "User should not be present after unsubscribing")
        }
    }

    @Test
    fun whoIsPresent_withMultipleUsers_shouldReturnAllPresent() = runTest {
        // given - create a test channel
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()

        // when - multiple users subscribe (simulate with current user)
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var subscription: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(testChannelId)) {
                subscription = testChannel.connect { }
            }

            // wait for presence to settle
            delayInMillis(2000)

            // then - whoIsPresent should return current user
            val presentUsers = testChannel.whoIsPresent().await()
            assertTrue(presentUsers.isNotEmpty(), "Should have at least one present user")
            assertContains(presentUsers, chat.currentUser.id, "Current user should be in present list")

            // verify multiple calls return consistent results
            val presentUsers2 = testChannel.whoIsPresent().await()
            assertEquals(presentUsers.size, presentUsers2.size, "Multiple calls should return same count")
            assertTrue(presentUsers2.contains(chat.currentUser.id), "Should consistently show current user")

            subscription?.close()
        }
    }

    @Test
    fun getEventsHistory_withPagination_shouldReturnCorrectPages() = runTest {
        // given - create multiple events on a channel
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()
        val eventCount = 10
        val timetokens = mutableListOf<Long>()

        // publish multiple messages to create events
        repeat(eventCount) { index ->
            val publishResult = testChannel.sendText("Message $index").await()
            timetokens.add(publishResult.timetoken)
        }

        delayForHistory()

        // when - fetch first page with count=3
        val firstPage = chat.getEventsHistory(
            channelId = testChannelId,
            count = 3
        ).await()

        // then - should get 3 most recent events
        assertEquals(3, firstPage.events.size, "First page should have 3 events")
        assertTrue(firstPage.isMore, "Should indicate more events available")

        // verify events are in reverse chronological order (newest first)
        assertTrue(
            firstPage.events[0].timetoken > firstPage.events[1].timetoken,
            "Events should be ordered newest first"
        )
        assertTrue(
            firstPage.events[1].timetoken > firstPage.events[2].timetoken,
            "Events should be ordered newest first"
        )

        // when - fetch next page using endTimetoken from last event
        val secondPage = chat.getEventsHistory(
            channelId = testChannelId,
            endTimetoken = firstPage.events.last().timetoken,
            count = 3
        ).await()

        // then - should get next 3 older events
        assertEquals(3, secondPage.events.size, "Second page should have 3 events")
        assertTrue(secondPage.isMore, "Should still have more events")

        // verify pagination doesn't return duplicates
        val firstPageTimetokens = firstPage.events.map { it.timetoken }.toSet()
        val secondPageTimetokens = secondPage.events.map { it.timetoken }.toSet()
        assertTrue(
            firstPageTimetokens.intersect(secondPageTimetokens).isEmpty(),
            "Pages should not have duplicate events"
        )

        // when - fetch with large count to get all remaining
        val allRemaining = chat.getEventsHistory(
            channelId = testChannelId,
            count = 100
        ).await()

        // then - should get all events
        assertTrue(allRemaining.events.size >= eventCount, "Should get all $eventCount events")

        // cleanup
        chat.pubNub.deleteMessages(listOf(testChannelId)).await()
    }

    @Test
    fun createDirectConversation_withDuplicateUsers_shouldReturnExisting() = runTest {
        // given - initialize and create first direct conversation
        chat.initialize().await()
        val firstResult = chat.createDirectConversation(someUser).await()
        val firstChannelId = firstResult.channel.id

        // when - create direct conversation again with same user
        val secondResult = chat.createDirectConversation(someUser).await()
        val secondChannelId = secondResult.channel.id

        // then - should return the same channel ID (idempotent operation)
        assertEquals(firstChannelId, secondChannelId, "Creating direct conversation twice should return same channel")

        // verify memberships are consistent
        assertEquals(
            chat.currentUser.asImpl().copy(updated = null, lastActiveTimestamp = null),
            secondResult.hostMembership.user.asImpl().copy(updated = null, lastActiveTimestamp = null)
        )
        assertEquals(someUser, secondResult.inviteeMembership.user.asImpl())
        assertEquals(secondResult.channel, secondResult.hostMembership.channel)
        assertEquals(secondResult.channel, secondResult.inviteeMembership.channel)

        // verify no duplicate channels created - fetch channel directly
        val fetchedChannel = chat.getChannel(firstChannelId).await()
        assertNotNull(fetchedChannel, "Original channel should still exist")
        assertEquals(firstChannelId, fetchedChannel.id)
    }

    @Test
    fun isPresent_withStateTransitions_shouldReflectCorrectState() = runTest {
        // given - create a test channel
        val testChannelId = randomString()
        val testChannel = chat.createChannel(testChannelId).await()

        // initially, current user should not be present (not subscribed)
        val initiallyPresent = chat.isPresent(chat.currentUser.id, testChannelId).await()
        assertFalse(initiallyPresent, "User should not be present before subscribing")

        // when - user subscribes to the channel
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var subscription: AutoCloseable? = null
            pubnub.awaitSubscribe(listOf(testChannelId)) {
                subscription = testChannel.connect { }
            }

            // wait for presence to update
            delayInMillis(2000)

            // then - user should be present
            val nowPresent = chat.isPresent(chat.currentUser.id, testChannelId).await()
            assertTrue(nowPresent, "User should be present after subscribing")

            // when - user unsubscribes
            subscription?.close()

            // wait for presence to update
            delayInMillis(2000)

            // then - user should not be present anymore
            val afterUnsubscribe = chat.isPresent(chat.currentUser.id, testChannelId).await()
            assertFalse(afterUnsubscribe, "User should not be present after unsubscribing")
        }
    }

    private suspend fun assertPushChannels(chat: Chat, expectedNumberOfChannels: Int) {
        val pushChannels = chat.getPushChannels().await()
        assertEquals(expectedNumberOfChannels, pushChannels.size)
    }

    @Test
    fun wherePresent_whenUserNotSubscribed_shouldReturnEmptyList() = runTest {
        // given - a user not subscribed to any channels
        val channels = chat.wherePresent(chat.currentUser.id).await()

        // then - should return empty list
        assertTrue(channels.isEmpty(), "User not subscribed to any channel should have empty wherePresent result")
    }

    @Test
    fun wherePresent_whenUserSubscribedToMultipleChannels_shouldReturnAllChannelIds() = runTest {
        // given - create multiple test channels
        val testChannelId1 = randomString()
        val testChannelId2 = randomString()
        val testChannel1 = chat.createChannel(testChannelId1).await()
        val testChannel2 = chat.createChannel(testChannelId2).await()

        // when - user subscribes to both channels
        pubnub.test(backgroundScope, checkAllEvents = false) {
            var subscription1: AutoCloseable? = null
            var subscription2: AutoCloseable? = null

            // Subscribe to both channels in a single awaitSubscribe call
            // JS SDK handles sequential subscribes differently than JVM
            pubnub.awaitSubscribe(listOf(testChannelId1, testChannelId2)) {
                subscription1 = testChannel1.connect { }
                subscription2 = testChannel2.connect { }
            }

            // wait for presence to update
            delayInMillis(2000)

            // then - wherePresent should return both channels
            val channels = chat.wherePresent(chat.currentUser.id).await()
            assertTrue(channels.size >= 2, "Should have at least two channels")
            assertTrue(channels.contains(testChannelId1), "Should contain the first subscribed channel")
            assertTrue(channels.contains(testChannelId2), "Should contain the second subscribed channel")

            subscription1?.close()
            subscription2?.close()
        }
    }
}

fun User.asImpl() = this as UserImpl
