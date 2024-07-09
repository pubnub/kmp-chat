package com.pubnub.kmp.types

enum class MessageActionType {
    REACTIONS,
    DELETED,
    EDITED;

    override fun toString(): String {
        return name.lowercase()
    }
}
