@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.api.PubNubError
import com.pubnub.api.adjustCollectionTypes
import com.pubnub.chat.Message
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.internal.message.BaseMessage
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
@JsName("Message")
open class MessageJs internal constructor(internal val message: Message, internal val chatJs: ChatJs) {
    val hasThread by message::hasThread
    val timetoken: String get() = message.timetoken.toString()
    val content: JsMap<Any?>
        get() {
            return message.content.toJsTextMessage()
        }
    val channelId by message::channelId
    val userId by message::userId
    val actions get() = message.actions?.mapValues {
        it.value.mapValues {
            it.value.map { action ->
                createJsObject<Reaction> {
                    uuid = action.uuid
                    actionTimetoken = action.actionTimetoken.toString()
                }
            }.toTypedArray()
        }.toJsMap()
    }?.toJsMap()
    val meta get() = message.meta?.adjustCollectionTypes()
    val error: String? get() {
        return if (message.error == PubNubError.CRYPTO_IS_CONFIGURED_BUT_MESSAGE_IS_NOT_ENCRYPTED) {
            "Error while decrypting message content"
        } else {
            null
        }
    }

    val mentionedUsers: JsMap<MessageMentionedUser>?
        get() = message.mentionedUsers?.mapKeys { it.key.toString() }?.toJsMap()
    val referencedChannels: JsMap<MessageReferencedChannel>?
        get() = message.referencedChannels?.mapKeys { it.key.toString() }?.toJsMap()
    val textLinks get() = message.textLinks?.toTypedArray()
    val type by message::type
    val quotedMessage: QuotedMessageJs? get() = message.quotedMessage?.toJs()
    val files get() = message.files.toTypedArray()
    val text by message::text
    val deleted by message::deleted
    val reactions: Array<MessageReactionJs>
        get() = message.reactions.map { reaction ->
            createJsObject<MessageReactionJs> {
                value = reaction.value
                isMine = reaction.isMine
                userIds = reaction.userIds.toTypedArray()
            }
        }.toTypedArray()

    fun onUpdated(callback: (message: MessageJs) -> Unit): () -> Unit {
        val closeable = message.onUpdated {
            callback(it.asJs(chatJs))
        }
        return closeable::close
    }

    fun streamUpdates(callback: (MessageJs?) -> Unit): () -> Unit {
        return message.streamUpdates<Message> { callback(it.asJs(chatJs)) }::close
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

    open fun editText(newText: String): Promise<MessageJs> {
        return message.editText(newText).then { it.asJs(chatJs) }.asPromise()
    }

    fun delete(params: DeleteParameters?): Promise<Any> {
        return message.delete(params?.soft ?: false, params?.asDynamic()?.preserveFiles ?: false)
            .then {
                it?.asJs(chatJs) ?: true
            }.asPromise()
    }

    open fun restore(): Promise<MessageJs> {
        return message.restore().then { it.asJs(chatJs) }.asPromise()
    }

    fun hasUserReaction(reaction: String): Boolean {
        return message.hasUserReaction(reaction)
    }

    open fun toggleReaction(reaction: String): Promise<MessageJs> {
        return message.toggleReaction(reaction).then { it.asJs(chatJs) }.asPromise()
    }

    fun forward(channelId: String): Promise<PubNub.PublishResponse> {
        return message.forward(channelId).then { it.toPublishResponse() }.asPromise()
    }

    fun pin(): Promise<Any> {
        return message.pin().then { it.asJs(chatJs) }.asPromise()
    }

    fun report(reason: String): Promise<PubNub.PublishResponse> {
        return message.report(reason).then { it.toPublishResponse() }.asPromise()
    }

    fun getThread(): Promise<ThreadChannelJs> {
        return message.getThread().then { it.asJs(chatJs) }.asPromise()
    }

    fun createThread(text: String, options: SendTextParamsJs? = null): Promise<ThreadChannelJs> {
        return message.createThread(
            text = text,
            params = options.toSendTextParams(),
        ).then { it.asJs(chatJs) }.asPromise()
    }

    fun createThreadWithResult(text: String, options: SendTextParamsJs? = null): Promise<CreateThreadResultJs> {
        return message.createThreadWithResult(
            text = text,
            params = options.toSendTextParams(),
        ).then { result ->
            createJsObject<CreateThreadResultJs> {
                this.threadChannel = result.threadChannel.asJs(chatJs)
                this.parentMessage = result.parentMessage.asJs(chatJs)
            }
        }.asPromise()
    }

    fun createThreadMessageDraft(config: MessageDraftConfig? = null): Promise<MessageDraftV2Js> {
        return message.createThread().then { threadChannel ->
            val channelJs = threadChannel.asJs(chatJs)
            channelJs.createMessageDraft(config)
        }.asPromise()
    }

    @Deprecated("Use createThreadMessageDraft() instead.")
    fun createThreadMessageDraftV2(config: MessageDraftConfig? = null): Promise<MessageDraftV2DeprecatedJs> {
        return message.createThread().then { threadChannel ->
            val channelJs = threadChannel.asJs(chatJs)
            channelJs.createMessageDraftV2(config)
        }.asPromise()
    }

    fun removeThread(): Promise<Array<Any>> {
        return message.removeThread().then {
            arrayOf(
                Any(),
                it.second?.asJs(chatJs)?.let { channelJs -> DeleteChannelResult(channelJs) }
                    ?: DeleteChannelResult(true)
            )
        }.asPromise()
    }

    fun toJSON(): Json {
        return json(
            "hasThread" to hasThread,
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

    companion object {
        @JsStatic
        fun streamUpdatesOn(messages: Array<MessageJs>, callback: (Array<MessageJs>) -> Unit): () -> Unit {
            val chatJs = messages.first().chatJs
            return BaseMessage.streamUpdatesOn(messages.map { it.message }) { kmpMessages ->
                callback(kmpMessages.map { kmpMessage -> kmpMessage.asJs(chatJs) }.toTypedArray())
            }::close
        }
    }
}

internal fun Message.asJs(chat: ChatJs) = MessageJs(this, chat)
