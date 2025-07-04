package com.pubnub.chat

import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.chat.types.GetChannelsResponse
import com.pubnub.kmp.PNFuture

/**
 * Represents an interface for types that refers to a channel group.
 */
interface ChannelGroup {
    /**
     * Unique identifier for a channel group.
     */
    val id: String

    /**
     * Reference to the main Chat object.
     */
    val chat: Chat

    /**
     * Returns a paginated list of all existing channels in a given [ChannelGroup].
     *
     * @param filter Expression used to filter the results. Returns only these channels whose properties satisfy the given expression are returned.
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc.
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     *
     * @return [PNFuture] containing [GetChannelsResponse].
     */
    fun listChannels(
        filter: String? = null,
        sort: Collection<PNSortKey<PNKey>> = listOf(),
        limit: Int? = null,
        page: PNPage? = null,
    ): PNFuture<GetChannelsResponse>

    /**
     * Adds [Channel] entities to a channel group.
     *
     * @param channels Channel entities to add.
     */
    fun addChannels(channels: List<Channel>): PNFuture<Unit>

    /**
     * Adds channels to a channel group.
     *
     * This method reduces the overhead of fetching full Channel entities when
     * only the IDs are known. It does not perform validation to check whether
     * the channels with the given IDs exist — responsibility for ensuring
     * validity lies with the caller.
     *
     * @param ids [Channel] identifiers to add.
     */
    fun addChannelIdentifiers(ids: List<String>): PNFuture<Unit>

    /**
     * Remove [Channel] entities from a channel group.
     */
    fun removeChannels(channels: List<Channel>): PNFuture<Unit>

    /**
     * Remove channels from a channel group.
     *
     * This method reduces the overhead of fetching full Channel entities when
     * only the IDs are known. It does not perform validation to check whether
     * the channels with the given IDs exist — responsibility for ensuring
     * validity lies with the caller.
     *
     * @param ids Channel identifiers to add.
     */
    fun removeChannelIdentifiers(ids: List<String>): PNFuture<Unit>

    /**
     * Returns a collection of users currently present in any channel within the given [ChannelGroup]
     */
    fun whoIsPresent(): PNFuture<Map<String, List<String>>>

    /**
     * Enables real-time tracking of users connecting to or disconnecting from the given [ChannelGroup]
     */
    fun streamPresence(callback: (presenceByChannels: Map<String, List<String>>) -> Unit): AutoCloseable

    /**
     * Watch the [ChannelGroup] content.
     *
     * @param callback custom behavior whenever a message is received
     * @return AutoCloseable interface you can call to stop listening for new messages by invoking the close() method.
     */
    fun connect(callback: (Message) -> Unit): AutoCloseable
}
