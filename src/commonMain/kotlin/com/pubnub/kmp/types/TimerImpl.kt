package com.pubnub.kmp.types

import kotlinx.datetime.Clock

class TimerImpl : Timer {
    override fun getCurrentTimeStampInMillis(): Long {
        return Clock.System.now().toEpochMilliseconds()
    }
}
