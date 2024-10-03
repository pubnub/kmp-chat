package com.pubnub.chat.types

import com.pubnub.api.JsonElement
import com.pubnub.chat.restrictions.RestrictionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.LongAsStringSerializer
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Represents a file that is associated with a message in the chat system.
 *
 * @property name The name of the file.
 * @property id The unique identifier of the file.
 * @property url The URL where the file can be accessed or downloaded.
 * @property type The MIME type of the file (e.g., "image/jpeg", "application/pdf"). This is optional.
 */
@OptIn(ExperimentalJsExport::class)
@Serializable
@JsExport
class File(
    val name: String,
    val id: String,
    val url: String,
    val type: String? = null
)

/**
 * Represents the content of various types of events emitted during chat operations.
 * This is a sealed class with different subclasses representing specific types of events.
 */
@Serializable
sealed class EventContent {
    /**
     * Represents a typing event that indicates whether a user is typing.
     *
     * @property value A boolean value indicating whether the user is typing (true) or not (false).
     */
    @Serializable
    @SerialName("typing")
    class Typing(val value: Boolean) : EventContent()

    /**
     * Represents a report event, which is used to report a message or user to the admin.
     *
     * @property text The text of the report, if provided.
     * @property reason The reason for reporting the message or user.
     * @property reportedMessageTimetoken The timetoken of the message being reported, if applicable.
     * @property reportedMessageChannelId The channel ID of the reported message, if applicable.
     * @property reportedUserId The ID of the user being reported.
     */
    @Serializable
    @SerialName("report")
    class Report(
        val text: String? = null,
        val reason: String,
        @Serializable(with = LongAsStringSerializer::class)
        val reportedMessageTimetoken: Long? = null,
        val reportedMessageChannelId: String? = null,
        val reportedUserId: String?,
    ) : EventContent()

    /**
     * Represents a receipt event, indicating that a message was read.
     *
     * @property messageTimetoken The timetoken of the message for which the receipt is being acknowledged.
     */
    @Serializable
    @SerialName("receipt")
    class Receipt(
        @Serializable(with = LongAsStringSerializer::class) val messageTimetoken: Long
    ) : EventContent()

    /**
     * Represents a mention event, which indicates that a user was mentioned in a message.
     *
     * @property messageTimetoken The timetoken of the message in which the user was mentioned.
     * @property channel The ID of the channel where the mention occurred.
     * @property parentChannel The ID of the parent channel if the mention occurred in a thread, otherwise null.
     */
    @Serializable
    @SerialName("mention")
    class Mention(
        @Serializable(with = LongAsStringSerializer::class) val messageTimetoken: Long,
        val channel: String,
        val parentChannel: String? = null
    ) : EventContent()
    // channel should be channelId and parentChannel should be parentChannelId, but we can't change it not tt break compatibility with existing Chat SDK

    /**
     * Represents an invite event, which is used when a user is invited to join a channel.
     *
     * @property channelType The type of the channel (e.g., direct, group).
     * @property channelId The ID of the channel to which the user is invited.
     */
    @Serializable
    @SerialName("invite")
    class Invite(val channelType: ChannelType, val channelId: String) : EventContent()

    /**
     * Represents a custom event with arbitrary data.
     *
     * @property data A map containing key-value pairs of custom data associated with the event.
     * @property method The method by which the event was emitted (PUBLISH, SIGNAL).
     */
    class Custom(
        val data: Map<String, Any?>,
        @Transient val method: EmitEventMethod = EmitEventMethod.PUBLISH
    ) : EventContent()

    /**
     * Represents a moderation event, which is triggered when a restriction is applied to a user.
     *
     * @property channelId The ID of the channel where the moderation event occurred.
     * @property restriction The type of restriction applied (e.g., ban, mute).
     * @property reason The reason for the restriction, if provided.
     */
    @Serializable
    @SerialName("moderation")
    class Moderation(val channelId: String, val restriction: RestrictionType, val reason: String? = null) :
        EventContent()

    /**
     * Represents a text message event, containing the message text and any associated files.
     *
     * @property text The text content of the message.
     * @property files A list of files attached to the message, if any.
     */
    @Serializable
    @SerialName("text")
    open class TextMessageContent(
        val text: String,
        val files: List<File>? = null,
    ) : EventContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is TextMessageContent) {
                return false
            }

            if (text != other.text) {
                return false
            }
            if (files != other.files) {
                return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + (files?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "TextMessageContent(text='$text', files=$files)"
        }
    }

    /**
     * Represents a message with an unknown format, used to handle cases where the message format doesn't match known types.
     *
     * @property jsonElement The raw JSON element representing the message with the unknown format.
     */
    class UnknownMessageFormat(val jsonElement: JsonElement) : TextMessageContent("", null)
}

/**
 * Enum representing the method used to emit an event in the chat system.
 *
 * @property SIGNAL Represents events emitted using the "signal" method, typically for lightweight real-time updates.
 * @property PUBLISH Represents events emitted using the "publish" method, typically for broadcasting messages to a channel.
 */
enum class EmitEventMethod {
    /**
     * Emits the event using the "signal" method, which is lightweight(doesn't store data in history) and used for
     * real-time updates such as typing indicators, read receipt.
     */
    SIGNAL,

    /**
     * Emits the event using the "publish" method, typically used for sending or broadcasting messages to a channel.
     */
    PUBLISH
}
