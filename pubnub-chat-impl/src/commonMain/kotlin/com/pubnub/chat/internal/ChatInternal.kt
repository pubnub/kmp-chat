package com.pubnub.chat.internal

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.message_actions.PNMessageAction
import com.pubnub.api.models.consumer.message_actions.PNRemoveMessageActionResult
import com.pubnub.chat.Channel
import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadChannel
import com.pubnub.chat.User
import com.pubnub.chat.internal.timer.TimerManager
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface ChatInternal : Chat {
    val editMessageActionName: String
    val deleteMessageActionName: String
    val reactionsActionName: String
    val timerManager: TimerManager

    fun createUser(user: User): PNFuture<User>

    fun removeThreadChannel(
        chat: Chat,
        message: Message,
        soft: Boolean = false
    ): PNFuture<Pair<PNRemoveMessageActionResult, Channel?>>

    fun restoreThreadChannel(message: Message): PNFuture<PNMessageAction?>

    fun createChannel(
        id: String,
        name: String? = null,
        description: String? = null,
        custom: CustomObject? = null,
        type: ChannelType? = null,
        status: String? = null,
    ): PNFuture<Channel>

    fun forwardMessage(message: Message, channelId: String): PNFuture<PNPublishResult>

    fun getThreadChannel(message: Message): PNFuture<ThreadChannel>

    /**
     * Retrieves all channels referenced in the [Channel.sendText] that match the provided 3-letter string from
     * your app's keyset.
     * @param text At least a 3-letter string typed in after # with the channel name you want to reference.
     * @param limit Maximum number of returned channel names that match the typed 3-letter suggestion.
     * The default value is set to 10, and the maximum is 100.
     *
     * @return [PNFuture] containing set of [Channel]
     */
    fun getChannelSuggestions(text: String, limit: Int = 10): PNFuture<List<Channel>>

    /**
     * Returns all suggested users that match the provided 3-letter string.
     *
     * @param text At least a 3-letter string typed in after @ with the user name you want to mention.
     * @param limit Maximum number of returned usernames that match the typed 3-letter suggestion.
     * The default value is set to 10, and the maximum is 100.
     *
     * @return [PNFuture] containing set of [User]
     */
    fun getUserSuggestions(text: String, limit: Int = 10): PNFuture<List<User>>

    /**
     * Internal use with functions
     */
    fun getMessageFromReport(
        reportEvent: Event<EventContent.Report>,
        lookupBefore: Duration = 3.seconds,
        lookupAfter: Duration = 5.seconds
    ): PNFuture<Message?>
}
