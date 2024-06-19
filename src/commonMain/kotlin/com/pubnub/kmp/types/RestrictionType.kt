package com.pubnub.kmp.types

enum class RestrictionType(val stringValue: String) {
    BAN("banned"), MUTE("muted"), LIFT("lifted");

    companion object{
        fun getByTest(text: String): RestrictionType?{
            return values().find {it.stringValue== text }
        }
    }

    override fun toString(): String {
        return stringValue
    }
}