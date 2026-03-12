package com.pubnub.chat.types

/**
 * Parameters for sending text messages.
 *
 * @property meta Additional details to publish with the request.
 * @property shouldStore If true, the messages are stored in Message Persistence if enabled in Admin Portal.
 * @property usePost Use HTTP POST.
 * @property ttl Defines if / how long (in hours) the message should be stored in Message Persistence.
 * @property customPushData Additional key-value pairs that will be added to the FCM and/or APNS push messages.
 */
data class SendTextParams(
    val meta: Map<String, Any>? = null,
    val shouldStore: Boolean = true,
    val usePost: Boolean = false,
    val ttl: Int? = null,
    val customPushData: Map<String, String>? = null,
)
