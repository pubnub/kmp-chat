package com.pubnub.kmp.channel

import com.pubnub.api.PubNubException
import com.pubnub.api.endpoints.objects.member.GetChannelMembers
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.PNTimeResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.member.PNUUIDDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.Channel
import com.pubnub.kmp.Chat
import com.pubnub.kmp.ChatImpl.Companion.pinMessageToChannel
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.Event
import com.pubnub.kmp.INTERNAL_MODERATION_PREFIX
import com.pubnub.kmp.Message
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.User
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.catch
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.error.PubNubErrorMessage
import com.pubnub.kmp.error.PubNubErrorMessage.MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY
import com.pubnub.kmp.error.PubNubErrorMessage.TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.kmp.listenForEvents
import com.pubnub.kmp.membership.MembersResponse
import com.pubnub.kmp.membership.Membership
import com.pubnub.kmp.restrictions.GetRestrictionsResponse
import com.pubnub.kmp.restrictions.Restriction
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import com.pubnub.kmp.types.ChannelType
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.JoinResult
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val MINIMAL_TYPING_INDICATOR_TIMEOUT: Duration = 1.seconds

abstract class BaseChannel(
    private val chat: Chat,
    private val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val description: String? = null,
    override val updated: String? = null,
    override val status: String? = null,
    override val type: ChannelType? = null,
) : Channel {
    private val suggestedNames = mutableMapOf<String, List<Membership>>()
    private var disconnect: AutoCloseable? = null
    private var typingSent: Instant? = null
    internal var typingIndicators = mutableMapOf<String, Instant>()
    private val sendTextRateLimiter: String? = null // todo should be ExponentialRateLimiter instead of String
    private val typingIndicatorsLock = reentrantLock()
    private val channelFilterString get() = "channel.id == '${this.id}'"

    override fun update(
        name: String?,
        custom: CustomObject?,
        description: String?,
        updated: String?,
        status: String?,
        type: ChannelType?,
    ): PNFuture<Channel> {
        return chat.updateChannel(id, name, custom, description, updated, status, type)
    }

    override fun delete(soft: Boolean): PNFuture<Channel> {
        return chat.deleteChannel(id, soft)
    }

    override fun forwardMessage(message: Message): PNFuture<PNPublishResult> {
        return chat.forwardMessage(message, this.id)
    }

    override fun startTyping(): PNFuture<Unit> {
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

    override fun stopTyping(): PNFuture<Unit> {
        if (type == ChannelType.PUBLIC) {
            return PubNubException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message).asFuture()
        }

        typingSent?.let { typingSentNotNull: Instant ->
            val now = clock.now()
            if (timeoutElapsed(typingSentNotNull, now)) {
                return Unit.asFuture()
            }
        } ?: return Unit.asFuture()

        typingSent = null
        return sendTypingSignal(false)
    }

    override fun getTyping(callback: (typingUserIds: Collection<String>) -> Unit): AutoCloseable {
        if (type == ChannelType.PUBLIC) {
            throw PubNubException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS.message)
        }

        return chat.listenForEvents(this.id) { event: Event<EventContent.Typing> ->
            if (event.channelId != id) {
                return@listenForEvents
            }
            val now = clock.now()
            val userId = event.userId
            val isTyping = event.payload.value

            typingIndicatorsLock.withLock {
                updateUserTypingStatus(userId, isTyping, now)
                removeExpiredTypingIndicators(now)
                typingIndicators.keys.toList()
            }.also { typingIndicatorsList ->
                callback(typingIndicatorsList)
            }
        }
    }

    override fun whoIsPresent(): PNFuture<Collection<String>> {
        return chat.whoIsPresent(id)
    }

    override fun isPresent(userId: String): PNFuture<Boolean> {
        return chat.isPresent(userId, id)
    }

    override fun getHistory( // todo add paging in response
        startTimetoken: Long?,
        endTimetoken: Long?,
        count: Int?
    ): PNFuture<List<Message>> {
        return chat.pubNub.fetchMessages(
            listOf(id),
            PNBoundedPage(startTimetoken, endTimetoken, count),
            includeMessageActions = true,
            includeMeta = true
        ).then { pnFetchMessagesResult: PNFetchMessagesResult ->
            pnFetchMessagesResult.channels[id]?.map { messageItem: PNFetchMessageItem ->
                Message.fromDTO(chat, messageItem, id)
            } ?: error("Unable to read messages")
        }.catch {
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_HISTORY_DATA.message, it))
        }
    }

    override fun sendText(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean?,
        usePost: Boolean,
        ttl: Int?,
        mentionedUsers: MessageMentionedUsers?,
        referencedChannels: Map<Int, MessageReferencedChannel>?,
        textLinks: List<TextLink>?,
        quotedMessage: Message?,
        files: List<File>?
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
                put(
                    "quotedMessage",
                    PNDataEncoder.encode(quotedMessage.asQuotedMessage())!!
                )
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


    override fun invite(user: User): PNFuture<Membership> {
        if (this.type == ChannelType.PUBLIC) {
            return PubNubException(PubNubErrorMessage.CHANNEL_INVITES_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS.message).asFuture()
        }
        return getMembers(filter = user.uuidFilterString).thenAsync { channelMembers: MembersResponse ->
            if (channelMembers.members.isNotEmpty()) {
                return@thenAsync channelMembers.members.first().asFuture()
            } else {
                chat.pubNub.setMemberships(
                    channels = listOf(PNChannelMembership.Partial(this.id)),
                    uuid = user.id,
                    includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM,
                    includeCustom = true,
                    includeCount = true,
                    includeType = true,
                    filter = channelFilterString,
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

    override fun inviteMultiple(users: Collection<User>): PNFuture<List<Membership>> {
        if (this.type == ChannelType.PUBLIC) {
            return PubNubException("Channel invites are not supported in Public chats.").asFuture()
        }
        return chat.pubNub.setChannelMembers(
            this.id,
            users.map { PNMember.Partial(it.id) },
            includeCustom = true,
            includeCount = true,
            includeType = true,
            includeUUIDDetails = PNUUIDDetailsLevel.UUID_WITH_CUSTOM,
            filter = users.joinToString(" || ") { it.uuidFilterString }
        ).thenAsync { memberArrayResult: PNMemberArrayResult ->
            chat.pubNub.time().thenAsync { time: PNTimeResult ->
                val futures: List<PNFuture<Membership>> = memberArrayResult.data.map {
                    Membership.fromChannelMemberDTO(chat, it, this).setLastReadMessageTimetoken(time.timetoken)
                }
                futures.awaitAll()
            }
        }.alsoAsync {
            users.map { u ->
                chat.emitEvent(u.id, EventContent.Invite(this.type ?: ChannelType.UNKNOWN, this.id))
            }.awaitAll()
        }
    }

    override fun getMembers(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMemberKey>>
    ): PNFuture<MembersResponse> {
        return chat.pubNub.getChannelMembers(
            this.id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            includeCustom = true,
            includeCount = true,
            includeType = true,
            includeUUIDDetails = PNUUIDDetailsLevel.UUID_WITH_CUSTOM,
        ).then { it: PNMemberArrayResult ->
            MembersResponse(it.next, it.prev, it.totalCount!!, it.status, it.data.map {
                Membership.fromChannelMemberDTO(chat, it, this)
            }.toSet())
        }
    }

    override fun connect(callback: (Message) -> Unit): AutoCloseable {
        val channelEntity = chat.pubNub.channel(id)
        val subscription = channelEntity.subscription()
        val listener = createEventListener(
            chat.pubNub,
            onMessage = { _, pnMessageResult ->
                try {
                    val eventContent: EventContent = PNDataEncoder.decode(pnMessageResult.message)
                    if (eventContent !is EventContent.TextMessageContent) {
                        return@createEventListener
                    }
                    callback(Message.fromDTO(chat, pnMessageResult))
                } catch (e: Exception) {
                    e.printStackTrace() //todo add logging
                }
            },
        )
        subscription.addListener(listener)
        subscription.subscribe()
        return object : AutoCloseable {
            override fun close() {
                subscription.removeListener(listener)
                subscription.unsubscribe()
            }
        }
    }

    override fun join(custom: CustomObject?, callback: (Message) -> Unit): PNFuture<JoinResult> {
        val user = this.chat.user
        return chat.pubNub.setMemberships(
            channels = listOf(
                PNChannelMembership.Partial(
                    this.id,
                    custom
                )
            ), //todo should null overwrite? wait for optionals?
            includeChannelDetails = PNChannelDetailsLevel.CHANNEL_WITH_CUSTOM,
            includeCustom = true,
            includeCount = true,
            includeType = true,
            filter = channelFilterString,
        ).thenAsync { membershipArray: PNChannelMembershipArrayResult ->
            val resultDisconnect = disconnect ?: connect(callback)
            chat.pubNub.time().thenAsync { time: PNTimeResult ->
                Membership.fromMembershipDTO(chat, membershipArray.data.first(), user)
                    .setLastReadMessageTimetoken(time.timetoken)
            }.then {
                JoinResult(
                    it,
                    resultDisconnect
                ) //todo the whole disconnect handling is not safe! state can be made inconsistent
            }
        }
    }

    override fun leave(): PNFuture<Unit> = PNFuture<Unit> {
        disconnect?.close()
        disconnect = null
    }.alsoAsync { chat.pubNub.removeMemberships(channels = listOf(id)) }

    override fun getPinnedMessage(): PNFuture<Message?> {
        val pinnedMessageTimetoken = this.custom?.get("pinnedMessageTimetoken") as? Long ?: return null.asFuture()
        val pinnedMessageChannelID = this.custom?.get("pinnedMessageChannelID") as? String ?: return null.asFuture()

        if (pinnedMessageChannelID == this.id) {
            return getMessage(pinnedMessageTimetoken)
        }
        return this.chat.getChannel(pinnedMessageChannelID).thenAsync { threadChannel: Channel? ->
            if (threadChannel == null) {
                error("The thread channel does not exist")
            }
            threadChannel.getMessage(pinnedMessageTimetoken)
        }
    }

    override fun getMessage(timetoken: Long): PNFuture<Message?> {
        val previousTimetoken = timetoken + 1
        return getHistory(previousTimetoken, timetoken).then {
            it.firstOrNull()
        }
    }

    override fun registerForPush() = chat.registerPushChannels(listOf(id))

    override fun unregisterFromPush() = chat.unregisterPushChannels(listOf(id))

    override fun pinMessage(message: Message): PNFuture<Channel> {
        return pinMessageToChannel(chat.pubNub, message, this).then { ChannelImpl.fromDTO(chat, it.data!!) }
    }

    override fun unpinMessage(): PNFuture<Channel> {
        return pinMessageToChannel(chat.pubNub, null, this).then { ChannelImpl.fromDTO(chat, it.data!!) }
    }

    override fun getUsersRestrictions(
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMemberKey>>
    ): PNFuture<GetRestrictionsResponse> {
        val undefinedUser = null
        return getRestrictions(
            user = undefinedUser,
            limit = limit,
            page = page,
            sort = sort
        ).then { pnMemberArrayResult: PNMemberArrayResult ->
            val restrictions = pnMemberArrayResult.data.map { pnMember ->
                Restriction.fromMemberDTO(id, pnMember)
            }.toSet()
            GetRestrictionsResponse(
                restrictions = restrictions,
                next = pnMemberArrayResult.next,
                prev = pnMemberArrayResult.prev,
                total = pnMemberArrayResult.totalCount ?: 0,
                status = pnMemberArrayResult.status
            )
        }
    }

    override fun getUserRestrictions(user: User): PNFuture<Restriction> {
        return getRestrictions(user).then { pnMemberArrayResult: PNMemberArrayResult ->
            val firstMember: PNMember = pnMemberArrayResult.data.first()
            Restriction.fromMemberDTO(id, firstMember)
        }
    }

    override fun setRestrictions(
        user: User,
        ban: Boolean,
        mute: Boolean,
        reason: String?
    ): PNFuture<Unit> {
        if (chat.config.pubnubConfig.secretKey.isEmpty()) {
            throw PubNubException(MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY.message)
        }
        return chat.setRestrictions(
            Restriction(
                userId = user.id,
                channelId = id,
                ban = ban,
                mute = mute,
                reason = reason
            )
        )
    }

    internal fun getRestrictions(
        user: User?,
        limit: Int? = null,
        page: PNPage? = null,
        sort: Collection<PNSortKey<PNMemberKey>> = listOf(),
    ): GetChannelMembers {
        return chat.pubNub.getChannelMembers(
            channel = "$INTERNAL_MODERATION_PREFIX$id",
            limit = limit,
            page = page,
            filter = user?.let { "uuid.id == '${user.id}'" },
            sort = sort,
            includeCount = true,
            includeCustom = true,
            includeUUIDDetails = PNUUIDDetailsLevel.UUID_WITH_CUSTOM,
            includeType = true
        )
    }

    private fun emitUserMention(
        userId: String,
        timetoken: Long,
        text: String, //todo need to add push payload once push is implemented
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

    internal fun updateUserTypingStatus(userId: String, isTyping: Boolean, now: Instant) {
        if (typingIndicators[userId] != null) {
            if (isTyping) {
                typingIndicators[userId] = now
            } else {
                typingIndicators.remove(userId)
            }
        } else {
            if (isTyping) {
                typingIndicators[userId] = now
            }
        }
    }

    internal fun removeExpiredTypingIndicators(now: Instant) {
        val iterator = typingIndicators.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (timeoutElapsed(entry.value, now)) {
                iterator.remove()
            }
        }
    }

    internal abstract fun copyWithStatusDeleted(): Channel
}