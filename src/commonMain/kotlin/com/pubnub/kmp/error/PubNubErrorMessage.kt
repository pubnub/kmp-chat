package com.pubnub.kmp.error

enum class PubNubErrorMessage(val message: String) {
    TYPING_INDICATORS_NO_SUPPORTED_IN_PUBLIC_CHATS("Typing indicators are not supported in Public chats."),
    FAILED_TO_RETRIEVE_WHERE_PRESENT_DATA("Failed to retrieve wherePresent data: "),
    FAILED_TO_RETRIEVE_IS_PRESENT_DATA("Failed to retrieve isPresent data: "),
    FAILED_TO_UPDATE_CHANNEL_META_DATA("Failed to update channel metadata: "),
    CANNOT_FORWARD_MESSAGE_TO_THE_SAME_CHANNEL("You cannot forward the message to the same channel"),
    FOR_PUBLISH_PAYLOAD_SHOULD_BE_OF_TYPE_TEXT_MESSAGE_CONTENT("When emitEvent method is PUBLISH payload should be of type EventContent.TextMessageContent"),

    FAILED_TO_UPDATE_USER_METADATA("Failed to update user metadata: ")
}