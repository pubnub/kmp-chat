import com.pubnub.api.JsonElement
import com.pubnub.api.createJsonElement
import com.pubnub.api.enums.PNPushEnvironment
import com.pubnub.api.enums.PNPushType
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNMembershipKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.objects.SortField
import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.chat.config.CustomPayloads
import com.pubnub.chat.config.PushNotificationsConfig
import com.pubnub.chat.config.RateLimitPerChannel
import com.pubnub.chat.internal.serialization.PNDataEncoder
import com.pubnub.chat.restrictions.GetRestrictionsResponse
import com.pubnub.chat.restrictions.Restriction
import com.pubnub.chat.types.ChannelType
import com.pubnub.chat.types.EventContent
import com.pubnub.chat.types.File
import com.pubnub.chat.types.QuotedMessage
import com.pubnub.kmp.JsMap
import com.pubnub.kmp.PNFuture
import com.pubnub.kmp.createCustomObject
import com.pubnub.kmp.createJsObject
import com.pubnub.kmp.toMap
import kotlinx.serialization.Serializable
import kotlin.js.Promise
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal fun GetRestrictionsResponse.toJs() =
    createJsObject<GetRestrictionsResponseJs> {
        this.page = MetadataPage(next, prev)
        this.restrictions = this@toJs.restrictions.map { it.asJs() }.toTypedArray()
        this.status = this@toJs.status
        this.total = this@toJs.total
    }

internal inline fun <reified T : SortField> extractSortKeys(sort: Any?): List<PNSortKey<T>> =
    sort?.unsafeCast<JsMap<String?>>()?.toMap()?.map {
        val fieldName = it.key
        val direction = it.value ?: "asc"
        when (T::class) {
            PNMembershipKey::class -> getAscOrDesc(
                direction,
                PNMembershipKey.entries.find {
                    it.fieldName == fieldName
                } ?: error("Unknown sort field: $fieldName")
            )
            PNKey::class -> getAscOrDesc(direction, PNKey.entries.find { it.fieldName == fieldName } ?: error("Unknown sort field: $fieldName"))
            PNMemberKey::class -> getAscOrDesc(
                direction,
                PNMemberKey.entries.find {
                    it.fieldName == fieldName
                } ?: error("Unknown sort field: $fieldName")
            )
            else -> error("Should never happen")
        } as PNSortKey<T>
    } ?: listOf()

internal fun <T : SortField> getAscOrDesc(direction: String, field: T): PNSortKey<T> {
    return if (direction == "asc") {
        PNSortKey.PNAsc(field)
    } else {
        PNSortKey.PNDesc(field)
    }
}

internal fun QuotedMessage.toJs(): QuotedMessageJs = QuotedMessageJs(
    timetoken = timetoken.toString(),
    text = text,
    userId = userId
)

internal inline fun <reified T> @Serializable T.toJsObject(): JsMap<Any?> {
    return createJsonElement(PNDataEncoder.encode(this) as Map<String, Any?>).value.unsafeCast<JsMap<Any?>>()
}

internal inline fun Map<String, Any?>.toJsObject(): JsMap<Any?> {
    return createJsonElement(this).value.unsafeCast<JsMap<Any?>>()
}

internal class TextMessageContentWithCustom(text: String, files: List<File>?, internal val customJsObject: Any) : EventContent.TextMessageContent(
    text,
    files
)

internal fun EventContent.TextMessageContent.withCustomJsObject(customJsObject: Any) = TextMessageContentWithCustom(
    text,
    files,
    customJsObject
)

internal fun CustomPayloadsJs?.toKmp(): CustomPayloads {
    if (this == null) {
        return CustomPayloads()
    }
    return CustomPayloads(
        getMessagePublishBody?.let { mpb ->
            {
                    m: EventContent.TextMessageContent,
                    channelId: String,
                    defaultMessagePublishBody: (m: EventContent.TextMessageContent) -> Map<String, Any?> ->
                mpb(
                    m.toJsTextMessage(),
                    channelId
                ).unsafeCast<JsMap<Any>>().toMap()
            }
        },
        getMessageResponseBody?.let { mrb ->
            fun(
                m: JsonElement,
                channelId: String,
                defaultMessageResponseBody: (JsonElement) -> EventContent.TextMessageContent?
            ): EventContent.TextMessageContent? {
                val jsMap = m.value.unsafeCast<JsMap<Any?>>()
                val messageDTOparams = Any().asDynamic()
                messageDTOparams.channel = channelId
                messageDTOparams.message = jsMap
                val resultingMessage = mrb(messageDTOparams)
                val textMessageContent = PNDataEncoder.decode<EventContent.TextMessageContent?>(createJsonElement(resultingMessage))
                return textMessageContent?.withCustomJsObject(resultingMessage)
            }
        },
        editMessageActionName = editMessageActionName,
        deleteMessageActionName = deleteMessageActionName,
        reactionsActionName = reactionsActionName
    )
}

fun PushNotificationsConfigJs?.toKmp(): PushNotificationsConfig {
    if (this == null) {
        return PushNotificationsConfig(false, "", PNPushType.FCM)
    }
    return PushNotificationsConfig(
        sendPushes,
        deviceToken,
        PNPushType.fromParamString(deviceGateway),
        apnsTopic,
        PNPushEnvironment.fromParamString(apnsEnvironment)
    )
}

internal fun Restriction.asJs(): RestrictionJs {
    val restriction = this
    return createJsObject {
        this.ban = restriction.ban
        this.mute = restriction.mute
        restriction.reason?.let { this.reason = it }
        this.channelId = restriction.channelId
        this.userId = restriction.userId
    }
}

internal fun PubNub.MetadataPage?.toKmp() =
    this?.next?.let { PNPage.PNNext(it) } ?: this?.prev?.let { PNPage.PNPrev(it) }

internal fun MetadataPage(next: PNPage.PNNext?, prev: PNPage.PNPrev?) = createJsObject<PubNub.MetadataPage> {
    this.next = next?.pageHash ?: undefined
    this.prev = prev?.pageHash ?: undefined
}

internal fun convertToCustomObject(custom: Any?) = custom?.let {
    createCustomObject(custom.unsafeCast<JsMap<Any?>>().toMap())
}

fun <T> PNFuture<T>.asPromise(): Promise<T> = Promise { resolve, reject ->
    async {
        it.onSuccess {
            resolve(it)
        }.onFailure {
            reject(it)
        }
    }
}

internal fun ChatConfig.toChatConfiguration(): ChatConfiguration {
    return ChatConfiguration(
        typingTimeout = typingTimeout?.milliseconds ?: 5.seconds,
        storeUserActivityInterval = storeUserActivityInterval?.milliseconds ?: 600.seconds,
        storeUserActivityTimestamps = storeUserActivityTimestamps == true,
        pushNotifications = pushNotifications.toKmp(),
        rateLimitFactor = rateLimitFactor ?: 2,
        rateLimitPerChannel = rateLimitPerChannel.toKmp(),
        customPayloads = customPayloads.toKmp(),
        syncMutedUsers = syncMutedUsers == true
    )
}

private fun RateLimitPerChannelJs?.toKmp(): Map<ChannelType, Duration> {
    val resultingMap = RateLimitPerChannel().toMutableMap()
    this?.unsafeCast<JsMap<Int>>()?.toMap()?.forEach {
        val type = ChannelType.from(it.key)
        resultingMap[type] = it.value.milliseconds
    }
    return resultingMap
}

internal fun EventContent.TextMessageContent.toJsTextMessage(): JsMap<Any?> =
    (
        (this as? TextMessageContentWithCustom)?.customJsObject?.unsafeCast<JsMap<Any?>>()
            ?: (this as EventContent).toJsObject()
    )
