package com.pubnub.chat.types

import kotlinx.serialization.Serializable

@Serializable
class TextLink(val startIndex: Int, val endIndex: Int, val link: String)
