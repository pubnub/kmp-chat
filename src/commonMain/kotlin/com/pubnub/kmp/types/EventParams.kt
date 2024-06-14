package com.pubnub.kmp.types


sealed class EventParams(val type: String, open val channel: String? = null, open val user: String? = null) {
    data class Typing(override val channel: String) : EventParams(type = "typing", channel = channel)
    data class Report(override val channel: String) : EventParams(type = "report", channel = channel)
    data class Receipt(override val channel: String) : EventParams(type = "receipt", channel = channel)
    data class Mention(override val user: String) : EventParams(type = "mention", user = user)
    data class Invite(override val channel: String) : EventParams(type = "invite", channel = channel)
    data class Moderation(override val channel: String) : EventParams(type = "moderation", channel = channel)
    data class CustomEventParam(override val channel: String, val method: EmitEventMethod = EmitEventMethod.SIGNAL) :
        EventParams("custom", channel = channel)
}
