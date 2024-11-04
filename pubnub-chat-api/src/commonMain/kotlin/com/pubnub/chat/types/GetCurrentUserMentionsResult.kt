package com.pubnub.chat.types

/**
 * Represents the result of fetching all instances where a specific user was mentioned in channels or threads.
 *
 * @property enhancedMentionsData A list of [UserMentionData] objects representing the details of each mention of the user.
 * @property isMore Indicates whether there are more mentions available beyond the current result set.
 */
class GetCurrentUserMentionsResult(val enhancedMentionsData: List<UserMentionData>, val isMore: Boolean)
