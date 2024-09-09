package com.pubnub.chat.internal.util

import com.pubnub.chat.types.EventContent
import platform.Foundation.NSString
import platform.Foundation.stringByRemovingPercentEncoding
import kotlin.reflect.KClass

internal actual fun urlDecode(encoded: String): String {
    return (encoded as NSString).stringByRemovingPercentEncoding().orEmpty()
}

class KClassConstants {
    companion object {
        var typing: KClass<EventContent.Typing> = EventContent.Typing::class
        var report: KClass<EventContent.Report> = EventContent.Report::class
        var receipt: KClass<EventContent.Receipt> = EventContent.Receipt::class
        var mention: KClass<EventContent.Mention> = EventContent.Mention::class
        var invite: KClass<EventContent.Invite> = EventContent.Invite::class
        var custom: KClass<EventContent.Custom> = EventContent.Custom::class
        var moderation: KClass<EventContent.Moderation> = EventContent.Moderation::class
        var textMessageContent: KClass<EventContent.TextMessageContent> = EventContent.TextMessageContent::class
        var unknownMessageFormat: KClass<EventContent.UnknownMessageFormat> = EventContent.UnknownMessageFormat::class
    }
}
