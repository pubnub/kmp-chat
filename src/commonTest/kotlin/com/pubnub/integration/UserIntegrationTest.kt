package com.pubnub.integration

import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.kmp.channel.ChannelImpl
import com.pubnub.kmp.restrictions.GetRestrictionsResponse
import com.pubnub.kmp.restrictions.Restriction
import com.pubnub.test.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserIntegrationTest : BaseChatIntegrationTest() {

    @Test
    fun getChannelRestrictions() = runTest {
        val channelId = "channelId01"
        val channel = ChannelImpl(chat = chatPam, id = channelId)
        val ban = true
        val mute = true
        val reason = "rude"

        userPam.setRestrictions(channel = channel, ban = ban, mute = mute, reason = reason).await()

        val restriction: Restriction = userPam.getChannelRestrictions(channel).await()
        assertEquals(userPam.id, restriction.userId)
        assertEquals(channelId, restriction.channelId)
        assertEquals(ban, restriction.ban)
        assertEquals(mute, restriction.mute)
        assertEquals(reason, restriction.reason)
    }

    @Test
    fun getChannelsRestrictions_sortAsc() = runTest {
        val channelId01 = "channelId01"
        val channelId02 = "channelId02"
        val ban = true
        val mute = true
        val reason = "rude"
        val limit = 2
        val page = null
        val sort: Collection<PNSortKey<PNMembershipKey>> = listOf(PNSortKey.PNAsc(PNMembershipKey.CHANNEL_ID))

        userPam.setRestrictions(
            channel = ChannelImpl(chat = chatPam, id = channelId01),
            ban = ban,
            mute = mute,
            reason = reason
        ).await()
        userPam.setRestrictions(
            channel = ChannelImpl(chat = chatPam, id = channelId02),
            ban = ban,
            mute = mute,
            reason = reason
        ).await()

        val getRestrictionsResponse = userPam.getChannelsRestrictions(limit = limit, page = page, sort = sort).await()

        assertEquals(limit, getRestrictionsResponse.total)
        assertEquals(200, getRestrictionsResponse.status)
        val firstRestriction = getRestrictionsResponse.restrictions.first()
        assertEquals(channelId01, firstRestriction.channelId)
        assertEquals(ban, firstRestriction.ban)
        assertEquals(mute, firstRestriction.mute)
        assertEquals(reason, firstRestriction.reason)
        val secondRestriction = getRestrictionsResponse.restrictions.elementAt(1)
        assertEquals(channelId02, secondRestriction.channelId)
        assertEquals(ban, secondRestriction.ban)
        assertEquals(mute, secondRestriction.mute)
        assertEquals(reason, secondRestriction.reason)

    }

    @Test
    fun getChannelsRestrictions_sortDsc() = runTest {
        val channelId01 = "channelId01"
        val channelId02 = "channelId02"
        val ban = true
        val mute = true
        val reason = "rude"
        val limit = 2
        val page = null
        val sort: Collection<PNSortKey<PNMembershipKey>> = listOf(PNSortKey.PNDesc(PNMembershipKey.CHANNEL_ID))

        userPam.setRestrictions(
            channel = ChannelImpl(chat = chatPam, id = channelId01),
            ban = ban,
            mute = mute,
            reason = reason
        ).await()
        userPam.setRestrictions(
            channel = ChannelImpl(chat = chatPam, id = channelId02),
            ban = ban,
            mute = mute,
            reason = reason
        ).await()

        val getRestrictionsResponse: GetRestrictionsResponse =
            userPam.getChannelsRestrictions(limit = limit, page = page, sort = sort).await()

        val firstRestriction = getRestrictionsResponse.restrictions.first()
        assertEquals(channelId02, firstRestriction.channelId)
        val secondRestriction = getRestrictionsResponse.restrictions.elementAt(1)
        assertEquals(channelId01, secondRestriction.channelId)

    }
}