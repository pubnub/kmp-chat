@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

import com.pubnub.chat.Message
import com.pubnub.chat.internal.message.BaseMessage
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.MessageMentionedUser
import com.pubnub.chat.types.MessageReferencedChannel
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.then
import com.pubnub.kmp.toJsMap
import kotlin.js.Promise

@JsExport
@JsName("Message")
open class MessageJs internal constructor(internal val message: Message) {
    val hasThread by message::hasThread
    val timetoken: String get() = message.timetoken.toString()
    val content get() = (message.content as EventContent).toJsObject()
    val channelId by message::channelId
    val userId by message::userId
    val actions get() = message.actions?.mapValues {
        it.value.mapValues {
            it.value.map { action ->
                val jsAction = Any().asDynamic()
                jsAction.uuid = action.uuid
                jsAction.actionTimetoken = action.actionTimetoken.toString()
                jsAction
            }.toTypedArray()
        }.toJsMap()
    }?.toJsMap()
    val meta get() = message.meta?.toJsMap() // todo recursive?

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
    val reactions
        get() = message.reactions.mapValues { mapEntry ->
            mapEntry.value.map { action ->
                val jsAction = Any().asDynamic()
                jsAction.uuid = action.uuid
                jsAction.actionTimetoken = action.actionTimetoken.toString()
                jsAction
            }.toTypedArray()
        }.toJsMap()

    fun streamUpdates(callback: (MessageJs?) -> Unit): () -> Unit {
        return message.streamUpdates<Message> { it.asJs() }::close
    }

//    fun getMessageElements() TODO

    fun editText(newText: String): Promise<MessageJs> {
        return message.editText(newText).then { it.asJs() }.asPromise()
    }

    fun delete(params: DeleteParameters?): Promise<Any> {
        return message.delete(params?.soft ?: false, params?.asDynamic()?.preserveFiles ?: false)
            .then {
                it?.asJs() ?: true
            }.asPromise()
    }

    fun restore(): Promise<MessageJs> {
        return message.restore().then { it.asJs() }.asPromise()
    }

    fun hasUserReaction(reaction: String): Boolean {
        return message.hasUserReaction(reaction)
    }

    fun toggleReaction(reaction: String): Promise<MessageJs> {
        return message.toggleReaction(reaction).then { it.asJs() }.asPromise()
    }

    fun forward(channelId: String): Promise<PubNub.PublishResponse> {
        return message.forward(channelId).then { result ->
            createJsObject<PubNub.PublishResponse> { timetoken = result.timetoken.toString() }
        }.asPromise()
    }

    fun pin(): Promise<Any> {
        return message.pin().then { it.asJs() }.asPromise()
    }

    fun report(reason: String): Promise<PubNub.SignalResponse> {
        return message.report(reason).then { result ->
            createJsObject<PubNub.SignalResponse> { timetoken = result.timetoken.toString() }
        }.asPromise()
    }

    fun getThread(): Promise<ThreadChannelJs> {
        return message.getThread().then { it.asJs() }.asPromise()
    }

    fun createThread(): Promise<ThreadChannelJs> {
        return message.createThread().then { it.asJs() }.asPromise()
    }

    fun removeThread(): Promise<Array<Any>> {
        return message.removeThread().then {
            arrayOf(
                Any(),
                it.second?.asJs() ?: true
            )
        }.asPromise()
    }

    companion object {
        @JsStatic
        fun streamUpdatesOn(messages: Array<MessageJs>, callback: (Array<MessageJs>) -> Unit): () -> Unit {
            return BaseMessage.streamUpdatesOn(messages.map { it.message }) { kmpMessages ->
                callback(kmpMessages.map { kmpMessage -> kmpMessage.asJs() }.toTypedArray())
            }::close
        }
    }
}

internal fun Message.asJs() = MessageJs(this)
