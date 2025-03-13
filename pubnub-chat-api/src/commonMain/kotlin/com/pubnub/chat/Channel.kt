package com.pubnub.chat

import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.chat.membership.MembersResponse
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.HistoryResponse
import com.pubnub.chat.types.JoinResult
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture

/**
 * Channel is an object that refers to a single chat room.
 */
interface Channel : BaseChannel<Channel, Message> {
    override fun update(
        name: String?,
        custom: CustomObject?,
        description: String?,
        status: String?,
        type: ChannelType?,
    ): PNFuture<Channel>

    override fun delete(soft: Boolean): PNFuture<Channel?>

    override fun connect(callback: (Message) -> Unit): AutoCloseable

    override fun unpinMessage(): PNFuture<Channel>

    override fun getMessage(timetoken: Long): PNFuture<Message?>

    override fun streamUpdates(callback: (channel: Channel?) -> Unit): AutoCloseable

    override fun getHistory(startTimetoken: Long?, endTimetoken: Long?, count: Int): PNFuture<HistoryResponse<Message>>

    override operator fun plus(update: PNChannelMetadata): Channel

    /**
     * Requests another user to join a channel(except Public channel) and become its member.
     *
     * @param user that you want to invite to a channel.
     *
     * @return [PNFuture] containing [Membership]
     */
    fun invite(user: User): PNFuture<Membership>

    /**
     * Requests other users to join a channel and become its members. You can invite up to 100 users at once.
     *
     * @param users List of users you want to invite to the [BaseChannel]. You can invite up to 100 users in one call.
     *
     * @return [PNFuture] containing list of [Membership] of invited users.
     */
    fun inviteMultiple(users: Collection<User>): PNFuture<List<Membership>>

    /**
     * Returns the list of all channel members.
     *
     * @param limit Number of objects to return in response. The default (and maximum) value is 100.
     * @param page Object used for pagination to define which previous or next result page you want to fetch.
     * @param filter Expression used to filter the results. Returns only these members whose properties satisfy the given expression.
     * @param sort A collection to specify the sort order. Available options are id, name, and updated. Use asc or desc.
     * to specify the sorting direction, or specify null to take the default sorting direction (ascending).
     *
     * @return [PNFuture] containing [MembersResponse]
     */
    fun getMembers(
        limit: Int? = 100,
        page: PNPage? = null,
        filter: String? = null,
        sort: Collection<PNSortKey<PNMemberKey>> = listOf(),
    ): PNFuture<MembersResponse>

    /**
     * Connects a user to the [BaseChannel] and sets membership - this way, the chat user can both watch the channel's
     * content and be its full-fledged member.
     *
     * @param custom Any custom properties or metadata associated with the channel-user membership in the form of a `Map`.
     * Values must be scalar only; arrays or objects are not supported.
     *                a JSON. Values must be scalar only; arrays or objects are not supported.
     * @param callback defines the custom behavior to be executed whenever a message is received on the [BaseChannel]
     *
     * @return [PNFuture] containing [JoinResult] that contains the [JoinResult.membership] and
     * [JoinResult.disconnect] that  lets you stop listening to new channel messages or message updates while remaining
     * a channel membership. This might be useful when you want to stop receiving notifications about new messages or
     * limit incoming messages or updates to reduce network traffic.
     */
    fun join(custom: CustomObject? = null, callback: ((Message) -> Unit)? = null): PNFuture<JoinResult>

    /**
     * Remove user's [BaseChannel] membership
     */
    fun leave(): PNFuture<Unit>

    /**
     * Lets you get a read confirmation status for messages you published on a channel.
     * @param callback defines the custom behavior to be executed when receiving a read confirmation status on the joined channel.
     *
     * @return AutoCloseable Interface you can call to stop listening for message read receipts
     * and clean up resources by invoking the close() method.
     */
    fun streamReadReceipts(callback: (receipts: Map<Long, List<String>>) -> Unit): AutoCloseable

    // Companion object required for extending this class elsewhere
    companion object
}
