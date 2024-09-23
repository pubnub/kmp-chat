package com.pubnub.chat.types

import com.pubnub.chat.Event

/**
 * Represents the result of fetching the history of reported message events for a channel.
 *
 * @property events A set of [Event] objects containing [EventContent] representing the reported message events that were fetched from the history.
 * @property isMore A boolean indicating whether there are more events available beyond the current result set.
 */
class GetEventsHistoryResult(
    val events: Set<Event<EventContent>>,
    val isMore: Boolean
)
