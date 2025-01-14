package com.pubnub.chat.internal.channel

import co.touchlab.kermit.Logger
import com.pubnub.api.models.consumer.PNTimeResult
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
import com.pubnub.api.utils.Clock
import com.pubnub.chat.Channel
import com.pubnub.chat.Membership
import com.pubnub.chat.Message
import com.pubnub.chat.User
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.DELETED
import com.pubnub.chat.internal.METADATA_LAST_READ_MESSAGE_TIMETOKEN
import com.pubnub.chat.internal.MembershipImpl
import com.pubnub.chat.internal.error.PubNubErrorMessage.CHANNEL_INVITES_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.chat.internal.error.PubNubErrorMessage.READ_RECEIPTS_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS
import com.pubnub.chat.internal.message.MessageImpl
import com.pubnub.chat.internal.util.logErrorAndReturnException
import com.pubnub.chat.internal.util.pnError
import com.pubnub.chat.internal.uuidFilterString
import com.pubnub.chat.listenForEvents
import com.pubnub.chat.membership.MembersResponse
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.JoinResult
import com.pubnub.kmp.CustomObject
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.alsoAsync
import com.pubnub.kmp.asFuture
import com.pubnub.kmp.awaitAll
import com.pubnub.kmp.remember
import com.pubnub.kmp.then
import com.pubnub.kmp.thenAsync
import tryLong
import kotlin.text.get

data class ChannelImpl(
    override val chat: ChatInternal,
    private val clock: Clock = Clock.System,
    override val id: String,
    override val name: String? = null,
    override val custom: Map<String, Any?>? = null,
    override val description: String? = null,
    override val updated: String? = null,
    override val status: String? = null,
    override val type: ChannelType? = null,
) : Channel, BaseChannelImpl<Channel, Message>(
        chat = chat,
        clock = clock,
        id = id,
        name = name,
        custom = custom,
        description = description,
        updated = updated,
        status = status,
        type = type,
        channelFactory = ::fromDTO,
        messageFactory = MessageImpl::fromDTO,
        messageFactory2 = MessageImpl::fromDTO
    ) {
    companion object {
        private val log = Logger.withTag("ChannelImpl")

        fun fromDTO(chat: ChatInternal, channel: PNChannelMetadata): Channel {
            return ChannelImpl(
                chat,
                id = channel.id,
                name = channel.name?.value,
                custom = channel.custom?.value,
                description = channel.description?.value,
                updated = channel.updated?.value,
                status = channel.status?.value,
                type = ChannelType.from(channel.type?.value)
            )
        }
    }

    override fun copyWithStatusDeleted(): Channel = copy(status = DELETED)

    override fun invite(user: User): PNFuture<Membership> {
        if (this.type == ChannelType.PUBLIC) {
            return log.logErrorAndReturnException(CHANNEL_INVITES_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS).asFuture()
        }
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
        if (this.type == ChannelType.PUBLIC) {
            return log.logErrorAndReturnException(CHANNEL_INVITES_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS).asFuture()
        }
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

    private val suggestedMemberships = mutableMapOf<String, List<Membership>>()

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

    private val channelFilterString get() = "channel.id == '${this.id}'"
}
