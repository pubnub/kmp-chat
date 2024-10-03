package com.pubnub.chat.types

import kotlinx.serialization.Serializable
import kotlin.js.JsExport

@Serializable
@JsExport
class TextLink(val startIndex: Int, val endIndex: Int, val link: String)
