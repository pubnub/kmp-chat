package com.pubnub.kmp.error

enum class PubNubErrorMessage(val message: String) {
    TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS("Typing indicators are not supported in Public chats."),
    FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA("Failed to retrieve wherePresent data. "),
    FAILED_TO_RETRIEVE_IS_PRESENT_DATA("Failed to retrieve isPresent data."),
    FAILED_TO_RETRIEVE_HISTORY_DATA("Failed to retrieve getHistory data."),
    FAILED_TO_RETRIEVE_WHO_IS_PRESENT_DATA("Failed to retrieve whoIsPresent data."),
    FAILED_TO_CREATE_UPDATE_CHANNEL_DATA("Failed to create/update channel data."),
    FAILED_TO_CREATE_UPDATE_USER_DATA("Failed to create/update user data."),
    FAILED_TO_RETRIEVE_CHANNEL_DATA("Failed to retrieve channel data."),
    FAILED_TO_RETRIEVE_USER_DATA("Failed to retrieve user data."),
    FAILED_TO_RETRIEVE_GET_MEMBERSHIP_DATA("Failed to retrieve getMembership data."),
    FAILED_TO_GET_USERS("Failed to get users."),
    FAILED_TO_GET_CHANNELS("Failed to get channels."),
    FAILED_TO_FORWARD_MESSAGE("Failed to forward message."),
    CHANNEL_META_DATA_IS_EMPTY("Channel metadata is empty."),
    CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL("You cannot forward the message to the same channel."),
    FOR_PUBLISH_PAYLOAD_SHOULD_BE_OF_TYPE_TEXT_MESSAGE_CONTENT(
        "When emitEvent method is PUBLISH payload should be of type EventContent.TextMessageContent"
    ),
    CHANNEL_ID_ALREADY_EXIST("Channel with this ID already exists"),
    USER_ID_ALREADY_EXIST("User with this ID already exists"),
    CHANNEL_NOT_EXIST("Channel does not exist"),
    CHANNEL_ID_SHOULD_BE_DEFINED("Channel id should be defined"),
    USER_ID_SHOULD_BE_DEFINED("User id should be defined"),
    USER_NOT_EXIST("User does not exist"),
    FAILED_TO_SOFT_DELETE_CHANNEL("Failed to soft delete the channel"),
    FAILED_TO_DELETE_USER("Failed to delete the user"),
    FAILED_TO_UPDATE_USER_METADATA("Failed to update user metadata."),
    FAILED_TO_REMOVE_CHANNEL_MEMBERS("Failed to remove channel members."),
    FAILED_TO_SET_CHANNEL_MEMBERS("Failed to set channel members."),
    CHANNEL_INVITES_ARE_NOT_SUPPORTED_IN_PUBLIC_CHATS("Channel invites are not supported in Public chats."),
    MODERATION_CAN_BE_SET_ONLY_BY_CLIENT_HAVING_SECRET_KEY(
        "Moderation restrictions can only be set by clients initialized with a Secret Key."
    ),
    STORE_USER_ACTIVITY_INTERVAL_IS_FALSE(
        "storeUserActivityTimestamps config property is set to false so can not provide info about user being active"
    ),
    STORE_USER_ACTIVITY_INTERVAL_SHOULD_BE_AT_LEAST_1_MIN("storeUserActivityInterval must be at least 60000ms"),
    APNS_TOPIC_SHOULD_BE_DEFINED_WHEN_DEVICE_GATEWAY_IS_SET_TO_APNS2(
        "apnsTopic has to be defined when deviceGateway is set to apns2"
    ),
    ;

    override fun toString(): String {
        return message
    }
}
