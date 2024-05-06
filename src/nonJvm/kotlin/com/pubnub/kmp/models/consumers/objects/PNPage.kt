package com.pubnub.kmp.models.consumers.objects

actual sealed class PNPage {
    actual abstract val pageHash: String

    actual data class PNNext(override val pageHash: String) : PNPage()

    actual data class PNPrev(override val pageHash: String) : PNPage()
}
