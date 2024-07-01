package com.pubnub.kmp.utils

fun IntRange.shift(byStartOffset: Int, byEndOffset: Int): IntRange {
    return (start + byStartOffset)..(endInclusive + byEndOffset)
}