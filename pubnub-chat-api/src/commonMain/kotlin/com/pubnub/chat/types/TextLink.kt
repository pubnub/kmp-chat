@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.chat.types

import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@Serializable
@JsExport
data class TextLink(val startIndex: Int, val endIndex: Int, val link: String)
