package com.pubnub.chat.internal.error

internal object PubNubErrorMessage {
    internal const val TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS =
        "Typing indicators are not supported in Public chats."
    internal const val FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA = "Failed to retrieve wherePresent data. "
    internal const val FAILED_TO_RETRIEVE_IS_PRESENT_DATA = "Failed to retrieve isPresent data."
    internal const val FAILED_TO_RETRIEVE_HISTORY_DATA = "Failed to retrieve getHistory data."
    internal const val FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA = "Failed to retrieve whoIsPresent data."
    internal const val FAILED_TO_CREATE_UPDATE_CHANNEL_DATA = "Failed to create/update channel data."
    internal const val FAILED_TO_CREATE_UPDATE_USER_DATA = "Failed to create/update user data."
    internal const val FAILED_TO_RETRIEVE_CHANNEL_DATA = "Failed to retrieve channel data."
    internal const val FAILED_TO_RETRIEVE_USER_DATA = "Failed to retrieve user data."
    internal const val FAILED_TO_RETRIEVE_GET_MEMBERSHIP_DATA = "Failed to retrieve getMembership data."
    internal const val FAILED_TO_GET_USERS = "Failed to get users."
    internal const val FAILED_TO_GET_CHANNELS = "Failed to get channels."
    internal const val FAILED_TO_FORWARD_MESSAGE = "Failed to forward message."
    internal const val CHANNEL_META_DATA_IS_EMPTY = "Channel metadata is empty."
    internal const val CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL =
        "You cannot forward the message to the same channel."
    internal const val FOR_PUBLISH_PAYLOAD_SHOULD_BE_OF_TYPE_TEXT_MESSAGE_CONTENT =
        "When emitEvent method is PUBLISH payload should be of type EventContent.TextMessageContent"
    internal const val CHANNEL_ID_ALREADY_EXIST = "Channel with this ID already exists"
    internal const val USER_ID_ALREADY_EXIST = "User with this ID already exists"
    internal const val CHANNEL_NOT_EXIST = "Channel does not exist"
    internal const val CHANNEL_ID_MUST_BE_DEFINED = "Channel id must be defined"
    internal const val USER_ID_MUST_BE_DEFINED = "User id must be defined"
    internal const val USER_NOT_EXIST = "User does not exist"
    internal const val FAILED_TO_SOFT_DELETE_CHANNEL = "Failed to soft delete the channel"
    internal const val FAILED_TO_DELETE_USER = "Failed to delete the user"
    internal const val FAILED_TO_UPDATE_USER_METADATA = "Failed to update user metadata."
    internal const val FAILED_TO_REMOVE_CHANNEL_MEMBERS = "Failed to remove channel members."
    internal const val FAILED_TO_SET_CHANNEL_MEMBERS = "Failed to set channel members."
    internal const val CHANNEL_INVITES_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS =
        "Channel invites are not supported in Public chats."
    internal const val MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY =
        "Moderation restrictions can only be set by clients initialized with a Secret Key."
    internal const val STORE_USER_ACTIVITY_INTERVAL_IS_FALSE =
        "storeUserActivityTimestamps config property is set to false so can not provide info about user being active"
    internal const val STORE_USER_ACTIVITY_INTERVAL_SHOULD_BE_AT_LEAST_1_MIN =
        "storeUserActivityInterval must be at least 60000ms"
    internal const val APNS_TOPIC_SHOULD_BE_DEFINED_WHEN_DEVICE_GATEWAY_IS_SET_TO_APNS2 =
        "apnsTopic has to be defined when deviceGateway is set to apns2"
    internal const val NO_SUCH_MEMBERSHIP_EXISTS = "No such membership exists"
}
