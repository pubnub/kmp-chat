package com.pubnub.kmp

import com.pubnub.api.PubNub
import com.pubnub.api.PubNubException
import com.pubnub.api.UserId
import com.pubnub.api.endpoints.objects.channel.GetAllChannelMetadata
import com.pubnub.api.endpoints.objects.channel.GetChannelMetadata
import com.pubnub.api.endpoints.objects.channel.RemoveChannelMetadata
import com.pubnub.api.endpoints.objects.channel.SetChannelMetadata
import com.pubnub.api.endpoints.objects.uuid.GetAllUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.GetUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.RemoveUUIDMetadata
import com.pubnub.api.endpoints.objects.uuid.SetUUIDMetadata
import com.pubnub.api.endpoints.presence.HereNow
import com.pubnub.api.endpoints.presence.WhereNow
import com.pubnub.api.endpoints.pubsub.Publish
import com.pubnub.api.endpoints.pubsub.Signal
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataArrayResult
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadataResult
import com.pubnub.api.models.consumer.presence.PNHereNowChannelData
import com.pubnub.api.models.consumer.presence.PNHereNowOccupantData
import com.pubnub.api.models.consumer.presence.PNHereNowResult
import com.pubnub.api.models.consumer.presence.PNWhereNowResult
import com.pubnub.api.v2.PNConfiguration
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.createPNConfiguration
import com.pubnub.kmp.channel.GetChannelsResponse
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.EventContent.TextMessageContent
import com.pubnub.kmp.types.MessageType
import com.pubnub.kmp.user.GetUsersResponse
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.Capture
import dev.mokkery.matcher.capture.capture
import dev.mokkery.matcher.capture.get
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ChatTest {
    private lateinit var objectUnderTest: ChatImpl
    private val chatConfig: ChatConfig = mock(MockMode.strict)

    private val chatMock: Chat = mock(MockMode.strict)
    private val pubnub: PubNub = mock(MockMode.strict)
    private lateinit var pnConfiguration: PNConfiguration
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
    private val id = "testId"
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
    private val meta = mapOf("one" to "ten")
    private val ttl = 10
    val timetoken: Long = 123457
    val pnException404 = PubNubException(statusCode = 404, errorMessage = "Requested object was not found.")

    @BeforeTest
    fun setUp() {
        pnConfiguration = createPNConfiguration(UserId(userId), subscribeKey, publishKey)
        every { chatConfig.pubnubConfig } returns pnConfiguration
        every { chatConfig.typingTimeout } returns 2000.milliseconds
        objectUnderTest = ChatImpl(chatConfig, pubnub)
    }

    @Test
    fun createUserShouldResultSuccessWhenUserDoesNotExist() {
        // given
        val pnUuidMetadataResult = getPNUuidMetadataResult()
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }
        every { pubnub.setUUIDMetadata(any(), any(), any(), any(), any(), any(), any(),any(),any())
        } returns setUUIDMetadataEndpoint
        every { setUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(pnUuidMetadataResult))
        }
        val callback: (Result<User>) -> Unit = { result: Result<User> ->
            assertTrue(result.isSuccess)
            assertEquals(id, result.getOrNull()?.id)
            assertEquals(name, result.getOrNull()?.name)
            assertEquals(externalId, result.getOrNull()?.externalId)
            assertEquals(profileUrl, result.getOrNull()?.profileUrl)
            assertEquals(email, result.getOrNull()?.email)
            assertEquals(status, result.getOrNull()?.status)
            assertEquals(typeAsString, result.getOrNull()?.type)
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
            type = typeAsString,
            callback = callback
        )
    }

    @Test
    fun createUserShouldResultFailureWhenUserExists() {
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(getPNUuidMetadataResult()))
        }

        val callback: (Result<User>) -> Unit = { result: Result<User> ->
            assertTrue(result.isFailure)
            assertEquals("User with this ID already exists", result.exceptionOrNull()?.message)

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
            type = typeAsString,
            callback = callback
        )
    }

    @Test
    fun whenCreatingUseriWithcanCreateUser() {

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
            type = typeAsString,
            callback = callback
        )

        // then
        verify { pubnub.getUUIDMetadata(uuid = id, includeCustom = false) }
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
        objectUnderTest.deleteUser(id, softDeleteFalse, callback)

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
        val status = "Deleted"
        val includeCustomFalse = false

        // when
        objectUnderTest.deleteUser(id, softDelete, callback)

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
        val callback: (Result<User>) -> Unit = { result: Result<User> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Id is required", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.deleteUser(emptyID, softDelete, callback)
    }

    @Test
    fun when_wherePresent_executedCallbackResultShouldContainListOfChannels() {
        // given
        val whereNowEndpoint: WhereNow = mock(MockMode.strict)
        val channel01 = "myChannel1"
        val channel02 = "myChannel02"
        val pnWhereNowResult: PNWhereNowResult = PNWhereNowResult(listOf(channel01, channel02))
        every { pubnub.whereNow(any()) } returns whereNowEndpoint
        every { whereNowEndpoint.async(any()) } calls { (callback: Consumer<Result<PNWhereNowResult>>) ->
            callback.accept(Result.success(pnWhereNowResult))
        }

        val callback: (Result<List<String>>) -> Unit = { result: Result<List<String>> ->
            // then
            assertTrue(result.isSuccess)
            assertFalse(result.getOrNull()!!.isEmpty())
            assertTrue(result.getOrNull()!!.contains(channel01))
            assertTrue(result.getOrNull()!!.contains(channel02))
        }

        // when
        objectUnderTest.wherePresent(id, callback)
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

        val callback: (Result<Boolean>) -> Unit = { result: Result<Boolean> ->
            // then
            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull()!!)
        }

        // when
        objectUnderTest.isPresent(id, channelId, callback)
    }

    @Test
    fun whenChannelIdIsEmptyThen_isPresent_shouldResultsFailure() {
        // given
        val emptyChannelId = ""
        val callback: (Result<Boolean>) -> Unit = { result: Result<Boolean> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Channel Id is required", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.isPresent(id, emptyChannelId, callback)
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
            1, 2, mutableMapOf(
                channel01 to PNHereNowChannelData(
                    channel01, 2, listOf(PNHereNowOccupantData(user01), PNHereNowOccupantData(user02))
                )
            )
        )
        every { pubnub.hereNow(any()) } returns hereNowEndpoint
        every { hereNowEndpoint.async(any()) } calls { (callback: Consumer<Result<PNHereNowResult>>) ->
            callback.accept(Result.success(pnHereNowResult))
        }

        val callback: (Result<Collection<String>>) -> Unit = { result: Result<Collection<String>> ->
            // then
            assertTrue(result.isSuccess)
            assertTrue { result.getOrNull()?.contains(user01) == true }
            assertTrue { result.getOrNull()?.contains(user02) == true }
        }

        // when
        objectUnderTest.whoIsPresent(channelId, callback)
    }

    @Test
    fun whenChannelIdIsEmptyThen_whoIsPresent_shouldResultsFailure() {
        // given
        val emptyChannelId = ""
        val callback: (Result<Collection<String>>) -> Unit = { result: Result<Collection<String>> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Channel Id is required", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.whoIsPresent(emptyChannelId, callback)
    }

    @Test
    fun whenChannelIdIsEmptyResultShouldContainException() {
        // given
        val channelId = ""
        val callback: (Result<Channel>) -> Unit = { result: Result<Channel> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Channel Id is required", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.updateChannel(id = channelId, callback = callback)
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
        val callback: (Result<Channel>) -> Unit = { result: Result<Channel> ->
            // then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message!!.contains("Failed to create/update channel data"))
        }

        // when
        objectUnderTest.updateChannel(id = id, callback = callback)
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
        val callback: (Result<Channel>) -> Unit = { result: Result<Channel> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("Channel not found", result.exceptionOrNull()?.message)
        }

        // when
        objectUnderTest.updateChannel(id = id, callback = callback)
    }

    @Test
    fun shouldResultSuccessWhenSetChannelMetadataResultSuccess() {
        val updatedName = "updatedName"
        val updatedDescription = "updatedDescription"
        val updatedCustom = mapOf("cos" to "cos1")
        val updatedUpdated = "updatedUpdated"
        val updatedType = "GROUP"
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
                        updatedName,
                        updatedDescription,
                        updatedCustom,
                        updatedUpdated,
                        updatedType,
                        updatedStatus
                    )
                )
            )
        }
        val callback: (Result<Channel>) -> Unit = { result: Result<Channel> ->
            // then
            assertTrue(result.isSuccess)
            assertEquals(id, result.getOrNull()!!.id)
            assertEquals(updatedName, result.getOrNull()!!.name)
            assertEquals(updatedDescription, result.getOrNull()!!.description)
            assertTrue(result.getOrNull()!!.custom is CustomObject)
            assertEquals(updatedUpdated, result.getOrNull()!!.updated)
            assertEquals(ChannelType.valueOf(updatedType), result.getOrNull()!!.type)
            assertEquals(updatedStatus, result.getOrNull()!!.status)
        }

        // when
        objectUnderTest.updateChannel(id = id, name = name, description = description, callback = callback)
    }

    @Test
    fun canHardDeleteChannel() {
        val callback: (Result<Channel>) -> Unit = {}
        every { pubnub.getChannelMetadata(any(), any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(
                Result.success(
                    getPNChannelMetadataResult(
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

        objectUnderTest.deleteChannel(id = id, soft = false, callback = callback)

        verify { pubnub.removeChannelMetadata(channel = id) }
    }

    @Test
    fun canSoftDeleteChannel() {
        // given
        val callback: (Result<Channel>) -> Unit = {}
        every {
            pubnub.setChannelMetadata(any(), any(), any(), any(), any(), any(), any())
        } returns setChannelMetadataEndpoint
        every { setChannelMetadataEndpoint.async(any()) } returns Unit
        val pnChannelMetadataResult: PNChannelMetadataResult =
            getPNChannelMetadataResult(name, description, customData, updated, typeAsString, status)
        every { pubnub.getChannelMetadata(any(), any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(pnChannelMetadataResult))
        }
        val softDelete = true
        val status = "Deleted"
        val includeCustomFalse = false

        // when
        objectUnderTest.deleteChannel(id, softDelete, callback)

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
        val callback: (Result<Unit>) -> Unit = { result: Result<Unit> ->
            // then
            assertTrue(result.isFailure)
            assertEquals("You cannot forward the message to the same channel.", result.exceptionOrNull()!!.message)
        }

        // when
        objectUnderTest.forwardMessage(message, channelId, callback)
    }

    @Test
    fun forwardedMessageShouldContainOriginalPublisherLocatedInMeta() {
        val message = createMessage()
        val channelId = "forwardedChannelId"
        val callback: (Result<Unit>) -> Unit = { result: Result<Unit> ->
            assertTrue(result.isSuccess)
        }
        val metaSlot = Capture.slot<Any>()
        every {
            pubnub.publish(
                channel = any(),
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

        objectUnderTest.forwardMessage(message, channelId, callback)

        val actualMeta: Map<String, String> = metaSlot.get() as Map<String, String>
        val mapEntry = mapOf("originalPublisher" to userId).entries.first()
        assertTrue(actualMeta.entries.contains(mapEntry))
    }

    @Test
    fun shouldCalSignalWhenEmitEventWithMethodSignal() {
        every { pubnub.signal(any(), any()) } returns signalEndpoint
        every { signalEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNPublishResult>>) ->
            callback1.accept(Result.success(PNPublishResult(timetoken)))
        }
        val method = EmitEventMethod.SIGNAL
        val payload = EventContent.Typing(true)
        val callback: (Result<PNPublishResult>) -> Unit = { result ->
            assertTrue(result.isSuccess)
            assertEquals(timetoken, result.getOrNull()?.timetoken)
        }

        objectUnderTest.emitEvent(
            channel = channelId,
            method = method,
            type = typeAsString,
            payload = payload,
            callback = callback
        )

        verify { pubnub.signal(channel = channelId, message = payload) }
    }

    @Test
    fun shouldCalPublishWhenEmitEventWithMethodPublish() {
        every { pubnub.publish(any(), any(), any(), any(), any(), any(), any()) } returns publishEndpoint
        every { publishEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNPublishResult>>) ->
            callback1.accept(Result.success(PNPublishResult(timetoken)))
        }
        val method = EmitEventMethod.PUBLISH
        val payload = EventContent.TextMessageContent(type = MessageType.TEXT, text = "messageContent")
        val callback: (Result<PNPublishResult>) -> Unit = { result ->
            assertTrue(result.isSuccess)
            assertEquals(timetoken, result.getOrNull()?.timetoken)
        }

        objectUnderTest.emitEvent(
            channel = channelId,
            method = method,
            type = typeAsString,
            payload = payload,
            callback = callback
        )

        verify { pubnub.publish(channel = channelId, message = payload) }
    }

    @Test
    fun whenEmitEventMethodIsPublishPayloadShouldBeOfTypeTextMessage() {
        val payload = EventContent.Typing(true)
        val method = EmitEventMethod.PUBLISH
        val callback: (Result<PNPublishResult>) -> Unit = { result ->
            assertTrue(result.isFailure)
            assertEquals(
                "When emitEvent method is PUBLISH payload should be of type EventContent.TextMessageContent",
                result.exceptionOrNull()?.message
            )
        }

        objectUnderTest.emitEvent(
            channel = channelId,
            method = method,
            type = typeAsString,
            payload = payload,
            callback = callback
        )
    }

    @Test
    fun whenChannelIdIsEmptyThenGetChannelShouldResultFailure() {
        val emptyChannelId = ""
        val callback: (Result<Channel?>) -> Unit = { result ->
            assertTrue(result.isFailure)
            assertEquals("Channel Id is required", result.exceptionOrNull()?.message)
        }
        objectUnderTest.getChannel(emptyChannelId, callback)
    }

    @Test
    fun whenChannelNotFoundShouldReturnProperMessage() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }
        val callback: (Result<Channel?>) -> Unit = { result ->
            assertTrue(result.isSuccess)
            assertNull(result.getOrNull())
        }

        objectUnderTest.getChannel(channelId, callback)
    }

    @Test
    fun getChannelShouldResultSuccessWhenChannelExists() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(
                Result.success(
                    getPNChannelMetadataResult(
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

        val callback: (Result<Channel?>) -> Unit = { result ->
            assertTrue(result.isSuccess)
            assertEquals(id, result.getOrNull()?.id)
            assertEquals(name, result.getOrNull()?.name)
            assertEquals(description, result.getOrNull()?.description)
            assertEquals(updated, result.getOrNull()?.updated)
            assertEquals(typeAsString, result.getOrNull()?.type.toString().lowercase())
            assertEquals(status, result.getOrNull()?.status)
        }

        objectUnderTest.getChannel(channelId, callback)
    }

    @Test
    fun createChannelShouldResultFailureWhenChannelExists() {
        every { pubnub.getChannelMetadata(any()) } returns getChannelMetadataEndpoint
        every { getChannelMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNChannelMetadataResult>>) ->
            callback1.accept(Result.success(getPNChannelMetadataResult()))
        }

        val callback: (Result<Channel>) -> Unit = { result ->
            assertTrue(result.isFailure)
            assertEquals("Channel with this ID already exists", result.exceptionOrNull()?.message)
        }

        objectUnderTest.createChannel(id = id, name = name, callback = callback)
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

        objectUnderTest.createChannel(id = id, name = name) { result ->
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
    fun whenUserIdIsEmptyThenGetChannelShouldResultFailure() {
        val emptyUserId = ""
        val callback: (Result<User?>) -> Unit = { result: Result<User?> ->
            assertTrue(result.isFailure)
            assertEquals("Id is required", result.exceptionOrNull()?.message)
        }

        objectUnderTest.getUser(emptyUserId, callback)
    }

    @Test
    fun whenUserNotFoundShouldReturnProperMessage() {
        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.failure(pnException404))
        }

        val callback: (Result<User?>) -> Unit = { result: Result<User?> ->
            assertTrue(result.isSuccess)
            assertNull(result.getOrNull())
        }

        objectUnderTest.getUser(userId, callback)
    }

    @Test
    fun getUserShouldResultSuccessWhenUserExists() {
        val pnUuidMetadataResult = getPNUuidMetadataResult()

        every { pubnub.getUUIDMetadata(any(), any()) } returns getUUIDMetadataEndpoint
        every { getUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataResult>>) ->
            callback1.accept(Result.success(pnUuidMetadataResult))
        }

        val callback: (Result<User?>) -> Unit = { result: Result<User?> ->
            assertTrue(result.isSuccess)
            assertEquals(id, result.getOrNull()?.id)
            assertEquals(name, result.getOrNull()?.name)
            assertEquals(externalId, result.getOrNull()?.externalId)
            assertEquals(profileUrl, result.getOrNull()?.profileUrl)
            assertEquals(email, result.getOrNull()?.email)
            assertEquals(updated, result.getOrNull()?.updated)
            assertEquals(status, result.getOrNull()?.status)
        }

        objectUnderTest.getUser(userId = userId, callback = callback)
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
        val callback: (Result<GetUsersResponse>) -> Unit = { result: Result<GetUsersResponse> ->
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
        val filter = "name LIKE 'test*'"

        objectUnderTest.getUsers(filter = filter, callback = callback)

    }

    @Test
    fun getUsersShouldResultFailureWhenUserCanNotBeRetrieved() {
        every { pubnub.getAllUUIDMetadata(any(), any(), any(), any(), any(), any()) } returns getAllUUIDMetadataEndpoint
        every { getAllUUIDMetadataEndpoint.async(any()) } calls { (callback1: Consumer<Result<PNUUIDMetadataArrayResult>>) ->
            callback1.accept(Result.failure(Exception("Error calling getAllUUIDMetadata")))
        }
        val callback: (Result<GetUsersResponse>) -> Unit = { result ->
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message!!.contains("Failed to get users."))
        }
        objectUnderTest.getUsers(callback = callback)
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

        val callback: (Result<GetChannelsResponse>) -> Unit = { result ->
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
        val filter = "description LIKE '*support*'"
        objectUnderTest.getChannels(filter = filter, callback = callback)
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

        val callback: (Result<GetChannelsResponse>) -> Unit = { result ->
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message!!.contains("Failed to get channels."))
        }

        objectUnderTest.getChannels(callback = callback)
    }

    private fun getPNChannelMetadataResult(
        updatedName: String = "",
        updatedDescription: String = "",
        updatedCustom: Map<String, Any?>? = null,
        updatedUpdated: String = "",
        updatedType: String = ChannelType.GROUP.toString().lowercase(),
        updatedStatus: String = "",
    ): PNChannelMetadataResult {
        val pnChannelMetadata = PNChannelMetadata(
            id = id,
            name = updatedName,
            description = updatedDescription,
            custom = updatedCustom,
            updated = updatedUpdated,
            eTag = "updatedETag",
            type = updatedType,
            status = updatedStatus
        )
        return PNChannelMetadataResult(status = 200, data = pnChannelMetadata)
    }


    private fun createMessage(): Message {
        return Message(
            chat = chatMock,
            timetoken = "123345",
            content = TextMessageContent(
                type = MessageType.TEXT,
                text = "justo",
                files = listOf()
            ),
            channelId = channelId,
            userId = userId,
            actions = mapOf(),
            meta = mapOf()
        )
    }

    private fun getPNUuidMetadata() = PNUUIDMetadata(
        id = id,
        name = name,
        externalId = externalId,
        profileUrl = profileUrl,
        email = email,
        custom = customData,
        updated = updated,
        eTag = "eTag",
        type = typeAsString,
        status = status
    )

    private fun getPNChannelMetadata() = PNChannelMetadata(
        id = id,
        name = name,
        description = description,
        custom = customData,
        updated = updated,
        eTag = "updatedETag",
        type = typeAsString,
        status = status
    )

    private fun getPNUuidMetadataResult() :PNUUIDMetadataResult{
        val pnUUIDMetadata: PNUUIDMetadata = getPNUuidMetadata()
        return PNUUIDMetadataResult(status = 200, data = pnUUIDMetadata)
    }
}
