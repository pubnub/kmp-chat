package com.pubnub.chat.types

import com.pubnub.kmp.Uploadable

/**
 * Represents a file that can be attached to a message when sending text to a channel.
 *
 * @property name The name of the file.
 * @property type The type or MIME type of the file (e.g., "image/jpeg", "application/pdf").
 * @property source The [Uploadable] object representing the file's source, such as the file content or its location.
 */
class InputFile(
    val name: String,
    val type: String,
    val source: Uploadable
)
