package com.pubnub.chat.types

import com.pubnub.chat.Event

class GetEventsHistoryResult(
    val events: Set<Event<EventContent>>,
    val isMore: Boolean
)
