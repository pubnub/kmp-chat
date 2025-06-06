package com.pubnub.chat.internal.channel

import co.touchlab.kermit.Logger
import com.pubnub.api.PubNubException
import com.pubnub.api.endpoints.objects.member.GetChannelMembers
import com.pubnub.api.models.consumer.PNBoundedPage
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.PNTimeResult
import com.pubnub.api.models.consumer.files.PNDeleteFileResult
import com.pubnub.api.models.consumer.files.PNFileUrlResult
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.api.models.consumer.history.PNFetchMessagesResult
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.member.MemberInclude
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.membership.MembershipInclude
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.pubsub.objects.PNDeleteChannelMetadataEventMessage
import com.pubnub.api.models.consumer.pubsub.objects.PNObjectEventResult
import com.pubnub.api.models.consumer.pubsub.objects.PNSetChannelMetadataEventMessage
import com.pubnub.api.models.consumer.push.payload.PushPayloadHelper
import com.pubnub.api.utils.Clock
import com.pubnub.api.utils.Instant
import com.pubnub.api.utils.PatchValue
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.subscriptions.SubscriptionOptions
import com.pubnub.chat.Channel
import com.pubnub.chat.Event
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.config.PushNotificationsConfig
import com.pubnub.chat.internal.ChatImpl.Companion.pinOrUnpinMessageToChannel
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.INTERNAL_MODERATION_PREFIX
import com.pubnub.chat.internal.METADATA_LAST_READ_MESSAGE_TIMETOKEN
import com.pubnub.chat.internal.METADATA_MENTIONED_USERS
import com.pubnub.chat.internal.METADATA_QUOTED_MESSAGE
import com.pubnub.chat.internal.METADATA_REFERENCED_CHANNELS
import com.pubnub.chat.internal.METADATA_TEXT_LINKS
import com.pubnub.chat.internal.MINIMAL_TYPING_INDICATOR_TIMEOUT
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.PINNED_MESSAGE_CHANNEL_ID
import com.pubnub.chat.internal.PINNED_MESSAGE_TIMETOKEN
import com.pubnub.chat.internal.defaultGetMessageResponseBody
import com.pubnub.chat.internal.error.PubNubErrorMessage.CANNOT_QUOTE_MESSAGE_FROM_OTHER_CHANNELS
import com.pubnub.chat.internal.error.PubNubErrorMessage.CAN_NOT_STREAM_CHANNEL_UPDATES_ON_EMPTY_LIST
import com.pubnub.chat.internal.error.PubNubErrorMessage.ERROR_HANDLING_ONMESSAGE_EVENT
import com.pubnub.chat.internal.error.PubNubErrorMessage.FAILED_TO_RETRIEVE_HISTORY_DATA
import com.pubnub.chat.internal.error.PubNubErrorMessage.MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY
import com.pubnub.chat.internal.error.PubNubErrorMessage.READ_RECEIPTS_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.chat.internal.error.PubNubErrorMessage.THREAD_CHANNEL_DOES_NOT_EXISTS
import com.pubnub.chat.internal.error.PubNubErrorMessage.TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.internal.restrictions.RestrictionImpl
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.internal.util.channelsUrlDecoded
import com.pubnub.chat.internal.util.logErrorAndReturnException
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.internal.utils.ExponentialRateLimiter
import com.pubnub.chat.internal.uuidFilterString
import com.pubnub.chat.listenForEvents
import com.pubnub.chat.membership.MembersResponse
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.File
import com.pubnub.chat.types.GetEventsHistoryResult
import com.pubnub.chat.types.GetFileItem
import com.pubnub.chat.types.GetFilesResult
import com.pubnub.chat.types.HistoryResponse
import com.pubnub.chat.types.InputFile
import com.pubnub.chat.types.JoinResult
import com.pubnub.chat.types.MessageMentionedUsers
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.chat.types.MessageReferencedChannels
import com.pubnub.chat.types.TextLink
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.catch
import com.pubnub.kmp.createEventListener
import com.pubnub.kmp.remember
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import encodeForSending
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import tryLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

abstract class BaseChannel<C : Channel, M : Message>(
    override val chat: ChatInternal,
    private val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val description: String? = null,
    override val updated: String? = null,
    override val status: String? = null,
    override val type: ChannelType? = null,
    val channelFactory: (ChatInternal, PNChannelMetadata) -> C,
    val messageFactory: (ChatInternal, PNFetchMessageItem, channelId: String) -> M,
) : Channel {
    private val suggestedMemberships = mutableMapOf<String, List<Membership>>()
    internal var typingSent: Instant? = null
    private val sendTextRateLimiter by lazy {
        ExponentialRateLimiter(
            type?.let { typeNotNull -> chat.config.rateLimitPerChannel[typeNotNull] } ?: Duration.ZERO,
            chat.config.rateLimitFactor,
            chat.timerManager
        )
    }
    private val channelFilterString get() = "channel.id == '${this.id}'"
    private val typingTimeout get() = maxOf(chat.config.typingTimeout, MINIMAL_TYPING_INDICATOR_TIMEOUT)
    private val typingTimoutMargin = 500.milliseconds // sendTypingSignal 500 millis before typingTimeout expires to ensure continuity

    override fun update(
        name: String?,
        custom: CustomObject?,
        description: String?,
        status: String?,
        type: ChannelType?,
    ): PNFuture<Channel> {
        return chat.updateChannel(id, name, custom, description, status, type)
    }

    override fun delete(soft: Boolean): PNFuture<Channel?> {
        return chat.deleteChannel(id, soft)
    }

    override fun forwardMessage(message: Message): PNFuture<PNPublishResult> {
        return chat.forwardMessage(message, this.id)
    }

    override fun startTyping(): PNFuture<PNPublishResult?> {
        if (type == ChannelType.PUBLIC) {
            return log.logErrorAndReturnException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS).asFuture()
        }
        val now = clock.now()

        typingSent?.let { typingSentNotNull: Instant ->
            if (!timeoutElapsed(typingTimeout - typingTimoutMargin, typingSentNotNull, now)) {
                return null.asFuture()
            }
        }

        typingSent = now
        return sendTypingSignal(true)
    }

    override fun stopTyping(): PNFuture<PNPublishResult?> {
        if (type == ChannelType.PUBLIC) {
            return log.logErrorAndReturnException(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS).asFuture()
        }

        typingSent?.let { typingSentNotNull: Instant ->
            val now = clock.now()
            if (timeoutElapsed(typingTimeout, typingSentNotNull, now)) {
                return null.asFuture()
            }
        } ?: return null.asFuture()

        typingSent = null
        return sendTypingSignal(false)
    }

    override fun getTyping(callback: (typingUserIds: Collection<String>) -> Unit): AutoCloseable {
        val typingIndicators = mutableMapOf<String, Instant>()
        val typingIndicatorsLock = reentrantLock()
        val atomicClosed = atomic(false)
        var closed: Boolean by atomicClosed
        if (type == ChannelType.PUBLIC) {
            log.pnError(TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS)
        }

        return chat.listenForEvents(this.id) { event: Event<EventContent.Typing> ->
            if (event.channelId != id || closed) {
                return@listenForEvents
            }
            val now = clock.now()
            val userId = event.userId
            val isTyping = event.payload.value

            if (isTyping) {
                chat.timerManager.runWithDelay(typingTimeout + 10.milliseconds) { // +10ms just to make sure the timeout expires
                    if (closed) {
                        return@runWithDelay
                    }
                    typingIndicatorsLock.withLock {
                        removeExpiredTypingIndicators(typingTimeout, typingIndicators, clock.now())
                        typingIndicators.keys.toList()
                    }.also { typingIndicatorsList ->
                        callback(typingIndicatorsList)
                    }
                }
            }

            typingIndicatorsLock.withLock {
                updateUserTypingStatus(userId, isTyping, now, typingIndicators)
                removeExpiredTypingIndicators(typingTimeout, typingIndicators, now)
                typingIndicators.keys.toList()
            }.also { typingIndicatorsList ->
                callback(typingIndicatorsList)
            }
        }.let { autoCloseable ->
            object : AutoCloseable {
                override fun close() {
                    autoCloseable.close()
                    closed = true
                }
            }
        }
    }

    override fun whoIsPresent(): PNFuture<Collection<String>> {
        return chat.whoIsPresent(id)
    }

    override fun isPresent(userId: String): PNFuture<Boolean> {
        return chat.isPresent(userId, id)
    }

    override fun streamUpdates(callback: (channel: Channel?) -> Unit): AutoCloseable {
        return streamUpdatesOn(listOf(this)) {
            callback(it.firstOrNull())
        }
    }

    override fun getHistory(
        startTimetoken: Long?,
        endTimetoken: Long?,
        count: Int,
    ): PNFuture<HistoryResponse<M>> {
        return getHistory(
            chat = chat,
            channelId = id,
            messageFactory = messageFactory,
            startTimetoken = startTimetoken,
            endTimetoken = endTimetoken,
            count = count
        )
    }

    @Deprecated("Will be removed from SDK in the future", level = DeprecationLevel.WARNING)
    override fun sendText(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?,
        mentionedUsers: MessageMentionedUsers?,
        referencedChannels: Map<Int, MessageReferencedChannel>?,
        textLinks: List<TextLink>?,
        quotedMessage: Message?,
        files: List<InputFile>?,
        customPushData: Map<String, String>?,
    ): PNFuture<PNPublishResult> {
        val newMeta = buildMetaForPublish(meta, quotedMessage, mentionedUsers, referencedChannels, textLinks)
        return sendTextInternal(text, newMeta, shouldStore, usePost, ttl, quotedMessage, files, mentionedUsers?.map { it.value.id }, customPushData)
    }

    override fun sendText(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?,
        quotedMessage: Message?,
        files: List<InputFile>?,
        usersToMention: Collection<String>?,
        customPushData: Map<String, String>?,
    ): PNFuture<PNPublishResult> {
        return sendTextInternal(
            text = text,
            meta = buildMetaForPublish(meta, quotedMessage),
            shouldStore = shouldStore,
            usePost = usePost,
            ttl = ttl,
            quotedMessage = quotedMessage,
            files = files,
            usersToMention = usersToMention,
            customPushData = customPushData,
        )
    }

    private fun sendTextInternal(
        text: String,
        meta: Map<String, Any>?,
        shouldStore: Boolean,
        usePost: Boolean,
        ttl: Int?,
        quotedMessage: Message?,
        files: List<InputFile>?,
        usersToMention: Collection<String>? = null,
        customPushData: Map<String, String>? = null,
    ): PNFuture<PNPublishResult> {
        if (quotedMessage != null && quotedMessage.channelId != id) {
            return log.logErrorAndReturnException(CANNOT_QUOTE_MESSAGE_FROM_OTHER_CHANNELS).asFuture()
        }
        return sendTextRateLimiter.runWithinLimits(
            sendFilesForPublish(files).thenAsync { filesData ->
                chat.pubNub.publish(
                    channel = id,
                    message = EventContent.TextMessageContent(text, filesData).encodeForSending(
                        id,
                        chat.config.customPayloads?.getMessagePublishBody,
                        getPushPayload(this, text, chat.config.pushNotifications, customPushData)
                    ),
                    meta = meta,
                    shouldStore = shouldStore,
                    usePost = usePost,
                    ttl = ttl,
                )
            }.then { publishResult: PNPublishResult ->
                usersToMention?.forEach { mentionedUser ->
                    emitUserMention(mentionedUser, publishResult.timetoken, text, customPushData).async {
                        it.onFailure { ex ->
                            log.w(throwable = ex) { ex.message.orEmpty() }
                        }
                    }
                }
                publishResult
            }
        )
    }

    private fun sendFilesForPublish(files: List<InputFile>?): PNFuture<List<File>> =
        (files ?: emptyList()).map { file ->
            chat.pubNub.sendFile(id, file.name, file.source, shouldStore = false).thenAsync { sendFileResult ->
                chat.pubNub.getFileUrl(id, sendFileResult.file.name, sendFileResult.file.id).then {
                    File(sendFileResult.file.name, sendFileResult.file.id, it.url, file.type)
                }
            }
        }.awaitAll()

    private fun buildMetaForPublish(
        meta: Map<String, Any>?,
        quotedMessage: Message?,
        mentionedUsers: MessageMentionedUsers? = null,
        referencedChannels: MessageReferencedChannels? = null,
        textLinks: List<TextLink>? = null,
    ): Map<String, Any> = buildMap {
        meta?.let { putAll(it) }
        quotedMessage?.let {
            put(
                METADATA_QUOTED_MESSAGE,
                PNDataEncoder.encode((quotedMessage as BaseMessage<*>).asQuotedMessage())!!
            )
        }
        mentionedUsers?.let { put(METADATA_MENTIONED_USERS, PNDataEncoder.encode(it)!!) }
        referencedChannels?.let { put(METADATA_REFERENCED_CHANNELS, PNDataEncoder.encode(it)!!) }
        textLinks?.let { put(METADATA_TEXT_LINKS, PNDataEncoder.encode(it)!!) }
    }

    override fun invite(user: User): PNFuture<Membership> {
        return getMembers(filter = user.uuidFilterString).thenAsync { channelMembers: MembersResponse ->
            if (channelMembers.members.isNotEmpty()) {
                return@thenAsync channelMembers.members.first().asFuture()
            } else {
                chat.pubNub.setMemberships(
                    channels = listOf(PNChannelMembership.Partial(this.id)),
                    userId = user.id,
                    filter = channelFilterString,
                    include = MembershipInclude(
                        includeCustom = true,
                        includeStatus = false,
                        includeType = false,
                        includeTotalCount = true,
                        includeChannel = true,
                        includeChannelCustom = true,
                        includeChannelType = true,
                        includeChannelStatus = false
                    )
                ).then { setMembershipsResult ->
                    MembershipImpl.fromMembershipDTO(chat, setMembershipsResult.data.first(), user)
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
        return chat.pubNub.setChannelMembers(
            this.id,
            users.map { PNMember.Partial(it.id) },
            include = MemberInclude(
                includeCustom = true,
                includeStatus = false,
                includeType = false,
                includeTotalCount = true,
                includeUser = true,
                includeUserCustom = true,
                includeUserType = true,
                includeUserStatus = false
            ),
            filter = users.joinToString(" || ") { it.uuidFilterString }
        ).thenAsync { memberArrayResult: PNMemberArrayResult ->
            chat.pubNub.time().thenAsync { time: PNTimeResult ->
                val futures: List<PNFuture<Membership>> = memberArrayResult.data.map {
                    MembershipImpl.fromChannelMemberDTO(chat, it, this).setLastReadMessageTimetoken(time.timetoken)
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
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<MembersResponse> {
        return chat.pubNub.getChannelMembers(
            channel = this.id,
            limit = limit,
            page = page,
            filter = filter,
            sort = sort,
            include = MemberInclude(
                includeCustom = true,
                includeStatus = false,
                includeType = false,
                includeTotalCount = true,
                includeUser = true,
                includeUserCustom = true,
                includeUserType = true,
                includeUserStatus = false
            ),
        ).then { it: PNMemberArrayResult ->
            MembersResponse(
                it.next,
                it.prev,
                it.totalCount!!,
                it.status,
                it.data.map {
                    MembershipImpl.fromChannelMemberDTO(chat, it, this)
                }
            )
        }
    }

    override fun connect(callback: (Message) -> Unit): AutoCloseable {
        val channelEntity = chat.pubNub.channel(id)
        val subscription = channelEntity.subscription()
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

    override fun join(custom: CustomObject?, callback: ((Message) -> Unit)?): PNFuture<JoinResult> {
        val user = this.chat.currentUser
        return chat.pubNub.setMemberships(
            channels = listOf(
                PNChannelMembership.Partial(
                    this.id,
                    custom
                )
            ), // todo should null overwrite? Waiting for optionals?
            filter = channelFilterString,
            include = MembershipInclude(
                includeCustom = true,
                includeStatus = false,
                includeType = false,
                includeTotalCount = true,
                includeChannel = true,
                includeChannelCustom = true,
                includeChannelType = true,
                includeChannelStatus = false
            )
        ).thenAsync { membershipArray: PNChannelMembershipArrayResult ->
            val resultDisconnect = callback?.let { connect(it) }

            chat.pubNub.time().thenAsync { time: PNTimeResult ->
                MembershipImpl.fromMembershipDTO(chat, membershipArray.data.first(), user)
                    .setLastReadMessageTimetoken(time.timetoken)
            }.then { membership: Membership ->
                JoinResult(
                    membership,
                    resultDisconnect
                )
            }
        }
    }

    // there is a discrepancy between KMP and JS. There is no unsubscribe here. This is agreed and will be changed in JS Chat
    override fun leave(): PNFuture<Unit> = chat.pubNub.removeMemberships(channels = listOf(id), include = MembershipInclude()).then { Unit }

    override fun getPinnedMessage(): PNFuture<Message?> {
        val pinnedMessageTimetoken = this.custom?.get(PINNED_MESSAGE_TIMETOKEN).tryLong() ?: return null.asFuture()
        val pinnedMessageChannelID = this.custom?.get(PINNED_MESSAGE_CHANNEL_ID) as? String ?: return null.asFuture()

        if (pinnedMessageChannelID == this.id) {
            return getMessage(pinnedMessageTimetoken)
        }
        return this.chat.getChannel(pinnedMessageChannelID).thenAsync { threadChannel: Channel? ->
            if (threadChannel == null) {
                log.pnError(THREAD_CHANNEL_DOES_NOT_EXISTS)
            }
            threadChannel.getMessage(pinnedMessageTimetoken)
        }
    }

    override fun getMessage(timetoken: Long): PNFuture<Message?> {
        return getMessage(chat = chat, channelId = id, timetoken = timetoken)
    }

    override fun registerForPush() = chat.registerPushChannels(listOf(id))

    override fun unregisterFromPush() = chat.unregisterPushChannels(listOf(id))

    override fun pinMessage(message: Message): PNFuture<C> {
        return pinOrUnpinMessageToChannel(chat.pubNub, message, this).then { channelFactory(chat, it.data) }
    }

    override fun unpinMessage(): PNFuture<C> {
        return pinOrUnpinMessageToChannel(chat.pubNub, null, this).then { channelFactory(chat, it.data) }
    }

    override fun getUsersRestrictions(
        limit: Int?,
        page: PNPage?,
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<GetRestrictionsResponse> {
        val undefinedUser = null
        return getRestrictions(
            user = undefinedUser,
            limit = limit,
            page = page,
            sort = sort
        ).then { pnMemberArrayResult: PNMemberArrayResult ->
            val restrictions = pnMemberArrayResult.data.map { pnMember ->
                RestrictionImpl.fromMemberDTO(id, pnMember)
            }
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
            val userHasRestrictions = pnMemberArrayResult.data.isNotEmpty()
            val userRestrictions: Restriction = if (userHasRestrictions) {
                RestrictionImpl.fromMemberDTO(id, pnMemberArrayResult.data.first())
            } else {
                Restriction(userId = user.id, channelId = id)
            }
            userRestrictions
        }
    }

    override fun setRestrictions(
        user: User,
        ban: Boolean,
        mute: Boolean,
        reason: String?,
    ): PNFuture<Unit> {
        if (chat.pubNub.configuration.secretKey.isEmpty()) {
            return log.logErrorAndReturnException(MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY).asFuture()
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

    override fun streamReadReceipts(callback: (receipts: Map<Long, List<String>>) -> Unit): AutoCloseable {
        if (type == ChannelType.PUBLIC) {
            log.pnError(READ_RECEIPTS_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS)
        }
        val timetokensPerUser = mutableMapOf<String, Long>()
        // in group chats it work till 100 members
        val future = getMembers().then { members ->
            members.members.forEach { m ->
                val lastTimetoken = m.custom?.get(METADATA_LAST_READ_MESSAGE_TIMETOKEN)?.tryLong()
                if (lastTimetoken != null) {
                    timetokensPerUser[m.user.id] = lastTimetoken
                }
            }
            callback(generateReceipts(timetokensPerUser))
        }.then {
            chat.listenForEvents<EventContent.Receipt>(id) { event ->
                timetokensPerUser[event.userId] = event.payload.messageTimetoken
                callback(generateReceipts(timetokensPerUser))
            }
        }.remember()
        return AutoCloseable {
            future.async {
                it.onSuccess { subscription ->
                    subscription.close()
                }
            }
        }
    }

    override fun getFiles(limit: Int, next: String?): PNFuture<GetFilesResult> {
        return chat.pubNub.listFiles(id, limit, next?.let { PNPage.PNNext(it) }).thenAsync { listFilesResult ->
            val filesList = listFilesResult.data.toList()
            filesList.map {
                chat.pubNub.getFileUrl(id, it.name, it.id)
            }.awaitAll().then { it: List<PNFileUrlResult> ->
                val fileItems = filesList.zip(it).map {
                    GetFileItem(it.first.name, it.first.id, it.second.url)
                }
                GetFilesResult(fileItems, listFilesResult.next?.pageHash, listFilesResult.count)
            }
        }
    }

    override fun deleteFile(id: String, name: String): PNFuture<PNDeleteFileResult> {
        return chat.pubNub.deleteFile(id, name, id)
    }

    override fun streamPresence(callback: (userIds: Collection<String>) -> Unit): AutoCloseable {
        val ids = mutableSetOf<String>()
        val future = whoIsPresent().then {
            ids.addAll(it)
            callback(ids.toSet())
        }.then {
            chat.pubNub.channel(id).subscription(SubscriptionOptions.receivePresenceEvents()).also { subscription ->
                subscription.addListener(
                    createEventListener(
                        chat.pubNub,
                        onPresence = { _, event ->
                            event.leave?.let { ids.removeAll(it.toSet()) }
                            event.timeout?.let { ids.removeAll(it.toSet()) }
                            event.join?.let { ids.addAll(it) }

                            when (event.event) {
                                "join" -> {
                                    ids.add(event.uuid!!)
                                }
                                "leave", "timeout" -> {
                                    ids.remove(event.uuid!!)
                                }
                            }
                            callback(ids.toSet())
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

    override fun getUserSuggestions(text: String, limit: Int): PNFuture<List<Membership>> {
        suggestedMemberships[text]?.let { nonNullMemberships ->
            return nonNullMemberships.asFuture()
        }

        return getMembers(filter = "uuid.name LIKE '$text*'", limit = limit).then { membersResponse ->
            val memberships = membersResponse.members
            suggestedMemberships[text] = memberships
            memberships
        }
    }

    override fun getMessageReportsHistory(
        startTimetoken: Long?,
        endTimetoken: Long?,
        count: Int
    ): PNFuture<GetEventsHistoryResult> {
        val channelId = "${INTERNAL_MODERATION_PREFIX}${this.id}"
        return chat.getEventsHistory(
            channelId = channelId,
            startTimetoken = startTimetoken,
            endTimetoken = endTimetoken,
            count = count
        )
    }

    override fun streamMessageReports(callback: (event: Event<EventContent.Report>) -> Unit): AutoCloseable {
        val channelId = "${INTERNAL_MODERATION_PREFIX}$id"
        return chat.listenForEvents<EventContent.Report>(channelId = channelId, callback = callback)
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
            include = MemberInclude(
                includeCustom = true,
                includeStatus = false,
                includeType = false,
                includeTotalCount = true,
                includeUser = true,
                includeUserCustom = true,
                includeUserType = true,
                includeUserStatus = false
            ),
        )
    }

    open fun emitUserMention(
        userId: String,
        timetoken: Long,
        text: String,
        customPushData: Map<String, String>? = null,
    ): PNFuture<PNPublishResult> {
        return chat.emitEvent(
            userId,
            EventContent.Mention(timetoken, id),
            getPushPayload(this, text, chat.config.pushNotifications, customPushData)
        )
    }

    override operator fun plus(update: PNChannelMetadata): Channel {
        return channelFactory(chat, toPNChannelMetadata() + update)
    }

    private fun sendTypingSignal(value: Boolean): PNFuture<PNPublishResult> {
        return chat.emitEvent(
            channelId = this.id,
            payload = EventContent.Typing(value),
        )
    }

    internal abstract fun copyWithStatusDeleted(): C

    private fun toPNChannelMetadata(): PNChannelMetadata {
        return PNChannelMetadata(
            id = id,
            name = name?.let { PatchValue.of(it) },
            description = description?.let { PatchValue.of(it) },
            custom = custom?.let { PatchValue.of(custom) },
            updated = updated?.let { PatchValue.of(it) },
            eTag = null,
            type = type?.let { PatchValue.of(it.stringValue) },
            status = status?.let { PatchValue.of(it) }
        )
    }

    companion object {
        private val log = Logger.withTag("BaseChannel")

        fun <M : Message> getHistory(
            chat: ChatInternal,
            channelId: String,
            messageFactory: (ChatInternal, PNFetchMessageItem, channelId: String) -> M,
            startTimetoken: Long?,
            endTimetoken: Long?,
            count: Int,
        ): PNFuture<HistoryResponse<M>> {
            return chat.pubNub.fetchMessages(
                listOf(channelId),
                PNBoundedPage(startTimetoken, endTimetoken, count),
                includeMessageActions = true,
                includeMeta = true
            ).then { pnFetchMessagesResult: PNFetchMessagesResult ->
                HistoryResponse(
                    messages = pnFetchMessagesResult.channelsUrlDecoded[channelId]?.mapNotNull { messageItem: PNFetchMessageItem ->
                        if (messageItem.uuid in chat.mutedUsersManager.mutedUsers) {
                            null
                        } else {
                            messageFactory(chat, messageItem, channelId)
                        }
                    } ?: emptyList(),
                    isMore = pnFetchMessagesResult.channelsUrlDecoded[channelId]?.size == count
                )
            }.catch {
                Result.failure(PubNubException(FAILED_TO_RETRIEVE_HISTORY_DATA, it))
            }
        }

        fun getMessage(chat: ChatInternal, channelId: String, timetoken: Long): PNFuture<Message?> {
            val previousTimetoken = timetoken + 1
            return getHistory(
                chat = chat,
                channelId = channelId,
                messageFactory = MessageImpl::fromDTO,
                startTimetoken = previousTimetoken,
                endTimetoken = timetoken,
                count = 1
            ).then {
                it.messages.firstOrNull()
            }
        }

        fun streamUpdatesOn(
            channels: Collection<Channel>,
            callback: (channels: Collection<Channel>) -> Unit
        ): AutoCloseable {
            if (channels.isEmpty()) {
                log.pnError(CAN_NOT_STREAM_CHANNEL_UPDATES_ON_EMPTY_LIST)
            }
            var latestChannels = channels
            val chat = channels.first().chat as ChatInternal
            val listener = createEventListener(chat.pubNub, onObjects = { _, event: PNObjectEventResult ->
                val (newChannel, newChannelId) = when (val message = event.extractedMessage) {
                    is PNSetChannelMetadataEventMessage -> {
                        val newChannelId = message.data.id
                        val previousChannel = latestChannels.firstOrNull { it.id == newChannelId }
                        val newChannel = previousChannel?.plus(message.data) ?: ChannelImpl.fromDTO(chat, message.data)
                        newChannel to newChannelId
                    }

                    is PNDeleteChannelMetadataEventMessage -> null to message.channel
                    else -> return@createEventListener
                }

                latestChannels = latestChannels.asSequence().filter { channel ->
                    channel.id != newChannelId
                }.let { sequence ->
                    if (newChannel != null) {
                        sequence + newChannel
                    } else {
                        sequence
                    }
                }.toList()
                callback(latestChannels)
            })

            val subscriptionSet = chat.pubNub.subscriptionSetOf(channels.map { it.id }.toSet())
            subscriptionSet.addListener(listener)
            subscriptionSet.subscribe()
            return subscriptionSet
        }

        internal fun getPushPayload(
            baseChannel: BaseChannel<*, *>,
            text: String,
            pushConfig: PushNotificationsConfig,
            customPushData: Map<String, String>? = null
        ): Map<String, Any> {
            val apnsTopic = pushConfig.apnsTopic
            val apnsEnv = pushConfig.apnsEnvironment
            if (!pushConfig.sendPushes) {
                return emptyMap()
            }
            val title = baseChannel.chat.currentUser.name ?: baseChannel.chat.currentUser.id
            val pushBuilder = PushPayloadHelper()
            pushBuilder.fcmPayloadV2 = PushPayloadHelper.FCMPayloadV2().apply {
                notification = PushPayloadHelper.FCMPayloadV2.Notification().apply {
                    this.title = title
                    this.body = text
                }
                data = buildMap {
                    baseChannel.name?.let { put("subtitle", it) }
                    customPushData?.let { putAll(it) }
                }
                this.android = PushPayloadHelper.FCMPayloadV2.AndroidConfig().apply {
                    this.notification = PushPayloadHelper.FCMPayloadV2.AndroidConfig.AndroidNotification().apply {
                        this.sound = "default"
                        this.title = title
                        this.body = text
                    }
                }
            }
            if (apnsTopic != null) {
                pushBuilder.apnsPayload = PushPayloadHelper.APNSPayload().apply {
                    this.aps = PushPayloadHelper.APNSPayload.APS().apply {
                        this.alert = mapOf(
                            "title" to title,
                            "body" to text
                        )
                        this.sound = "default"
                    }
                    apns2Configurations = listOf(
                        PushPayloadHelper.APNSPayload.APNS2Configuration().apply {
                            this.authMethod = PushPayloadHelper.APNSPayload.APNS2Configuration.APNS2AuthMethod.TOKEN
                            this.version = "v2"
                            this.targets = listOf(
                                PushPayloadHelper.APNSPayload.APNS2Configuration.Target().apply {
                                    this.topic = apnsTopic
                                    this.environment = apnsEnv
                                }
                            )
                        }
                    )
                    custom = buildMap {
                        baseChannel.name?.let { put("subtitle", it) }
                        customPushData?.let { putAll(it) }
                    }
                }
            }

            return pushBuilder.build()
        }

        internal fun generateReceipts(timetokensPerUser: Map<String, Long>): Map<Long, MutableList<String>> {
            return buildMap {
                timetokensPerUser.forEach {
                    val list = this.getOrPut(it.value) { mutableListOf() }
                    list += it.key
                }
            }
        }

        internal fun updateUserTypingStatus(
            userId: String,
            isTyping: Boolean,
            now: Instant,
            userLastTyped: MutableMap<String, Instant>
        ) {
            if (isTyping) {
                userLastTyped[userId] = now
            } else {
                userLastTyped.remove(userId)
            }
        }

        internal fun removeExpiredTypingIndicators(
            timeout: Duration,
            userLastTyped: MutableMap<String, Instant>,
            now: Instant
        ) {
            val iterator = userLastTyped.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (timeoutElapsed(timeout, entry.value, now)) {
                    iterator.remove()
                }
            }
        }

        private fun timeoutElapsed(timeout: Duration, lastTypingSent: Instant, now: Instant): Boolean {
            return lastTypingSent < now - timeout
        }
    }
}
