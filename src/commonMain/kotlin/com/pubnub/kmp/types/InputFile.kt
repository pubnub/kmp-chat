package com.pubnub.kmp.types

import com.pubnub.kmp.Uploadable

class InputFile(
    val name: String,
    val type: String,
    val source: Uploadable
)
