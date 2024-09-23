package com.pubnub.chat.types

/**
 * Represents the result of fetching files attached to messages on a given channel.
 *
 * @property files A collection of [GetFileItem] objects representing the files attached to messages that were retrieved.
 * @property next A pagination token to retrieve the next batch of files, if available.
 * @property total The total number of files that match the query.
 */
class GetFilesResult(
    val files: Collection<GetFileItem>,
    val next: String?,
    val total: Int,
)

/**
 * Represents a file attached to a message in a channel.
 *
 * @property name The name of the file.
 * @property id The unique identifier of the file.
 * @property url The URL where the file can be accessed or downloaded.
 */
class GetFileItem(
    val name: String,
    val id: String,
    val url: String,
)
