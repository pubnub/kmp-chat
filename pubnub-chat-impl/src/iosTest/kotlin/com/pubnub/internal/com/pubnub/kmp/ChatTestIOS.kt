package com.pubnub.internal.com.pubnub.kmp

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.member.MemberInput
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.kmp.ChatTest
import com.pubnub.kmp.CustomObject
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.Capture
import dev.mokkery.matcher.capture.capture
import dev.mokkery.matcher.capture.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatTestIOS : ChatTest() {
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
        val actualRestriction = userIdsSlot.get()[0].custom
        val customObject = actualRestriction as CustomObject
        val restrictionMap = customObject.value as Map<String, Any>

        assertTrue(restrictionMap?.get("ban") as Boolean ?: false)
        assertEquals(reason, restrictionMap["reason"])

        val actualModerationEventChannelId = userIdSlot.get()
        val actualEncodedMessageSlot = encodedMessageSlot.get()

        assertEquals("banned", actualEncodedMessageSlot.get("restriction"))
        assertEquals("PUBNUB_INTERNAL_MODERATION_$restrictedChannelId", actualRestrictedChannelId)
        assertEquals(restrictedUserId, actualModerationEventChannelId)
    }
}
