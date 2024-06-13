package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.asString
import com.pubnub.api.decode
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.member.PNUUIDDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.error.PubNubErrorMessage
import com.pubnub.kmp.error.PubNubErrorMessage.TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.kmp.membership.MembersResponse
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.MessageMentionedUser
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val MINIMAL_TYPING_INDICATOR_TIMEOUT: Duration = 1.seconds

data class Channel(
    private val chat: Chat,
    private val clock: Clock = Clock.System,
    val id: String,
    val name: String? = null,
    val custom: CustomObject? = null,
    val description: String? = null,
    val updated: String? = null,
    val status: String? = null,
    val type: ChannelType? = null,
) {
    private val suggestedNames = mutableMapOf<String, List<Membership>>()
    private var disconnect: (() -> Unit)? = null
    private var typingSent: Instant? = null
    private var typingIndicators =
        mutableMapOf<String, String>() //todo probably should be something like mutableMapOf<String, TimerTask>()
    private val sendTextRateLimiter: String? = null // todo should be ExponentialRateLimiter instead of String

    fun update(
        name: String? = null,
        custom: CustomObject? = null,
        description: String? = null,
        updated: String? = null,
        status: String? = null,
        type: ChannelType? = null,
    ): PNFuture<Channel> {
        return chat.updateChannel(id, name, custom, description, updated, status, type)
    }

    fun delete(soft: Boolean = false): PNFuture<Channel> {
        return chat.deleteChannel(id, soft)
    }

    fun forwardMessage(message: Message): PNFuture<Unit> {
        return chat.forwardMessage(message, this.id)
    }

    fun startTyping() : PNFuture<Unit> {
        if (type == ChannelType.PUBLIC) {
            return PubNubException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message).asFuture()
        }

        val now = clock.now()
        // todo currently in TypeScript there is "this.chat.config.typingTimeout - 1000". Typing timeout is actually 1sec shorter than this.chat.config.typingTimeout
        //  Writing TypeScript wrapper make sure to mimic this behaviour. In KMP the lowest possible value for this timeout is 1000(millis)
        typingSent?.let { typingSentNotNull: Instant ->
            if (!timeoutElapsed(typingSentNotNull, now)) {
                return Unit.asFuture()
            }
        }

        typingSent = now
        return sendTypingSignal(true)
    }

    fun stopTyping() : PNFuture<Unit> {
        if (type == ChannelType.PUBLIC) {
            return PubNubException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message).asFuture()
        }

        typingSent?.let { typingSentNotNull: Instant ->
            val now = clock.now()
            if(timeoutElapsed(typingSentNotNull, now)) {
                return Unit.asFuture()
            }
        } ?: return Unit.asFuture()

        typingSent = null
        return sendTypingSignal(false)
    }

    fun whoIsPresent(): PNFuture<Collection<String>> {
        return chat.whoIsPresent(id)
    }

    fun isPresent(userId: String): PNFuture<Boolean> {
        return chat.isPresent(userId, id)
    }

    fun getHistory(startTimetoken: Long? = null, endTimetoken: Long? = null, count: Int? = 25): PNFuture<List<Message>> {
        return chat.pubNub.fetchMessages(
            listOf(id),
            PNBoundedPage(startTimetoken, endTimetoken, count),
            includeMessageActions = true,
            includeMeta = true
        ).then { value ->
                value.channels[id]?.map { messageItem: PNFetchMessageItem ->
                    val eventContent = try {
                        messageItem.message.asString()?.let { text ->
                            EventContent.TextMessageContent(text, null)
                        } ?: PNDataEncoder.decode(messageItem.message)
                    } catch (e: Exception) {
                        EventContent.UnknownMessageFormat(messageItem.message)
                    }

                    Message(
                        chat,
                        messageItem.timetoken!!,
                        eventContent,
                        id,
                        messageItem.uuid!!,
                        messageItem.actions,
                        messageItem.meta?.decode()?.let { it as Map<String,Any>? }
                    )
                } ?: error("Unable to read messages")
            }.catch {
                Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_HISTORY_DATA.message, it))
            }
        }


    fun sendText(
        text: String,
        meta: Map<String,Any>? = null,
        shouldStore: Boolean? = null,
        usePost: Boolean = false,
        ttl: Int? = null,
        mentionedUsers: Map<Int, MessageMentionedUser>? = null,
        referencedChannels: Map<Int, MessageReferencedChannel>? = null,
        textLinks: List<TextLink>? = null,
        quotedMessage: Message? = null,
        files: List<File>? = null
    ): PNFuture<PNPublishResult> {
        if (quotedMessage != null && quotedMessage.channelId != id) {
            return PubNubException("You cannot quote messages from other channels").asFuture()
        }
        files?.forEach {
            //chat.pubNub todo sendFile here once implemented
        }
        val newMeta = buildMap {
            meta?.let { putAll(it) }
            mentionedUsers?.let { put("mentionedUsers", PNDataEncoder.encode(it)!!) }
            referencedChannels?.let { put("referencedChannels", PNDataEncoder.encode(it)!!) }
            textLinks?.let { put("textLinks", PNDataEncoder.encode(it)!!) }
            quotedMessage?.let {
                put("quotedMessage", mapOf(
                    "timetoken" to it.timetoken,
                    "text" to it.text,
                    "userId" to it.userId
                ))
            }
        }
        return chat.publish(
            channelId = id,
            message = EventContent.TextMessageContent(text, null), //todo files
            meta = newMeta,
            shouldStore = shouldStore,
            usePost = usePost,
            ttl = ttl,
        ).then { publishResult: PNPublishResult ->
                //todo chat SDK seems to ignore results of emitting these events?
                try {
                    mentionedUsers?.forEach {
                        emitUserMention(it.value.id, publishResult.timetoken, text).async {}
                    }
                } catch (_: Exception) {
                    //todo log
                }
                publishResult
            }
        }


    fun invite(user: User) : PNFuture<Membership> {
        if (this.type == ChannelType.PUBLIC) {
            return PubNubException("Channel invites are not supported in Public chats.").asFuture()
        }
        return getMembers(filter = "uuid.id == '${user.id}'").thenAsync { channelMembers: MembersResponse ->
            if (channelMembers.members.isNotEmpty()) {
                return@thenAsync channelMembers.members.first().asFuture()
            } else {
                chat.pubNub.setMemberships(
                    channels = listOf(PNChannelMembership.Partial(this.id)),
                    uuid = user.id,
                    includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM,
                    includeCustom = true,
                    includeCount = true,
                    filter = "channel.id == '${this.id}"
                ).then { setMembershipsResult ->
                    Membership.fromMembershipDTO(chat, setMembershipsResult.data.first(), user)
                }.thenAsync { membership ->
                    chat.pubNub.time().thenAsync { time ->
                        membership.setLastReadMessageTimetoken(time.timetoken)
                    }
                }.alsoAsync {
                    chat.emitEvent(user.id, EventContent.Invite(this.type ?: ChannelType.UNKNOWN, this.id))
                }
            }
        }
    }

    fun getMembers(limit: Int? = null, page: PNPage? = null, filter: String? = null, sort: Collection<PNSortKey<PNMemberKey>> = listOf()): PNFuture<MembersResponse> {
        return chat.pubNub.getChannelMembers(
            this.id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCustom = true,
            includeCount = true,
            includeUUIDDetails = PNUUIDDetailsLevel.UUID_WITH_CUSTOM,
        ).then { it: PNMemberArrayResult ->
            MembersResponse(it.next, it.prev, it.totalCount!!, it.status, it.data.map {
                Membership.fromChannelMemberDTO(chat, it, this)
            }.toSet() )
        }
    }


    private fun emitUserMention(
        userId: String,
        timetoken: Long,
        text: String, //todo need to add push payload including this once push is implemented here
    ): PNFuture<PNPublishResult> {
        return chat.emitEvent(userId, EventContent.Mention(timetoken, id))
    }

    private fun timeoutElapsed(lastTypingSent: Instant, now: Instant): Boolean {
        return lastTypingSent < now - maxOf(chat.config.typingTimeout, MINIMAL_TYPING_INDICATOR_TIMEOUT)
    }

    private fun sendTypingSignal(value: Boolean): PNFuture<Unit> {
        return chat.emitEvent(
            channel = this.id,
            payload = EventContent.Typing(value)
        ).then { Unit }
    }

    internal fun setTypingSent(value: Instant) {
        typingSent = value
    }

    companion object {
        fun fromDTO(chat: Chat, channel: PNChannelMetadata): Channel {
            return Channel(chat,
                id = channel.id,
                name = channel.name,
                custom = channel.custom?.let { createCustomObject(it) },
                description = channel.description,
                updated = channel.updated,
                status = channel.status,
                type = ChannelType.parse(channel.type)
            )
        }
    }
}

enum class ChannelType {
    DIRECT, GROUP, PUBLIC, UNKNOWN;
    companion object {
        fun parse(type: String?): ChannelType {
            if (type == null) {
                return UNKNOWN
            }
            return try {
                valueOf(type.uppercase())
            } catch (e: Exception) {
                UNKNOWN
            }
        }
    }
}
