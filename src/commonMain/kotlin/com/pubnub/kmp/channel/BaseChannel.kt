package com.pubnub.kmp.channel

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
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.member.PNUUIDDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelDetailsLevel
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembership
import com.pubnub.api.models.consumer.objects.membership.PNChannelMembershipArrayResult
import com.pubnub.api.models.consumer.push.payload.PushPayloadHelper
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.subscriptions.SubscriptionOptions
import com.pubnub.internal.PNDataEncoder
import com.pubnub.kmp.Channel
import com.pubnub.kmp.Chat
import com.pubnub.kmp.ChatImpl.Companion.pinMessageToChannel
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.Event
import com.pubnub.kmp.INTERNAL_MODERATION_PREFIX
import com.pubnub.kmp.Membership
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
import com.pubnub.kmp.message.BaseMessage
import com.pubnub.kmp.message.MessageImpl
import com.pubnub.kmp.remember
import com.pubnub.kmp.restrictions.GetRestrictionsResponse
import com.pubnub.kmp.restrictions.Restriction
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import com.pubnub.kmp.types.ChannelType
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.types.File
import com.pubnub.kmp.types.GetFileItem
import com.pubnub.kmp.types.GetFilesResult
import com.pubnub.kmp.types.InputFile
import com.pubnub.kmp.types.JoinResult
import com.pubnub.kmp.types.MessageMentionedUsers
import com.pubnub.kmp.types.MessageReferencedChannel
import com.pubnub.kmp.types.TextLink
import com.pubnub.kmp.util.getPhraseToLookFor
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import tryLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val MINIMAL_TYPING_INDICATOR_TIMEOUT: Duration = 1.seconds

abstract class BaseChannel<C : Channel, M : Message>(
    internal open val chat: Chat,
    private val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val description: String? = null,
    override val updated: String? = null,
    override val status: String? = null,
    override val type: ChannelType? = null,
    val channelFactory: (Chat, PNChannelMetadata) -> C,
    val messageFactory: (Chat, PNFetchMessageItem, channelId: String) -> M,
) : Channel {
    private val suggestedMemberships = mutableMapOf<String, Set<Membership>>()
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

    override fun getHistory(
        // todo add paging in response
        startTimetoken: Long?,
        endTimetoken: Long?,
        count: Int?,
    ): PNFuture<List<M>> {
        return chat.pubNub.fetchMessages(
            listOf(id),
            PNBoundedPage(startTimetoken, endTimetoken, count),
            includeMessageActions = true,
            includeMeta = true
        ).then { pnFetchMessagesResult: PNFetchMessagesResult ->
            pnFetchMessagesResult.channels[id]?.map { messageItem: PNFetchMessageItem ->
                messageFactory(chat, messageItem, id)
            } ?: error("Unable to read messages")
        }.catch {
            Result.failure(PubNubException(PubNubErrorMessage.FAILED_TO_RETRIEVE_HISTORY_DATA.message, it))
        }
    }

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
    ): PNFuture<PNPublishResult> {
        if (quotedMessage != null && quotedMessage.channelId != id) {
            return PubNubException("You cannot quote messages from other channels").asFuture()
        }
        return sendFilesForPublish(files).thenAsync { filesData ->
            val newMeta = buildMetaForPublish(meta, mentionedUsers, referencedChannels, textLinks, quotedMessage)
            chat.publish(
                channelId = id,
                message = EventContent.TextMessageContent(text, filesData), //todo files
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
    }

    private fun sendFilesForPublish(files: List<InputFile>?) =
        (files ?: emptyList()).map { file ->
            chat.pubNub.sendFile(id, file.name, file.source, shouldStore = false).thenAsync { sendFileResult ->
                chat.pubNub.getFileUrl(id, sendFileResult.file.name, sendFileResult.file.id).then {
                    File(sendFileResult.file.name, sendFileResult.file.id, it.url, file.type)
                }
            }
        }.awaitAll()

    private fun buildMetaForPublish(
        meta: Map<String, Any>?,
        mentionedUsers: MessageMentionedUsers?,
        referencedChannels: Map<Int, MessageReferencedChannel>?,
        textLinks: List<TextLink>?,
        quotedMessage: Message?,
    ): Map<String, Any> = buildMap {
        meta?.let { putAll(it) }
        mentionedUsers?.let { put("mentionedUsers", PNDataEncoder.encode(it)!!) }
        referencedChannels?.let { put("referencedChannels", PNDataEncoder.encode(it)!!) }
        textLinks?.let { put("textLinks", PNDataEncoder.encode(it)!!) }
        quotedMessage?.let {
            put(
                "quotedMessage",
                PNDataEncoder.encode((quotedMessage as BaseMessage<*>).asQuotedMessage())!!
            )
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
        sort: Collection<PNSortKey<PNMemberKey>>,
    ): PNFuture<MembersResponse> {
        return chat.pubNub.getChannelMembers(
            channel = this.id,
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
                    callback(MessageImpl.fromDTO(chat, pnMessageResult))
                } catch (e: Exception) {
                    e.printStackTrace() //todo add logging
                }
            },
        )
        subscription.addListener(listener)
        subscription.subscribe()
        return subscription
    }

    override fun join(custom: CustomObject?, callback: (Message) -> Unit): PNFuture<JoinResult> {
        val user = this.chat.currentUser
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
            val resultDisconnect =
                disconnect ?: connect(callback) //todo assign disconnect = resultDisconnect in subsequent line?
            chat.pubNub.time().thenAsync { time: PNTimeResult ->
                Membership.fromMembershipDTO(chat, membershipArray.data.first(), user)
                    .setLastReadMessageTimetoken(time.timetoken)
            }.then { membership: Membership ->
                JoinResult(
                    membership,
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
        val pinnedMessageTimetoken = this.custom?.get("pinnedMessageTimetoken").tryLong() ?: return null.asFuture()
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

    override fun pinMessage(message: Message): PNFuture<C> {
        return pinMessageToChannel(chat.pubNub, message, this).then { channelFactory(chat, it.data!!) }
    }

    override fun unpinMessage(): PNFuture<C> {
        return pinMessageToChannel(chat.pubNub, null, this).then { channelFactory(chat, it.data!!) }
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
        reason: String?,
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

    override fun streamReadReceipts(callback: (receipts: Map<String, List<String>>) -> Unit): AutoCloseable {
        if (type == ChannelType.PUBLIC) {
            throw PubNubException("Read receipts are not supported in Public chats.")
        }
        val timetokensPerUser = mutableMapOf<String, Long>()
        val future = getMembers().then { members -> //what about paging?
            members.members.forEach { m ->
                val lastTimetoken = m.custom?.get("lastReadMessageTimetoken")?.tryLong()
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
                            when (event.event) {
                                "join" -> {
                                    ids.add(event.uuid!!)
                                }

                                "leave", "timeout" -> {
                                    ids.remove(event.uuid)
                                }
                            }
                            callback(ids.toSet())
                        })
                )
                subscription.subscribe()
            }
        }.remember()

        return AutoCloseable {
            future.then { it.close() }
        }
    }

    // todo rename to getMembershipSuggestions?
    override fun getUserSuggestions(text: String, limit: Int): PNFuture<Set<Membership>> {
        val cacheKey: String = getPhraseToLookFor(text, "@") ?: return emptySet<Membership>().asFuture()

        suggestedMemberships[cacheKey]?.let { nonNullMemberships ->
            return nonNullMemberships.asFuture()
        }

        return getMembers(filter = "uuid.name LIKE '${cacheKey}*'", limit = limit).then { membersResponse ->
            val memberships = membersResponse.members
            suggestedMemberships[cacheKey] = memberships
            memberships
        }
    }

    private fun generateReceipts(timetokensPerUser: Map<String, Long>): Map<String, MutableList<String>> {
        return buildMap<String, MutableList<String>> {
            timetokensPerUser.forEach {
                val list = this.getOrPut(it.value.toString()) { mutableListOf() }
                list += it.key
            }
        }
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
        text: String,
    ): PNFuture<PNPublishResult> {
        return chat.emitEvent(userId, EventContent.Mention(timetoken, id), getPushPayload(text))
    }

    private fun timeoutElapsed(lastTypingSent: Instant, now: Instant): Boolean {
        return lastTypingSent < now - maxOf(chat.config.typingTimeout, MINIMAL_TYPING_INDICATOR_TIMEOUT)
    }

    private fun sendTypingSignal(value: Boolean): PNFuture<Unit> {
        return chat.emitEvent(
            channel = this.id,
            payload = EventContent.Typing(value),
        ).then { }
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

    internal abstract fun copyWithStatusDeleted(): C

    private fun getPushPayload(text: String): Map<String, Any> {
        val pushConfig = chat.config.pushNotifications
        val apnsTopic = pushConfig.apnsTopic
        val apnsEnv = pushConfig.apnsEnvironment
        if (!pushConfig.sendPushes) {
            return emptyMap()
        }
        val title = chat.currentUser.name ?: chat.currentUser.id
        val pushBuilder = PushPayloadHelper()
        pushBuilder.fcmPayloadV2 = PushPayloadHelper.FCMPayloadV2().apply {
            notification = PushPayloadHelper.FCMPayloadV2.Notification().apply {
                this.title = title
                this.body = text
            }
            data = buildMap {
                name?.let { put("subtitle", it) }
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
                apns2Configurations = listOf(PushPayloadHelper.APNSPayload.APNS2Configuration().apply {
                    this.targets = listOf(PushPayloadHelper.APNSPayload.APNS2Configuration.Target().apply {
                        this.topic = apnsTopic
                        this.environment = apnsEnv
                    })
                })
                name?.let { custom = mapOf("subtitle" to it) }
            }
        }

        return pushBuilder.build()
    }
}
