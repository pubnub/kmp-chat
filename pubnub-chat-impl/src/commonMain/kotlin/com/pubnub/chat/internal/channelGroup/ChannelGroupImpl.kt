package com.pubnub.chat.internal.channelGroup

import co.touchlab.kermit.Logger
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.presence.PNHereNowOccupantData
import com.pubnub.api.v2.subscriptions.SubscriptionOptions
import com.pubnub.chat.Channel
import com.pubnub.chat.ChannelGroup
import com.pubnub.chat.Message
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.defaultGetMessageResponseBody
import com.pubnub.chat.internal.error.PubNubErrorMessage.ERROR_HANDLING_ONMESSAGE_EVENT
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.remember
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync

data class ChannelGroupImpl internal constructor(
    override val id: String,
    override val chat: ChatInternal
) : ChannelGroup {
    companion object {
        private val log = Logger.withTag("ChannelGroupImpl")
    }

    override fun listChannels(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?
    ): PNFuture<GetChannelsResponse> {
        return chat.pubNub.listChannelsForChannelGroup(id).thenAsync { response ->
            if (response.channels.isEmpty()) {
                GetChannelsResponse(
                    emptyList(),
                    null,
                    null,
                    0
                ).asFuture()
            } else {
                chat.getChannels(
                    createFinalFilterToApply(filter, response.channels),
                    sort,
                    limit,
                    page
                )
            }
        }
    }

    private fun createFinalFilterToApply(currentFilter: String? = null, channelIdentifiers: List<String>): String? {
        val channelsFilter = channelIdentifiers.joinToString(" || ") {
            "id == '$it'"
        }
        return currentFilter?.let {
            it.plus(" && ($channelsFilter)")
        } ?: run {
            channelsFilter
        }
    }

    override fun addChannels(channels: List<Channel>): PNFuture<Unit> {
        return chat.pubNub.addChannelsToChannelGroup(channels.map { it.id }, id).then {}
    }

    override fun addChannelIdentifiers(ids: List<String>): PNFuture<Unit> {
        return chat.pubNub.addChannelsToChannelGroup(ids, id).then {}
    }

    override fun removeChannels(channels: List<Channel>): PNFuture<Unit> {
        return chat.pubNub.removeChannelsFromChannelGroup(channels.map { it.id }, id).then {}
    }

    override fun removeChannelIdentifiers(ids: List<String>): PNFuture<Unit> {
        return chat.pubNub.removeChannelsFromChannelGroup(ids, id).then {}
    }

    override fun whoIsPresent(): PNFuture<Map<String, List<String>>> {
        return chat.pubNub.hereNow(channelGroups = listOf(id)).then { response ->
            response.channels.mapValues { it ->
                it.value.occupants.map(PNHereNowOccupantData::uuid)
            }
        }
    }

    override fun streamPresence(callback: (userIds: Map<String, List<String>>) -> Unit): AutoCloseable {
        val ids = mutableMapOf<String, MutableSet<String>>()
        val future = whoIsPresent().then { whoIsPresentResponse ->
            ids.putAll(whoIsPresentResponse.mapValues { it.value.toMutableSet() })
            callback(whoIsPresentResponse)
        }.then {
            chat.pubNub.channelGroup(id).subscription(SubscriptionOptions.receivePresenceEvents())
                .also { subscription ->
                    subscription.addListener(
                        createEventListener(
                            chat.pubNub,
                            onPresence = { _, event ->
                                val leaveUuids = event.leave ?: emptyList<String>()
                                val joinUuids = event.join ?: emptyList<String>()
                                val timedOutUuids = event.timeout ?: emptyList<String>()

                                joinUuids.forEach { id ->
                                    if (ids.contains(event.channel)) {
                                        ids[event.channel]?.add(id)
                                    } else {
                                        ids[event.channel] = joinUuids.toMutableSet()
                                    }
                                }
                                leaveUuids.forEach { id ->
                                    ids[event.channel]?.remove(id)
                                }
                                timedOutUuids.forEach { id ->
                                    ids[event.channel]?.remove(id)
                                }

                                when (event.event) {
                                    "join" -> {
                                        event.uuid.let { uuid ->
                                            ids[event.channel]?.plus(uuid)
                                        }
                                    }

                                    "leave", "timeout" -> {
                                        event.uuid.let { uuid ->
                                            ids[event.uuid]?.remove(uuid)
                                        }
                                    }
                                }
                                callback(ids.mapValues { it.value.toList() })
                            }
                        )
                    )
                    subscription.subscribe()
                }
        }.remember()

        return AutoCloseable {
            future.then { it.close() }
        }
    }

    override fun connect(callback: (Message) -> Unit): AutoCloseable {
        val channelGroupEntity = chat.pubNub.channelGroup(id)
        val subscription = channelGroupEntity.subscription()
        val listener = createEventListener(
            chat.pubNub,
            onMessage = { _, pnMessageResult ->
                if (pnMessageResult.publisher in chat.mutedUsersManager.mutedUsers) {
                    return@createEventListener
                }
                try {
                    if (
                        (
                            chat.config.customPayloads?.getMessageResponseBody?.invoke(
                                pnMessageResult.message,
                                pnMessageResult.channel,
                                ::defaultGetMessageResponseBody
                            )
                                ?: defaultGetMessageResponseBody(pnMessageResult.message)
                        ) == null
                    ) {
                        return@createEventListener
                    }
                    callback(MessageImpl.fromDTO(chat, pnMessageResult))
                } catch (e: Exception) {
                    log.e(throwable = e) { ERROR_HANDLING_ONMESSAGE_EVENT }
                }
            },
        )
        subscription.addListener(listener)
        subscription.subscribe()

        return subscription
    }
}
