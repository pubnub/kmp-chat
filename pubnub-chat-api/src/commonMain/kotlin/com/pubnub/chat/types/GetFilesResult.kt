package com.pubnub.chat.types

class GetFilesResult(
    val files: Collection<GetFileItem>,
    val next: String?,
    val total: Int,
)

class GetFileItem(
    val name: String,
    val id: String,
    val url: String,
)
