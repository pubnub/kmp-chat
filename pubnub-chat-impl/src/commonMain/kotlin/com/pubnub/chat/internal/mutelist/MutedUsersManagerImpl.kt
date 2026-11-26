package com.pubnub.chat.internal.mutelist

import com.pubnub.api.PubNub
import com.pubnub.api.enums.PNStatusCategory
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.chat.internal.PREFIX_PUBNUB_PRIVATE
import com.pubnub.chat.internal.SUFFIX_MUTE_1
import com.pubnub.chat.internal.metadata.MetadataRepository
import com.pubnub.chat.mutelist.MutedUsersManager
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createStatusListener
import com.pubnub.kmp.then
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet

class MutedUsersManagerImpl internal constructor(
    val pubNub: PubNub,
    val userId: String,
    val syncEnabled: Boolean,
    private val metadataRepository: MetadataRepository,
) : MutedUsersManager {
    private val muteSetAtomic = atomic(emptySet<String>())
    override val mutedUsers by muteSetAtomic

    private val userMuteChannelId = "${PREFIX_PUBNUB_PRIVATE}$userId.$SUFFIX_MUTE_1"

    init {
        if (syncEnabled) {
            metadataRepository.streamMutedUsers(userMuteChannelId) { mutedUsers ->
                muteSetAtomic.value = mutedUsers
            }
            pubNub.addListener(
                createStatusListener(pubNub) { _, status: PNStatus ->
                    if (status.category == PNStatusCategory.PNConnectedCategory || status.category == PNStatusCategory.PNSubscriptionChanged) {
                        if (userMuteChannelId !in pubNub.getSubscribedChannels()) {
                            // the client might have been offline for a while and missed some updates so load the list first
                            loadMutedUsers().async { }
                            pubNub.subscribe(listOf(userMuteChannelId))
                        }
                    }
                }
            )
        }
    }

    fun loadMutedUsers(): PNFuture<Unit> {
        return if (syncEnabled) {
            metadataRepository.getMutedUsers(userMuteChannelId).then {
                muteSetAtomic.value = it
            }
        } else {
            Unit.asFuture()
        }
    }

    override fun muteUser(userId: String): PNFuture<Unit> {
        return updateMutedUsers { currentMutedUserIds -> currentMutedUserIds + userId }
    }

    override fun unmuteUser(userId: String): PNFuture<Unit> {
        return updateMutedUsers { currentMutedUserIds -> currentMutedUserIds - userId }
    }

    private fun updateMutedUsers(updateFunction: (Set<String>) -> Set<String>): PNFuture<Unit> {
        val newMuteSet = muteSetAtomic.updateAndGet(updateFunction)
        return if (syncEnabled) {
            metadataRepository.setMutedUsers(userMuteChannelId, userId, newMuteSet)
        } else {
            Unit.asFuture()
        }
    }
}
