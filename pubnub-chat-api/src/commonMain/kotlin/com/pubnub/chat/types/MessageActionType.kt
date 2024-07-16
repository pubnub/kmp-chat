package com.pubnub.chat.types

enum class MessageActionType {
    REACTIONS,
    DELETED,
    EDITED;

    override fun toString(): String {
        return name.lowercase()
    }
}
