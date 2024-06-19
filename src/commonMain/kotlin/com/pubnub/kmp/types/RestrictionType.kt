package com.pubnub.kmp.types

enum class RestrictionType(val stringValue: String) {
    BAN("ban"), MUTE("mute"), LIFTED("lifted");

    companion object{
        fun getByTest(text: String): RestrictionType?{
            return values().find {it.stringValue== text }
        }
    }

    override fun toString(): String {
        return stringValue
    }
}