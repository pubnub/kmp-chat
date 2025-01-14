@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.api.PubNubError
import com.pubnub.api.adjustCollectionTypes
import com.pubnub.chat.BaseMessage
import com.pubnub.chat.Message
import com.pubnub.chat.ThreadMessage
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import com.pubnub.kmp.toMap
import kotlin.js.Json
import kotlin.js.Promise
import kotlin.js.json

@JsExport
@JsName("BaseMessage")
open class BaseMessageJs internal constructor(internal val baseMessage: BaseMessage<*, *>, internal val chatJs: ChatJs) {
    val timetoken: String get() = baseMessage.timetoken.toString()
    val content: JsMap<Any?>
        get() {
            return baseMessage.content.toJsTextMessage()
        }
    val channelId by baseMessage::channelId
    val userId by baseMessage::userId
    val actions get() = baseMessage.actions?.mapValues {
        it.value.mapValues {
            it.value.map { action ->
                createJsObject<Reaction> {
                    uuid = action.uuid
                    actionTimetoken = action.actionTimetoken.toString()
                }
            }.toTypedArray()
        }.toJsMap()
    }?.toJsMap()
    val meta get() = baseMessage.meta?.adjustCollectionTypes()
    val error: String? get() {
        return if (baseMessage.error == PubNubError.CRYPTO_IS_CONFIGURED_BUT_MESSAGE_IS_NOT_ENCRYPTED) {
            "Error while decrypting message content"
        } else {
            null
        }
    }

    val mentionedUsers: JsMap<MessageMentionedUser>?
        get() = baseMessage.mentionedUsers?.mapKeys { it.key.toString() }?.toJsMap()
    val referencedChannels: JsMap<MessageReferencedChannel>?
        get() = baseMessage.referencedChannels?.mapKeys { it.key.toString() }?.toJsMap()
    val textLinks get() = baseMessage.textLinks?.toTypedArray()
    val type by baseMessage::type
    val quotedMessage: QuotedMessageJs? get() = baseMessage.quotedMessage?.toJs()
    val files get() = baseMessage.files.toTypedArray()
    val text by baseMessage::text
    val deleted by baseMessage::deleted
    val reactions: JsMap<Array<Reaction>>
        get() = baseMessage.reactions.mapValues { mapEntry ->
            mapEntry.value.map { action ->
                createJsObject<Reaction> {
                    uuid = action.uuid
                    actionTimetoken = action.actionTimetoken.toString()
                }
            }.toTypedArray()
        }.toJsMap()

    fun streamUpdates(callback: (BaseMessageJs?) -> Unit): () -> Unit {
        return baseMessage.streamUpdates { it.asJs(chatJs) }::close
    }

    fun getLinkedText() = getMessageElements()

    fun getMessageElements(): Array<MixedTextTypedElement> {
        // data from v1 message draft
        if (mentionedUsers?.toMap()?.isNotEmpty() == true ||
            textLinks?.isNotEmpty() == true ||
            referencedChannels?.toMap()?.isNotEmpty() == true
        ) {
            return MessageElementsUtils.getMessageElements(
                text,
                mentionedUsers?.toMap()?.mapKeys { it.key.toInt() } ?: emptyMap(),
                textLinks?.toList() ?: emptyList(),
                referencedChannels?.toMap()?.mapKeys { it.key.toInt() } ?: emptyMap(),
            )
        } else {
            // use v2 message draft
            return MessageDraftImpl.getMessageElements(text).toJs()
        }
    }

    fun editText(newText: String): Promise<BaseMessageJs> {
        return baseMessage.editText(newText).then { it.asJs(chatJs) }.asPromise()
    }

    fun delete(params: DeleteParameters?): Promise<Any> {
        return baseMessage.delete(params?.soft ?: false, params?.asDynamic()?.preserveFiles ?: false)
            .then {
                it?.asJs(chatJs) ?: true
            }.asPromise()
    }

    fun restore(): Promise<BaseMessageJs> {
        return baseMessage.restore().then { it.asJs(chatJs) }.asPromise()
    }

    fun hasUserReaction(reaction: String): Boolean {
        return baseMessage.hasUserReaction(reaction)
    }

    fun toggleReaction(reaction: String): Promise<BaseMessageJs> {
        return baseMessage.toggleReaction(reaction).then { it.asJs(chatJs) }.asPromise()
    }

    fun forward(channelId: String): Promise<PubNub.PublishResponse> {
        return baseMessage.forward(channelId).then { it.toPublishResponse() }.asPromise()
    }

    fun pin(): Promise<Any> {
        return baseMessage.pin().then { it.asJs(chatJs) }.asPromise()
    }

    fun report(reason: String): Promise<PubNub.PublishResponse> {
        return baseMessage.report(reason).then { it.toPublishResponse() }.asPromise()
    }

    fun toJSON(): Json {
        return json(
            "timetoken" to timetoken,
            "content" to content,
            "channelId" to channelId,
            "userId" to userId,
            "actions" to actions,
            "meta" to meta,
            "mentionedUsers" to mentionedUsers,
            "referencedChannels" to referencedChannels,
            "textLinks" to textLinks.contentToString(),
            "type" to type,
            "quotedMessage" to quotedMessage,
            "files" to files.contentToString(),
            "text" to text,
            "deleted" to deleted,
            "reactions" to reactions,
            "error" to error
        )
    }
}

internal fun BaseMessage<*, *>.asJs(chat: ChatJs) = when (this) {
    is Message -> MessageJs(this, chat)
    is ThreadMessage -> ThreadMessageJs(this, chat)
    else -> error("Unexpected error. $this is not a `Message` or `ThreadMessage`")
}
