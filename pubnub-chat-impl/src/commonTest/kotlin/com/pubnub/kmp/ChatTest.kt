package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.UserId
import com.pubnub.api.endpoints.FetchMessages
import com.pubnub.api.endpoints.MessageCounts
import com.pubnub.api.endpoints.objects.channel.GetAllChannelMetadata
import com.pubnub.api.endpoints.objects.channel.GetChannelMetadata
import com.pubnub.api.endpoints.objects.channel.RemoveChannelMetadata
import com.pubnub.api.endpoints.objects.channel.SetChannelMetadata
import com.pubnub.api.endpoints.objects.member.ManageChannelMembers
import com.pubnub.api.endpoints.objects.membership.GetMemberships
import com.pubnub.api.endpoints.objects.uuid.GetAllUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.GetUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.RemoveUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.SetUUIDMetadata
import com.pubnub.api.endpoints.presence.HereNow
import com.pubnub.api.endpoints.presence.WhereNow
import com.pubnub.api.endpoints.pubsub.Publish
import com.pubnub.api.endpoints.pubsub.Signal
import com.pubnub.api.endpoints.push.ListPushProvisions
import com.pubnub.api.endpoints.push.RemoveAllPushChannelsForDevice
import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.models.consumer.history.PNMessageCountResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataArrayResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.member.MemberInput
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNHereNowChannelData
import com.pubnub.api.models.consumer.presence.PNHereNowOccupantData
import com.pubnub.api.models.consumer.presence.PNHereNowResult
import com.pubnub.api.models.consumer.presence.PNWhereNowResult
import com.pubnub.api.models.consumer.push.PNPushListProvisionsResult
import com.pubnub.api.models.consumer.push.PNPushRemoveAllChannelsResult
import com.pubnub.api.utils.PatchValue
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.createPNConfiguration
import com.pubnub.chat.Channel
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.PushNotificationsConfig
import com.pubnub.chat.internal.ChatImpl
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.internal.timer.TimerManager
import com.pubnub.chat.message.GetUnreadMessagesCounts
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.user.GetUsersResponse
import com.pubnub.kmp.utils.BaseTest
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.Capture
import dev.mokkery.matcher.capture.SlotCapture
import dev.mokkery.matcher.capture.capture
import dev.mokkery.matcher.capture.get
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ChatTest : BaseTest() {
    private lateinit var objectUnderTest: ChatImpl
    private val chatMock: ChatInternal = mock(MockMode.strict)
    private val pubnub: PubNub = mock(MockMode.strict)
    private val timerManager: TimerManager = mock(MockMode.strict)
    private lateinit var pnConfiguration: PNConfiguration
    private lateinit var chatConfig: ChatConfiguration
    private val setUUIDMetadataEndpoint: SetUUIDMetadata = mock(MockMode.strict)
    private val setChannelMetadataEndpoint: SetChannelMetadata = mock(MockMode.strict)
    private val getUUIDMetadataEndpoint: GetUUIDMetadata = mock(MockMode.strict)
    private val getAllUUIDMetadataEndpoint: GetAllUUIDMetadata = mock(MockMode.strict)
    private val getAllChannelMetadataEndpoint: GetAllChannelMetadata = mock(MockMode.strict)
    private val getChannelMetadataEndpoint: GetChannelMetadata = mock(MockMode.strict)
    private val removeUUIDMetadataEndpoint: RemoveUUIDMetadata = mock(MockMode.strict)
    private val removeChannelMetadataEndpoint: RemoveChannelMetadata = mock(MockMode.strict)
    private val publishEndpoint: Publish = mock(MockMode.strict)
    private val signalEndpoint: Signal = mock(MockMode.strict)
    private val name = "testName"
    private val externalId = "testExternalId"
    private val profileUrl = "testProfileUrl"
    private val email = "testEmail"
    private val customData = mapOf("testCustom" to "custom")
    private val custom = createCustomObject(customData)
    private val status = "testStatus"
    private val typeAsString = "direct"
    private val updated = "timeStamp"
    private val callback: (Result<User>) -> Unit = { }
    private val userId = "myUserId"
    private val subscribeKey = "mySubscribeKey"
    private val publishKey = "myPublishKey"
    private val description = "testDescription"
    private val channelId = "myChannelId"
    private val manageChannelMembersEndpoint: ManageChannelMembers = mock(mode = MockMode.strict)
    private val timetoken: Long = 123457
    private val pnException404 = PubNubException(statusCode = 404, errorMessage = "Requested object was not found.")
    private val getMembershipsEndpoint: GetMemberships = mock(MockMode.strict)
    private val listPushProvisions: ListPushProvisions = mock(MockMode.strict)
    private val removeAllPushChannelsForDevice: RemoveAllPushChannelsForDevice = mock(MockMode.strict)
    private val fetchMessages: FetchMessages = mock(MockMode.strict)
    private val messageCounts: MessageCounts = mock(MockMode.strict)

    @BeforeTest
    fun setUp() {
        pnConfiguration = createPNConfiguration(UserId(userId), subscribeKey, publishKey)
        every { pubnub.configuration } returns pnConfiguration
        chatConfig = ChatConfiguration(
            typingTimeout = 2000.milliseconds
        )
        objectUnderTest = ChatImpl(chatConfig, pubnub, timerManager = timerManager)
    }

    @Test
    fun createUserShouldResultSuccessWhenUserDoesNotExist() {
        // given
        val pnUuidMetadataResult = getPNUuidMetadataResult()
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }
        every {
            pubnub.setUUIDMetadata(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns setUUIDMetadataEndpoint
        every { setUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(pnUuidMetadataResult))
        }

        // when
        objectUnderTest.createUser(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = typeAsString
        ).async { result: Result<User> ->
            assertTrue(result.isSuccess)
            result.onSuccess {
                assertEquals(id, it.id)
                assertEquals(name, it.name)
                assertEquals(externalId, it.externalId)
                assertEquals(profileUrl, it.profileUrl)
                assertEquals(email, it.email)
                assertEquals(status, it.status)
                assertEquals(typeAsString, it.type)
            }
        }
    }

    @Test
    fun createUserShouldResultFailureWhenUserExists() {
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(getPNUuidMetadataResult()))
        }

        // when
        objectUnderTest.createUser(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = typeAsString
        ).async { result: Result<User> ->
            assertTrue(result.isFailure)
            assertEquals("User with this ID already exists", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun whenCreatingUserWithInvalidIdExceptionShouldBeReturned() {
        val invalidId = ""
        objectUnderTest.createUser(id = invalidId).async { result ->
            assertTrue(result.isFailure)
            assertEquals("Id is required", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun canUpdateUser() {
        // given
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } returns Unit

        // when
        objectUnderTest.updateUser(
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = typeAsString
        ).async {
        }

        // then
        verify { pubnub.getUUIDMetadata(uuid = id, includeCustom = true) }
    }

    @Test
    fun canHardDeleteUser() {
        // given
        val pnUuidMetadataResult = getPNUuidMetadataResult()
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls
            { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
                callback1.accept(Result.success(pnUuidMetadataResult))
            }
        every { pubnub.removeUUIDMetadata(any()) } returns removeUUIDMetadataEndpoint
        every { removeUUIDMetadataEndpoint.async(any()) } returns Unit
        val softDeleteFalse = false

        // when
        objectUnderTest.deleteUser(id, softDeleteFalse).async {}

        // then
        verify { pubnub.removeUUIDMetadata(uuid = id) }
    }

    @Test
    fun canSoftDeleteUser() {
        // given
        every {
            pubnub.setUUIDMetadata(
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
        } returns setUUIDMetadataEndpoint
        every { setUUIDMetadataEndpoint.async(any()) } returns Unit

        val pnUuidMetadataResult = getPNUuidMetadataResult()
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(pnUuidMetadataResult))
        }
        val softDelete = true
        val status = "deleted"
        val includeCustomFalse = false

        // when
        objectUnderTest.deleteUser(id, softDelete).async {}

        // then
        verify {
            pubnub.setUUIDMetadata(
                id,
                name,
                externalId,
                profileUrl,
                email,
                any(),
                includeCustomFalse,
                typeAsString,
                status
            )
        }
    }

    @Test
    fun whenIdIsEmptyThenDeleteShouldResultsFailure() {
        // given
        val emptyID = ""
        val softDelete = true

        // when
        objectUnderTest.deleteUser(emptyID, softDelete).async { result: Result<User?> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Id is required", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun when_wherePresent_executedCallbackResultShouldContainListOfChannels() {
        // given
        val whereNowEndpoint: WhereNow = mock(MockMode.strict)
        val channel01 = "myChannel1"
        val channel02 = "myChannel02"
        val pnWhereNowResult = PNWhereNowResult(listOf(channel01, channel02))
        every { pubnub.whereNow(any()) } returns whereNowEndpoint
        every { whereNowEndpoint.async(any()) } calls { (callback: Consumer<Result<PNWhereNowResult>>) ->
            callback.accept(Result.success(pnWhereNowResult))
        }

        // when
        objectUnderTest.wherePresent(id).async { result: Result<List<String>> ->
            // then
            assertTrue(result.isSuccess)
            assertFalse(result.getOrNull()!!.isEmpty())
            assertTrue(result.getOrNull()!!.contains(channel01))
            assertTrue(result.getOrNull()!!.contains(channel02))
        }
    }

    @Test
    fun when_isPresent_executedCallbackResultShouldContainsAnswer() {
        // given
        val whereNowEndpoint: WhereNow = mock(MockMode.strict)
        val channel01 = "myChannel1"
        val channel02 = "myChannel02"
        val channelId = "myChannel1"
        val pnWhereNowResult: PNWhereNowResult = PNWhereNowResult(listOf(channel01, channel02))
        every { pubnub.whereNow(any()) } returns whereNowEndpoint
        every { whereNowEndpoint.async(any()) } calls { (callback: Consumer<Result<PNWhereNowResult>>) ->
            callback.accept(Result.success(pnWhereNowResult))
        }

        // when
        objectUnderTest.isPresent(id, channelId).async { result: Result<Boolean> ->
            // then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!)
        }
    }

    @Test
    fun whenChannelIdIsEmptyThen_isPresent_shouldResultsFailure() {
        // given
        val emptyChannelId = ""

        // when
        objectUnderTest.isPresent(id, emptyChannelId).async { result: Result<Boolean> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Channel Id is required", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun when_whoIsPresent_executedCallbackResultShouldContainsAnswer() {
        // given
        val hereNowEndpoint: HereNow = mock(MockMode.strict)
        val channel01 = "myChannel1"
        val channelId = "myChannel1"
        val user01 = "user1"
        val user02 = "user2"
        val pnHereNowResult = PNHereNowResult(
            1,
            2,
            mutableMapOf(
                channel01 to PNHereNowChannelData(
                    channel01, 2, listOf(PNHereNowOccupantData(user01), PNHereNowOccupantData(user02))
                )
            )
        )
        every { pubnub.hereNow(any()) } returns hereNowEndpoint
        every { hereNowEndpoint.async(any()) } calls { (callback: Consumer<Result<PNHereNowResult>>) ->
            callback.accept(Result.success(pnHereNowResult))
        }

        // when
        objectUnderTest.whoIsPresent(channelId).async { result: Result<Collection<String>> ->
            // then
            assertTrue(result.isSuccess)
            result.onSuccess {
                assertTrue { it.contains(user01) }
                assertTrue { it.contains(user02) }
            }
        }
    }

    @Test
    fun whenChannelIdIsEmptyThen_whoIsPresent_shouldResultsFailure() {
        // given
        val emptyChannelId = ""

        // when
        objectUnderTest.whoIsPresent(emptyChannelId).async { result: Result<Collection<String>> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Channel Id is required", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun whenChannelIdIsEmptyResultShouldContainException() {
        // given
        val channelId = ""

        // when
        objectUnderTest.updateChannel(id = channelId).async { result: Result<Channel> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Channel Id is required", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun shouldResultErrorWhenSetChannelMetadataResultError() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(getPNChannelMetadataResult()))
        }
        every {
            pubnub.setChannelMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(Exception("Error calling setChannelMetadata")))
        }

        // when
        objectUnderTest.updateChannel(id = id).async { result: Result<Channel> ->
            // then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message!!.contains("Failed to create/update channel data"))
        }
    }

    @Test
    fun shouldResultErrorWhenUpdatingChannelThatDoesNotExist() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }
        every {
            pubnub.setChannelMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(Exception("Error calling setChannelMetadata")))
        }

        // when
        objectUnderTest.updateChannel(id = id).async { result: Result<Channel> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Channel not found", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun shouldResultSuccessWhenSetChannelMetadataResultSuccess() {
        val updatedName = "updatedName"
        val updatedDescription = "updatedDescription"
        val updatedCustom = mapOf("cos" to "cos1")
        val updatedUpdated = "updatedUpdated"
        val updatedType = ChannelType.GROUP.stringValue
        val updatedStatus = "updatedStatus"

        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(getPNChannelMetadataResult()))
        }
        every {
            pubnub.setChannelMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(
                Result.success(
                    getPNChannelMetadataResult(
                        updatedName = updatedName,
                        updatedDescription = updatedDescription,
                        updatedCustom = updatedCustom,
                        updatedUpdated = updatedUpdated,
                        updatedType = updatedType,
                        updatedStatus = updatedStatus
                    )
                )
            )
        }

        // when
        objectUnderTest.updateChannel(id = id, name = name, description = description)
            .async { result: Result<Channel> ->
                // then
                assertTrue(result.isSuccess)
                result.onSuccess {
                    assertEquals(id, it.id)
                    assertEquals(updatedName, it.name)
                    assertEquals(updatedDescription, it.description)
                    assertEquals(updatedCustom, it.custom)
                    assertEquals(updatedUpdated, it.updated)
                    assertEquals(ChannelType.from(updatedType), it.type)
                    assertEquals(updatedStatus, it.status)
                }
            }
    }

    @Test
    fun canHardDeleteChannel() {
        every { pubnub.getChannelMetadata(any(), any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(
                Result.success(
                    getPNChannelMetadataResult(
                        id,
                        name,
                        description,
                        customData,
                        updated,
                        typeAsString,
                        status
                    )
                )
            )
        }
        every { pubnub.removeChannelMetadata(any()) } returns removeChannelMetadataEndpoint
        every { removeChannelMetadataEndpoint.async(any()) } returns Unit

        objectUnderTest.deleteChannel(id = id, soft = false).async {}

        verify { pubnub.removeChannelMetadata(channel = id) }
    }

    @Test
    fun canSoftDeleteChannel() {
        // given
        every {
            pubnub.setChannelMetadata(any(), any(), any(), any(), any(), any(), any())
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } returns Unit
        val pnChannelMetadataResult: PNChannelMetadataResult =
            getPNChannelMetadataResult(id, name, description, customData, updated, typeAsString, status)
        every { pubnub.getChannelMetadata(any(), any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(pnChannelMetadataResult))
        }
        val softDelete = true
        val status = "deleted"
        val includeCustomFalse = false

        // when
        objectUnderTest.deleteChannel(id, softDelete).async {}

        // then
        verify {
            pubnub.setChannelMetadata(
                channel = id,
                name = name,
                description = description,
                custom = any(),
                includeCustom = includeCustomFalse,
                type = typeAsString.lowercase(),
                status = status
            )
        }
    }

    @Test
    fun whenForwardedChannelIdIsEqualOriginalChannelIDShouldResultError() {
        // given
        val message = createMessage()

        // when
        objectUnderTest.forwardMessage(message, channelId).async { result: Result<PNPublishResult> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("You cannot forward the message to the same channel.", result.exceptionOrNull()!!.message)
        }
    }

    @Test
    fun forwardedMessageShouldContainOriginalPublisherLocatedInMeta() {
        val message = createMessage(channelId, userId)
        val forwardedChannelId = "forwardedChannelId"
        val metaSlot = Capture.slot<Any>()
        val forwardedChannelIdSlot: SlotCapture<String> = Capture.slot<String>()
        every {
            pubnub.publish(
                channel = capture(forwardedChannelIdSlot),
                message = any(),
                meta = capture(metaSlot),
                shouldStore = any(),
                usePost = any(),
                replicate = any(),
                ttl = any()
            )
        } returns publishEndpoint
        every { publishEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNPublishResult>>) ->
            callback1.accept(Result.success(PNPublishResult(timetoken)))
        }

        objectUnderTest.forwardMessage(message, forwardedChannelId).async { result: Result<PNPublishResult> ->
            assertTrue(result.isSuccess)
        }

        val actualMeta: Map<String, String> = metaSlot.get() as Map<String, String>
        val originalPublisher: String = actualMeta["originalPublisher"].toString()
        val originalChannelId: String = actualMeta["originalChannelId"].toString()
        val actualForwardedChannelId: String = forwardedChannelIdSlot.get().toString()
        assertEquals(channelId, originalChannelId)
        assertEquals(userId, originalPublisher)
        assertEquals(forwardedChannelId, actualForwardedChannelId)
    }

    @Test
    fun shouldCalSignalWhenEmitEventWithMethodSignal() {
        every { pubnub.signal(any(), any()) } returns signalEndpoint
        every { signalEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNPublishResult>>) ->
            callback1.accept(Result.success(PNPublishResult(timetoken)))
        }
        val payload = EventContent.Typing(true)

        objectUnderTest.emitEvent(
            channelId = channelId,
            payload = payload,
        ).async { result ->
            assertTrue(result.isSuccess)
            assertEquals(timetoken, result.getOrNull()?.timetoken)
        }

        verify { pubnub.signal(channel = channelId, message = mapOf("type" to "typing", "value" to true)) }
    }

    @Test
    fun shouldCalPublishWhenEmitEventWithMethodPublish() {
        every { pubnub.publish(any(), any(), any(), any(), any(), any(), any()) } returns publishEndpoint
        every { publishEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNPublishResult>>) ->
            callback1.accept(Result.success(PNPublishResult(timetoken)))
        }
        val payload = EventContent.TextMessageContent(text = "messageContent")

        objectUnderTest.emitEvent(
            channelId = channelId,
            payload = payload,
        ).async { result ->
            assertTrue(result.isSuccess)
            assertEquals(timetoken, result.getOrNull()?.timetoken)
        }

        verify {
            pubnub.publish(
                channel = channelId,
                message = mapOf("type" to "text", "text" to "messageContent", "files" to null)
            )
        }
    }

    @Test
    fun whenChannelIdIsEmptyThenGetChannelShouldResultFailure() {
        val emptyChannelId = ""
        objectUnderTest.getChannel(emptyChannelId).async { result ->
            assertTrue(result.isFailure)
            assertEquals("Channel Id is required", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun whenChannelNotFoundShouldReturnProperMessage() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }

        objectUnderTest.getChannel(channelId).async { result ->
            assertTrue(result.isSuccess)
            assertNull(result.getOrNull())
        }
    }

    @Test
    fun getChannelShouldResultSuccessWhenChannelExists() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(
                Result.success(
                    getPNChannelMetadataResult(
                        updatedName = name,
                        updatedDescription = description,
                        updatedCustom = customData,
                        updatedUpdated = updated,
                        updatedType = typeAsString,
                        updatedStatus = status
                    )
                )
            )
        }

        objectUnderTest.getChannel(channelId).async { result ->
            assertTrue(result.isSuccess)
            assertEquals(id, result.getOrNull()?.id)
            assertEquals(name, result.getOrNull()?.name)
            assertEquals(description, result.getOrNull()?.description)
            assertEquals(updated, result.getOrNull()?.updated)
            assertEquals(typeAsString, result.getOrNull()?.type.toString().lowercase())
            assertEquals(status, result.getOrNull()?.status)
        }
    }

    @Test
    fun createChannelShouldResultFailureWhenChannelExists() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(getPNChannelMetadataResult()))
        }

        objectUnderTest.createChannel(id = id, name = name).async { result ->
            assertTrue(result.isFailure)
            assertEquals("Channel with this ID already exists", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun createChannelShouldResultSuccessWhenChannelDoesNotExist() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }
        every {
            pubnub.setChannelMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(
                Result.success(
                    getPNChannelMetadataResult(
                        updatedName = name,
                        updatedDescription = description,
                        updatedCustom = customData,
                        updatedUpdated = updated,
                        updatedType = typeAsString,
                        updatedStatus = status
                    )
                )
            )
        }

        objectUnderTest.createChannel(id = id, name = name).async { result ->
            assertTrue(result.isSuccess)
            result.onSuccess {
                assertEquals(id, it.id)
                assertEquals(name, it.name)
                assertEquals(description, it.description)
                assertEquals(updated, it.updated)
                assertEquals(typeAsString, it.type.toString().lowercase())
                assertEquals(status, it.status)
            }
        }
    }

    @Test
    fun whenUserIdIsEmptyThenGetChannelShouldResultFailure() {
        val emptyUserId = ""

        objectUnderTest.getUser(emptyUserId).async { result: Result<User?> ->
            assertTrue(result.isFailure)
            assertEquals("Id is required", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun whenUserNotFoundShouldReturnProperMessage() {
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }

        objectUnderTest.getUser(userId).async { result: Result<User?> ->
            assertTrue(result.isSuccess)
            assertNull(result.getOrNull())
        }
    }

    @Test
    fun getUserShouldResultSuccessWhenUserExists() {
        val pnUuidMetadataResult = getPNUuidMetadataResult()

        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(pnUuidMetadataResult))
        }

        objectUnderTest.getUser(userId = userId).async { result: Result<User?> ->
            assertTrue(result.isSuccess)
            result.onSuccess {
                assertEquals(id, it?.id)
                assertEquals(name, it?.name)
                assertEquals(externalId, it?.externalId)
                assertEquals(profileUrl, it?.profileUrl)
                assertEquals(email, it?.email)
                assertEquals(updated, it?.updated)
                assertEquals(status, it?.status)
            }
        }
    }

    @Test
    fun getUsersShouldResultSuccessWhenUserExists() {
        val total = 1
        val pnUUIDMetadataList: Collection<PNUUIDMetadata> = listOf(getPNUuidMetadata())
        val pnUUIDMetadataArrayResult =
            PNUUIDMetadataArrayResult(status = 200, data = pnUUIDMetadataList, totalCount = total, null, null)
        every { pubnub.getAllUUIDMetadata(any(), any(), any(), any(), any(), any()) } returns getAllUUIDMetadataEndpoint
        every { getAllUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataArrayResult>>) ->
            callback1.accept(Result.success(pnUUIDMetadataArrayResult))
        }
        val filter = "name LIKE 'test*'"

        objectUnderTest.getUsers(filter = filter).async { result: Result<GetUsersResponse> ->
            assertTrue(result.isSuccess)
            assertEquals(total, result.getOrNull()?.total)
            val user: User = result.getOrNull()?.users?.first()!!
            assertEquals(id, user.id)
            assertEquals(name, user.name)
            assertEquals(externalId, user.externalId)
            assertEquals(profileUrl, user.profileUrl)
            assertEquals(email, user.email)
            assertEquals(updated, user.updated)
            assertEquals(status, user.status)
        }
    }

    @Test
    fun getUsersShouldResultFailureWhenUserCanNotBeRetrieved() {
        every { pubnub.getAllUUIDMetadata(any(), any(), any(), any(), any(), any()) } returns getAllUUIDMetadataEndpoint
        every { getAllUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataArrayResult>>) ->
            callback1.accept(Result.failure(Exception("Error calling getAllUUIDMetadata")))
        }
        objectUnderTest.getUsers().async { result ->
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message!!.contains("Failed to get users."))
        }
    }

    @Test
    fun getChannelsShouldResultSuccessWhenUserExists() {
        val totalCount = 1
        val pnChannelMetadataSet: Collection<PNChannelMetadata> = setOf(getPNChannelMetadata())
        val pnChannelMetadataArrayResult =
            PNChannelMetadataArrayResult(status = 200, data = pnChannelMetadataSet, totalCount = totalCount, null, null)
        every {
            pubnub.getAllChannelMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns getAllChannelMetadataEndpoint
        every { getAllChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataArrayResult>>) ->
            callback1.accept(Result.success(pnChannelMetadataArrayResult))
        }

        val filter = "description LIKE '*support*'"
        objectUnderTest.getChannels(filter = filter).async { result ->
            assertTrue(result.isSuccess)
            val channel: Channel = result.getOrNull()?.channels?.first()!!
            assertEquals(totalCount, result.getOrNull()?.total)
            assertTrue(result.isSuccess)
            assertEquals(id, channel.id)
            assertEquals(name, channel.name)
            assertEquals(description, channel.description)
            assertEquals(updated, channel.updated)
            assertEquals(typeAsString, channel.type.toString().lowercase())
            assertEquals(status, channel.status)
        }
    }

    @Test
    fun getChannelsShouldResultFailureWhenUserCanNotBeRetrieved() {
        every {
            pubnub.getAllChannelMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns getAllChannelMetadataEndpoint
        every { getAllChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataArrayResult>>) ->
            callback1.accept(Result.failure(Exception("Error calling getAllChannelMetadata")))
        }

        objectUnderTest.getChannels().async { result ->
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message!!.contains("Failed to get channels."))
        }
    }

    @Test
    fun getUnreadMessagesCountsShouldReturnEmptySetWhenUserHasNoMembership() {
        val resultWithEmptyData = PNChannelMembershipArrayResult(
            status = 200,
            data = emptyList(),
            totalCount = 0,
            next = null,
            prev = null,
        )
        every {
            pubnub.getMemberships(
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
        } returns getMembershipsEndpoint
        every { getMembershipsEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback.accept(Result.success(resultWithEmptyData))
        }

        objectUnderTest.getUnreadMessagesCounts().async { result: Result<Set<GetUnreadMessagesCounts>> ->
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.isEmpty()!!)
        }
    }

    @Test
    fun getPushChannelsShouldFailWhenDeviceTokenIsNull() {
        val exception: PubNubException = assertFailsWith<PubNubException> {
            objectUnderTest.getPushChannels()
        }

        assertEquals("Device Token has to be defined in Chat pushNotifications config.", exception.message)
    }

    @Test
    fun whenCallingGetPushChannelsShouldReturnListOfChannels() {
        val deviceId = "myDeviceId"
        val pushType = PNPushType.FCM
        val topic = "topic"
        val apnsEnvironment = PNPushEnvironment.PRODUCTION
        chatConfig = ChatConfiguration(
            pushNotifications = PushNotificationsConfig(
                sendPushes = false,
                deviceToken = deviceId,
                deviceGateway = pushType,
                apnsTopic = topic,
                apnsEnvironment = apnsEnvironment
            )
        )
        objectUnderTest = ChatImpl(chatConfig, pubnub)

        val channel01 = "channel1"
        val channel02 = "channel2"
        every { pubnub.auditPushChannelProvisions(any(), any(), any(), any()) } returns listPushProvisions
        every { listPushProvisions.async(any()) } calls { (callback: Consumer<Result<PNPushListProvisionsResult>>) ->
            callback.accept(Result.success(PNPushListProvisionsResult(channels = listOf(channel01, channel02))))
        }

        // when
        objectUnderTest.getPushChannels().async { result: Result<List<String>> ->
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()?.contains(channel01)!!)
            assertTrue(result.getOrNull()?.contains(channel02)!!)
        }

        verify {
            pubnub.auditPushChannelProvisions(
                pushType = pushType,
                deviceId = deviceId,
                topic = topic,
                environment = apnsEnvironment
            )
        }
    }

    @Test
    fun unregisterAllPushChannelsShouldFailWhenDeviceTokenIsNull() {
        val exception: PubNubException = assertFailsWith<PubNubException> {
            objectUnderTest.unregisterAllPushChannels()
        }

        assertEquals("Device Token has to be defined in Chat pushNotifications config.", exception.message)
    }

    @Test
    fun whenCallingUnregisterAllPushChannelsShouldPassProperDeviceId() {
        val deviceId = "myDeviceId"
        val pushType = PNPushType.FCM
        val topic = "topic"
        val apnsEnvironment = PNPushEnvironment.PRODUCTION
        chatConfig = ChatConfiguration(
            pushNotifications = PushNotificationsConfig(
                sendPushes = false,
                deviceToken = deviceId,
                deviceGateway = pushType,
                apnsTopic = topic,
                apnsEnvironment = apnsEnvironment
            )
        )
        objectUnderTest = ChatImpl(chatConfig, pubnub)
        every {
            pubnub.removeAllPushNotificationsFromDeviceWithPushToken(
                any(),
                any(),
                any(),
                any()
            )
        } returns removeAllPushChannelsForDevice
        every { removeAllPushChannelsForDevice.async(any()) } calls { (callback: Consumer<Result<PNPushRemoveAllChannelsResult>>) ->
            callback.accept(Result.success(PNPushRemoveAllChannelsResult()))
        }

        // when
        objectUnderTest.unregisterAllPushChannels().async { result: Result<Unit> ->
            assertTrue(result.isSuccess)
        }

        verify {
            pubnub.removeAllPushNotificationsFromDeviceWithPushToken(
                pushType = pushType,
                deviceId = deviceId,
                topic = topic,
                environment = apnsEnvironment
            )
        }
    }

    @Test
    fun getEventsHistoryShouldReturnIsMoreWhenCountIsProvided() {
        val message1 = "message text"
        val message2 = "second message"
        val user1 = "myUser"
        val user2 = "myUser2"
        val startTimetoken = 123L
        val endTimetoken = 456L
        val count = 2
        every { pubnub.fetchMessages(any(), any<PNBoundedPage>(), any(), any(), any(), any()) } returns fetchMessages
        every { fetchMessages.async(any()) } calls { (callback: Consumer<Result<PNFetchMessagesResult>>) ->
            callback.accept(
                Result.success(
                    createPnFetchMessagesResult(
                        channelId = channelId,
                        user1 = user1,
                        message1 = message1,
                        timetoken1 = startTimetoken,
                        user2 = user2,
                        message2 = message2,
                        timetoken2 = endTimetoken
                    )
                )
            )
        }

        objectUnderTest.getEventsHistory(
            channelId = channelId,
            startTimetoken = startTimetoken,
            endTimetoken = endTimetoken,
            count = count
        ).async { result: Result<GetEventsHistoryResult> ->
            assertTrue(result.isSuccess)
            result.getOrNull()?.let { assertTrue(it.isMore) }
            assertNotNull(result.getOrNull()?.events?.find { event -> event.userId == user1 })
            assertNotNull(result.getOrNull()?.events?.find { event -> event.userId == user2 })
        }

        verify {
            pubnub.fetchMessages(
                channels = listOf(channelId),
                page = PNBoundedPage(startTimetoken, endTimetoken, count),
                includeUUID = true,
                includeMeta = false,
                includeMessageActions = false,
                includeMessageType = true
            )
        }
    }

    @Test
    fun getEventsHistoryShouldReturnIsMoreFalseWhenCountIsNotProvided() {
        val message1 = "message text"
        val message2 = "second message"
        val user1 = "myUser"
        val user2 = "myUser2"
        val startTimetoken = 123L
        val endTimetoken = 456L
        every { pubnub.fetchMessages(any(), any<PNBoundedPage>(), any(), any(), any(), any()) } returns fetchMessages
        every { fetchMessages.async(any()) } calls { (callback: Consumer<Result<PNFetchMessagesResult>>) ->
            callback.accept(
                Result.success(
                    createPnFetchMessagesResult(
                        channelId = channelId,
                        user1 = user1,
                        message1 = message1,
                        timetoken1 = startTimetoken,
                        user2 = user2,
                        message2 = message2,
                        timetoken2 = endTimetoken
                    )
                )
            )
        }

        objectUnderTest.getEventsHistory(
            channelId = channelId,
            startTimetoken = startTimetoken,
            endTimetoken = endTimetoken,
        ).async { result: Result<GetEventsHistoryResult> ->
            assertTrue(result.isSuccess)
            result.getOrNull()?.let { assertFalse(it.isMore) }
            assertNotNull(result.getOrNull()?.events?.first()?.channelId == channelId)
            assertNotNull(result.getOrNull()?.events?.last()?.channelId == channelId)
        }
    }

    @Test
    fun whenCreatingPublicConversationWithChannelIdShouldUseIt() {
        val channelId = id
        //
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }
        every {
            pubnub.setChannelMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(
                Result.success(
                    getPNChannelMetadataResult(
                        updatedName = name,
                        updatedDescription = description,
                        updatedCustom = customData,
                        updatedUpdated = updated,
                        updatedType = ChannelType.PUBLIC.toString().lowercase(),
                        updatedStatus = status
                    )
                )
            )
        }

        // when
        objectUnderTest.createPublicConversation(channelId = channelId).async { result ->
            assertTrue(result.isSuccess)
            assertEquals(channelId, result.getOrNull()?.id)
            assertEquals(name, result.getOrNull()?.name)
            assertEquals(description, result.getOrNull()?.description)
            assertEquals(ChannelType.PUBLIC, result.getOrNull()?.type)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    @Test
    fun whenCreatingPublicConversationWithoutChannelIdShouldGenerateIt() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }
        every {
            pubnub.setChannelMetadata(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(
                Result.success(
                    getPNChannelMetadataResult(
                        Uuid.random().toString(),
                        name,
                        description,
                        customData,
                        updated,
                        ChannelType.PUBLIC.toString().lowercase(),
                        status
                    )
                )
            )
        }

        // when
        objectUnderTest.createPublicConversation().async { result ->
            val uuidPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            assertTrue(uuidPattern.matches(result.getOrNull()?.id as CharSequence))
            assertTrue(result.isSuccess)
            assertEquals(name, result.getOrNull()?.name)
            assertEquals(description, result.getOrNull()?.description)
            assertEquals(ChannelType.PUBLIC, result.getOrNull()?.type)
        }
    }

    @Test
    fun shouldThrowExceptionWhenGetCurrentUserMentionsWithCountBiggerThan100() {
        objectUnderTest.getCurrentUserMentions(count = 200).async { result ->
            assertTrue(result.isFailure)
            assertEquals("Count should not exceed 100", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun getUnreadMessagesCountsShouldReturnResult() {
        val numberOfMessagesUnread = 2L
        every {
            pubnub.getMemberships(
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
        } returns getMembershipsEndpoint
        every { getMembershipsEndpoint.async(any()) } calls { (callback: Consumer<Result<PNChannelMembershipArrayResult>>) ->
            callback.accept(Result.success(getPNChannelMembershipArrayResult()))
        }
        every { pubnub.messageCounts(channels = listOf(channelId), channelsTimetoken = any()) } returns messageCounts
        every { messageCounts.async(any()) } calls { (callback: Consumer<Result<PNMessageCountResult>>) ->
            callback.accept(Result.success(PNMessageCountResult(mapOf(channelId to numberOfMessagesUnread))))
        }

        objectUnderTest.getUnreadMessagesCounts().async { result: Result<Set<GetUnreadMessagesCounts>> ->
            assertTrue(result.isSuccess)
            assertFalse(result.getOrNull()?.isEmpty()!!)
            val messageCountForChannel: GetUnreadMessagesCounts = result.getOrNull()?.first()!!
            assertEquals(2, messageCountForChannel.count)
            assertEquals(channelId, messageCountForChannel.channel.id)
            assertEquals(channelId, messageCountForChannel.membership.channel.id)
            assertEquals(userId, messageCountForChannel.membership.user.id)
        }
    }

    @Test
    fun shouldRemoveRestrictionWhenBanAndMuteIsFalse() {
        val restrictedUserId = userId
        val restrictedChannelId = channelId
        val ban = false
        val mute = false
        val pnMemberArrayResult = PNMemberArrayResult(
            status = 200,
            data = listOf(PNMember(PNUUIDMetadata(id = userId), null, "", "", null)),
            1,
            null,
            null
        )
        val channelIdSlot = Capture.slot<String>()
        val userIdsSlot = Capture.slot<List<String>>()
        val userIdSlot = Capture.slot<String>()
        val restriction = Restriction(
            userId = restrictedUserId,
            channelId = restrictedChannelId,
            ban = ban,
            mute = mute,
            reason = "paid"
        )
        every {
            pubnub.removeChannelMembers(
                capture(channelIdSlot),
                capture(userIdsSlot),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns manageChannelMembersEndpoint
        every { manageChannelMembersEndpoint.async(any()) } calls { (callback: Consumer<Result<PNMemberArrayResult>>) ->
            callback.accept(Result.success(pnMemberArrayResult))
        }
        every { pubnub.publish(channel = capture(userIdSlot), message = any()) } returns publishEndpoint
        every { publishEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNPublishResult>>) ->
            callback1.accept(Result.success(PNPublishResult(timetoken)))
        }

        objectUnderTest.setRestrictions(restriction).async { result: Result<Unit> ->
            assertTrue(result.isSuccess)
        }

        val actualRestrictedChannelId: String = channelIdSlot.get()
        val actualRestrictedUserId: String = userIdsSlot.get()[0]
        val actualModerationEventChannelId = userIdSlot.get()
        assertEquals(restrictedUserId, actualRestrictedUserId)
        assertEquals("PUBNUB_INTERNAL_MODERATION_$restrictedChannelId", actualRestrictedChannelId)
        assertEquals(restrictedUserId, actualModerationEventChannelId)
    }

    @Test
    fun shouldAddRestrictionWhenBanIsTrue() {
        val restrictedUserId = userId
        val restrictedChannelId = channelId
        val ban = true
        val mute = false
        val reason = "He rehabilitated"
        val pnMemberArrayResult = PNMemberArrayResult(
            status = 200,
            data = listOf(PNMember(PNUUIDMetadata(id = userId), null, "", "", null)),
            1,
            null,
            null
        )
        val channelIdSlot = Capture.slot<String>()
        val userIdsSlot = Capture.slot<List<MemberInput>>()
        val userIdSlot = Capture.slot<String>()
        val encodedMessageSlot = Capture.slot<Map<String, Any>>()
        val restriction = Restriction(
            userId = restrictedUserId,
            channelId = restrictedChannelId,
            ban = ban,
            mute = mute,
            reason = reason
        )
        every {
            pubnub.setChannelMembers(
                channel = capture(channelIdSlot),
                uuids = capture(userIdsSlot)
            )
        } returns manageChannelMembersEndpoint
        every { manageChannelMembersEndpoint.async(any()) } calls { (callback: Consumer<Result<PNMemberArrayResult>>) ->
            callback.accept(Result.success(pnMemberArrayResult))
        }
        every {
            pubnub.publish(
                channel = capture(userIdSlot),
                message = capture(encodedMessageSlot)
            )
        } returns publishEndpoint
        every { publishEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNPublishResult>>) ->
            callback1.accept(Result.success(PNPublishResult(timetoken)))
        }

        objectUnderTest.setRestrictions(restriction).async { result: Result<Unit> ->
            assertTrue(result.isSuccess)
        }

        val actualRestrictedChannelId: String = channelIdSlot.get()
        val actualRestriction = userIdsSlot.get()[0].custom as Map<String, String>
        val actualModerationEventChannelId = userIdSlot.get()
        val actualEncodedMessageSlot = encodedMessageSlot.get()
        assertTrue(actualRestriction["ban"] as Boolean)
        assertEquals(reason, actualRestriction["reason"])
        assertEquals("banned", actualEncodedMessageSlot.get("restriction"))
        assertEquals("PUBNUB_INTERNAL_MODERATION_$restrictedChannelId", actualRestrictedChannelId)
        assertEquals(restrictedUserId, actualModerationEventChannelId)
    }

    private fun createMessage(chId: String = channelId, uId: String = userId): Message {
        return MessageImpl(
            chat = chatMock,
            timetoken = 123345,
            content = EventContent.TextMessageContent(
                text = "justo",
                files = listOf()
            ),
            channelId = chId,
            userId = uId,
            actions = mapOf(),
            metaInternal = null
        )
    }

    private fun getPNUuidMetadata() = PNUUIDMetadata(
        id = id,
        name = PatchValue.of(name),
        externalId = PatchValue.of(externalId),
        profileUrl = PatchValue.of(profileUrl),
        email = PatchValue.of(email),
        custom = PatchValue.of(customData),
        updated = PatchValue.of(updated),
        eTag = PatchValue.of("eTag"),
        type = PatchValue.of(typeAsString),
        status = PatchValue.of(status),
    )

    private fun getPNChannelMetadata() = PNChannelMetadata(
        id = id,
        name = PatchValue.of(name),
        description = PatchValue.of(description),
        custom = PatchValue.of(customData),
        updated = PatchValue.of(updated),
        eTag = PatchValue.of("updatedETag"),
        type = PatchValue.of(typeAsString),
        status = PatchValue.of(status)
    )

    private fun getPNUuidMetadataResult(): PNUUIDMetadataResult {
        val pnUUIDMetadata: PNUUIDMetadata = getPNUuidMetadata()
        return PNUUIDMetadataResult(status = 200, data = pnUUIDMetadata)
    }

    private fun getPNChannelMembershipArrayResult(): PNChannelMembershipArrayResult {
        val channelMetadata = PNChannelMetadata(
            id = channelId,
            name = null,
            description = null,
            custom = null,
            updated = null,
            eTag = null,
            type = null,
            status = null
        )

        val channelMembership = PNChannelMembership(
            channel = channelMetadata,
            custom = null,
            updated = "2024-05-20T14:50:19.972361Z",
            eTag = "AZO/t53al7m8fw",
            status = null
        )

        val data: List<PNChannelMembership> = mutableListOf(channelMembership)
        return PNChannelMembershipArrayResult(
            status = 200,
            data = data,
            totalCount = 1,
            next = null,
            prev = null
        )
    }

    @Test
    fun destroy() {
        every { timerManager.destroy() } returns Unit
        every { pubnub.destroy() } returns Unit
        objectUnderTest.destroy()
        verify { pubnub.destroy() }
        verify { timerManager.destroy() }
    }
}
