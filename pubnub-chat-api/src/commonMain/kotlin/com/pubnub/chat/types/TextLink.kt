package com.pubnub.chat.types

import kotlinx.serialization.Serializable

@Serializable
data class TextLink(val startIndex: Int, val endIndex: Int, val link: String)
