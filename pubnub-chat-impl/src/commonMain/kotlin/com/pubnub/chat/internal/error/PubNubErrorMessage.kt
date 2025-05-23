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
    internal const val FAILED_TO_RETRIEVE_GET_MEMBERSHIP_DATA = "Failed to retrieve getMembership data."
    internal const val FAILED_TO_GET_USERS = "Failed to get users."
    internal const val FAILED_TO_GET_CHANNELS = "Failed to get channels."
    internal const val FAILED_TO_FORWARD_MESSAGE = "Failed to forward message."
    internal const val CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL =
        "You cannot forward the message to the same channel."
    internal const val CHANNEL_ID_ALREADY_EXIST = "Channel with this ID already exists"
    internal const val USER_ID_ALREADY_EXIST = "User with this ID already exists"
    internal const val CHANNEL_NOT_EXIST = "Channel does not exist"
    internal const val USER_NOT_EXIST = "User does not exist"
    internal const val FAILED_TO_SOFT_DELETE_CHANNEL = "Failed to soft delete the channel"
    internal const val MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY =
        "Moderation restrictions can only be set by clients initialized with a Secret Key."
    internal const val STORE_USER_ACTIVITY_INTERVAL_SHOULD_BE_AT_LEAST_1_MIN =
        "storeUserActivityInterval must be at least 60000ms"
    internal const val APNS_TOPIC_SHOULD_BE_DEFINED_WHEN_DEVICE_GATEWAY_IS_SET_TO_APNS2 =
        "apnsTopic has to be defined when deviceGateway is set to apns2"
    internal const val NO_SUCH_MEMBERSHIP_EXISTS = "No such membership exists"
    internal const val ID_IS_REQUIRED = "Id is required"
    internal const val CHANNEL_ID_IS_REQUIRED = "Channel Id is required"
    internal const val COUNT_SHOULD_NOT_EXCEED_100 = "Count should not exceed 100"
    internal const val CHANNEL_NOT_FOUND = "Channel not found"
    internal const val DEVICE_TOKEN_HAS_TO_BE_DEFINED_IN_CHAT_PUSHNOTIFICATIONS_CONFIG = "Device Token has to be defined in Chat pushNotifications config."
    internal const val THERE_IS_NO_THREAD_TO_BE_DELETED = "There is no thread to be deleted."
    internal const val THERE_IS_NO_ACTION_TIMETOKEN_CORRESPONDING_TO_THE_THREAD = "There is no action timetoken corresponding to the thread."
    internal const val THERE_IS_NO_THREAD_WITH_ID = "There is no thread with id: "
    internal const val CAN_NOT_FIND_CHANNEL_WITH_ID = "Cannot find channel with id: "
    internal const val THIS_MESSAGE_IS_NOT_A_THREAD = "This message is not a thread."
    internal const val THREAD_CHANNEL_DOES_NOT_EXISTS = "The thread channel does not exist."
    internal const val PARENT_CHANNEL_DOES_NOT_EXISTS = "Parent channel doesn't exist."
    internal const val CAN_NOT_STREAM_CHANNEL_UPDATES_ON_EMPTY_LIST = "Cannot stream channel updates on an empty list."
    internal const val CAN_NOT_STREAM_MEMBERSHIP_UPDATES_ON_EMPTY_LIST = "Cannot stream membership updates on an empty list."
    internal const val CAN_NOT_STREAM_USER_UPDATES_ON_EMPTY_LIST = "Cannot stream user updates on an empty list."
    internal const val READ_RECEIPTS_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS = "Read receipts are not supported in Public chats."
    internal const val CANNOT_STREAM_MESSAGE_UPDATES_ON_EMPTY_LIST = "Cannot stream message updates on an empty list."
    internal const val ONLY_ONE_LEVEL_OF_THREAD_NESTING_IS_ALLOWED = "Only one level of thread nesting is allowed."
    internal const val YOU_CAN_NOT_CREATE_THREAD_ON_DELETED_MESSAGES = "You cannot create threads on deleted messages."
    internal const val THREAD_FOR_THIS_MESSAGE_ALREADY_EXISTS = "Thread for this message already exists."
    internal const val RECEIPT_EVENT_WAS_NOT_SENT_TO_CHANNEL = "Because PAM did not allow it 'receipt' event was not sent to channel: "
    internal const val ERROR_HANDLING_ONMESSAGE_EVENT = "Error handling onMessage event"
    internal const val THIS_MESSAGE_HAS_NOT_BEEN_DELETED = "This message has not been deleted"
    internal const val THIS_THREAD_ID_ALREADY_RESTORED = "This thread is already restored"
    internal const val KEY_IS_NOT_VALID_INTEGER = "Key is not a valid integer"
    internal const val ERROR_CALLING_DEFAULT_GET_MESSAGE_RESPONSE_BODY = "Error calling defaultGetMessageResponseBody:"
    internal const val CANNOT_QUOTE_MESSAGE_FROM_OTHER_CHANNELS = "You cannot quote messages from other channels"
    internal const val MENTION_SUGGESTION_INVALID = "This mention suggestion is no longer valid - the message draft text has been changed."
    internal const val MENTION_CANNOT_INTERSECT = "Cannot intersect with existing mention:"
    internal const val AUTOMODERATED_MESSAGE_CANNOT_BE_EDITED = "The automoderated message can no longer be edited"
}
