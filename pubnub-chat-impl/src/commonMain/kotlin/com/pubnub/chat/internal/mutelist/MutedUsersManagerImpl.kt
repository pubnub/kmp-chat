package com.pubnub.chat.internal.mutelist

import com.pubnub.api.PubNub
import com.pubnub.api.enums.PNStatusCategory
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteUUIDMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNSetUUIDMetadataEventMessage
import com.pubnub.api.utils.PatchValue
import com.pubnub.chat.internal.PREFIX_PUBNUB_PRIVATE
import com.pubnub.chat.internal.SUFFIX_MUTE_1
import com.pubnub.chat.internal.TYPE_PUBNUB_PRIVATE
import com.pubnub.chat.internal.util.nullOn404
import com.pubnub.chat.mutelist.MutedUsersManager
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.createStatusListener
import com.pubnub.kmp.remember
import com.pubnub.kmp.then
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.updateAndGet

class MutedUsersManagerImpl(val pubNub: PubNub, val userId: String, val syncEnabled: Boolean) : MutedUsersManager {
    private val muteSetAtomic = atomic(emptySet<String>())
    override val mutedUsers by muteSetAtomic

    private val userMuteChannelId = "${PREFIX_PUBNUB_PRIVATE}$userId.$SUFFIX_MUTE_1"

    init {
        if (syncEnabled) {
            pubNub.addListener(
                createEventListener(
                    pubNub,
                    onObjects = { _, objectEvent ->
                        when (val message = objectEvent.extractedMessage) {
                            is PNSetUUIDMetadataEventMessage -> {
                                if (message.data.id == userMuteChannelId) {
                                    muteSetAtomic.value = customToMutedUsersSet(message.data.custom)
                                }
                            }

                            is PNDeleteUUIDMetadataEventMessage -> {
                                if (message.uuid == userMuteChannelId) {
                                    muteSetAtomic.value = emptySet()
                                }
                            }

                            else -> {}
                        }
                    }
                )
            )
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
        return pubNub.getUUIDMetadata(
            userMuteChannelId,
            includeCustom = true
        ).nullOn404().then {
            muteSetAtomic.value = customToMutedUsersSet(it?.data?.custom)
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
            pubNub.setUUIDMetadata(
                name = userId,
                uuid = userMuteChannelId,
                includeCustom = false,
                type = TYPE_PUBNUB_PRIVATE,
                custom = mutedUsersSetToCustom(newMuteSet)
            ).then { Unit }.remember()
        } else {
            Unit.asFuture()
        }
    }

    private fun customToMutedUsersSet(custom: PatchValue<Map<String, Any?>?>?): Set<String> {
        val mutedUsersList = custom?.value?.getOrElse("m") { "" } as? String
        return mutedUsersList?.split(",")?.filterNot { it.isEmpty() }?.toSet() ?: emptySet()
    }

    private fun mutedUsersSetToCustom(set: Set<String>): CustomObject {
        return createCustomObject(mapOf("m" to set.joinToString(",")))
    }
}
