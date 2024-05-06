package com.pubnub.kmp.models.consumers.objects

expect sealed class PNPage {
    abstract val pageHash: String
    class PNNext
    class PNPrev
}