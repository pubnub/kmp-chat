package com.pubnub.chat.internal

import com.pubnub.api.PubNubException
import com.pubnub.api.asMap
import com.pubnub.api.asString
import com.pubnub.api.models.consumer.history.PNFetchMessageItem
import com.pubnub.chat.Chat
import com.pubnub.chat.Event
import com.pubnub.chat.internal.error.PubNubErrorMessage
import com.pubnub.chat.restrictions.RestrictionType
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EmitEventMethod
import com.pubnub.chat.types.EventContent

class EventImpl<T : EventContent>(
    override val chat: Chat,
    override val timetoken: Long,
    override val payload: T,
    override val channelId: String,
    override val userId: String
) : Event<T> {
    companion object {
        fun fromDTO(
            chat: Chat,
            channelId: String,
            pnFetchMessageItem: PNFetchMessageItem
        ): Event<EventContent> {
            val payload = getEventContentByPnFetchMessageItem(pnFetchMessageItem)

            return EventImpl(
                chat = chat,
                timetoken = pnFetchMessageItem.timetoken ?: 0,
                payload = payload,
                channelId = channelId,
                userId = pnFetchMessageItem.uuid ?: "unknown-user"
            )
        }

        private fun getEventContentByPnFetchMessageItem(pnFetchMessageItem: PNFetchMessageItem): EventContent {
            // "type" is added to EventContent in PNDataEncoder.encode(message) as Map<String, Any>
            val eventType: String = pnFetchMessageItem.message.asMap()?.get("type")?.asString().orEmpty()
            return when (eventType) {
                // "receipt" and "typing" goes via signal thus they are not stored in history
                "text" -> getEventContentTextFromMessageItem(pnFetchMessageItem)
                "invite" -> getEventContentInviteFromMessageItem(pnFetchMessageItem)
                "report" -> getEventContentReportFromMessageItem(pnFetchMessageItem)
                "mention" -> getEventContentMentionFromMessageItem(pnFetchMessageItem)
                "custom" -> getEventContentCustomFromMessageItem(pnFetchMessageItem)
                "moderation" -> getEventContentModerationFromMessageItem(pnFetchMessageItem)
                else -> throw PubNubException(PubNubErrorMessage.UNKNOWN_EVENT_TYPE + "eventType")
            }
        }

        private fun getEventContentCustomFromMessageItem(pnFetchMessageItem: PNFetchMessageItem): EventContent.Custom {
            val data = pnFetchMessageItem.message.asMap()?.get(EventContent.Custom.DATA)?.asString().orEmpty()
            val method = pnFetchMessageItem.message.asMap()?.get(EventContent.Custom.METHOD)?.asString().orEmpty()
            return EventContent.Custom(data = data, method = EmitEventMethod.from(method))
        }

        private fun getEventContentModerationFromMessageItem(pnFetchMessageItem: PNFetchMessageItem): EventContent.Moderation {
            val channelId = pnFetchMessageItem.message.asMap()?.get(EventContent.Moderation.CHANNEL_ID)?.asString().orEmpty()
            val restriction = pnFetchMessageItem.message.asMap()?.get(EventContent.Moderation.RESTRICTION)?.asString().orEmpty()
            val reason = pnFetchMessageItem.message.asMap()?.get(EventContent.Moderation.REASON)?.asString().orEmpty()
            return EventContent.Moderation(
                channelId = channelId,
                restriction = RestrictionType.from(restriction),
                reason = reason
            )
        }

        private fun getEventContentReportFromMessageItem(pnFetchMessageItem: PNFetchMessageItem): EventContent.Report {
            val text = pnFetchMessageItem.message.asMap()?.get(EventContent.Report.TEXT)?.asString().orEmpty()
            val reason = pnFetchMessageItem.message.asMap()?.get(EventContent.Report.REASON)?.asString().orEmpty()
            val reportedMessageTimetoken =
                pnFetchMessageItem.message.asMap()?.get(EventContent.Report.REPORTED_MESSAGE_TIMETOKEN)?.asString()?.toLong() ?: 0
            val reportedMessageChannelId =
                pnFetchMessageItem.message.asMap()?.get(EventContent.Report.REPORTED_MESSAGE_CHANNEL_ID)?.asString().orEmpty()
            val reportedUserId =
                pnFetchMessageItem.message.asMap()?.get(EventContent.Report.REPORTED_USER_ID)?.asString().orEmpty()
            return EventContent.Report(
                text = text,
                reason = reason,
                reportedMessageTimetoken = reportedMessageTimetoken,
                reportedMessageChannelId = reportedMessageChannelId,
                reportedUserId = reportedUserId
            )
        }

        private fun getEventContentMentionFromMessageItem(pnFetchMessageItem: PNFetchMessageItem): EventContent.Mention {
            val messageTimetoken =
                pnFetchMessageItem.message.asMap()?.get(EventContent.Mention.MESSAGE_TIMETOKEN)?.asString()?.toLong() ?: 0
            val channel = pnFetchMessageItem.message.asMap()?.get(EventContent.Mention.CHANNEL)?.asString().orEmpty()
            return EventContent.Mention(messageTimetoken = messageTimetoken, channel = channel)
        }

        private fun getEventContentTextFromMessageItem(pnFetchMessageItem: PNFetchMessageItem): EventContent.TextMessageContent {
            val text = pnFetchMessageItem.message.asMap()?.get(EventContent.TextMessageContent.TEXT)?.asString().orEmpty()
            val files = pnFetchMessageItem.message.asMap()?.get(EventContent.TextMessageContent.FILES)?.asString().orEmpty()
            return EventContent.TextMessageContent(text = text, files = listOf()) // todo handle files
        }

        private fun getEventContentInviteFromMessageItem(pnFetchMessageItem: PNFetchMessageItem): EventContent.Invite {
            val channelType = pnFetchMessageItem.message.asMap()?.get(EventContent.Invite.CHANNEL_TYPE)?.asString().orEmpty()
            val channelId = pnFetchMessageItem.message.asMap()?.get(EventContent.Invite.CHANNEL_ID)?.asString().orEmpty()
            return EventContent.Invite(channelType = ChannelType.from(channelType), channelId = channelId)
        }
    }
}
