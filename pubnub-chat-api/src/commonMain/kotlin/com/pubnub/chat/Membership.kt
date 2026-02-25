package com.pubnub.chat

import com.pubnub.api.models.consumer.pubsub.objects.PNSetMembershipEvent
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture

/**
 * Membership is an object that refers to a single user-channel relationship in a chat.
 */
interface Membership {
    /**
     * Reference to the main Chat object.
     */
    val chat: Chat

    /**
     * 	The [Channel] of this [Membership].
     */
    val channel: Channel

    /**
     * 	The [User] of this [Membership].
     */
    val user: User

    /**
     * Any custom properties or metadata associated with the user-channel relationship in the form of a map of key-value
     * pairs. App Context filtering language doesn’t support filtering by custom properties.
     */
    val custom: Map<String, Any?>?

    /**
     * Last time the Membership object was changed.
     */
    val updated: String?

    /**
     * Caching value that changes whenever the Membership object changes.
     */
    val eTag: String?

    /**
     * Status of a Membership
     */
    val status: String?

    /**
     * Type of a Membership
     */
    val type: String?

    /**
     * Timetoken of the last message a user read on a given channel.
     */
    val lastReadMessageTimetoken: Long?

    /**
     * Setting the last read message for users lets you implement the Read Receipts feature and monitor which channel member read which message.
     *
     * This method emits a read receipt event on the channel, unless
     * [com.pubnub.chat.config.ChatConfiguration.emitReadReceiptEvents] is set to `false` for the channel's type.
     *
     * @param message Last read message on a given channel with the timestamp that gets added to the user-channel membership as the [lastReadMessageTimetoken] property.
     * @return A [PNFuture] that returns an updated [Membership] object.
     */
    fun setLastReadMessage(message: Message): PNFuture<Membership>

    /**
     * Updates the channel membership information for a given user.
     *
     * @param custom Any custom properties or metadata associated with the channel-user membership in a `Map`. Values must be scalar only; arrays or objects are not supported. App Context filtering language doesn’t support filtering by custom properties.
     * @return A [PNFuture] that returns an updated [Membership] object.
     */
    fun update(custom: CustomObject?): PNFuture<Membership>

    /**
     * Setting the last read message for users lets you implement the Read Receipts feature and monitor which channel member read which message.
     *
     * This method emits a read receipt event on the channel, unless
     * [com.pubnub.chat.config.ChatConfiguration.emitReadReceiptEvents] is set to `false` for the channel's type.
     *
     * @param timetoken Timetoken of the last read message on a given channel that gets added to the user-channel membership as the [lastReadMessageTimetoken] property.
     * @return A [PNFuture] that returns an updated [Membership] object.
     */
    fun setLastReadMessageTimetoken(timetoken: Long): PNFuture<Membership>

    /**
     * Returns the number of messages you didn't read on a given channel. You can display this number on UI in the channel list of your chat app.
     *
     * @return A [PNFuture] that returns the number of unread messages on the membership's channel or `null` when [lastReadMessageTimetoken] is also `null`.
     */
    fun getUnreadMessagesCount(): PNFuture<Long?>

    /**
     * You can receive updates when specific user-channel Membership object(s) are added, edited, or removed.
     *
     * @param callback Defines the custom behavior to be executed when detecting membership changes.
     * @return An [AutoCloseable] that you can use to stop receiving objects events by invoking [AutoCloseable.close].
     */
    fun streamUpdates(callback: (membership: Membership?) -> Unit): AutoCloseable

    /**
     * Get a new [Membership] object updated with the values received in the [update].
     *
     * @param update Data received from the PubNub [com.pubnub.api.v2.callbacks.EventListener.objects] callback.
     * @return A [Membership] updated with values that are present in the [update]
     */
    operator fun plus(update: PNSetMembershipEvent): Membership

    companion object
}
