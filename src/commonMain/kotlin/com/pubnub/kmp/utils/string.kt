package com.pubnub.kmp.utils
fun String.isValidUrl(): Boolean {
    return Regex(
        pattern = """(https?://(?:www\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|
                 www\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\.[^\s]{2,}|
                 https?://(?:www\.|(?!www))[a-zA-Z0-9]+\.[^\s]{2,}|
                 www\.[a-zA-Z0-9]+\.[^\s]{2,})""".trimMargin(),
        options = setOf(
            RegexOption.IGNORE_CASE,
            RegexOption.MULTILINE
        )
    ).containsMatchIn(this)
}

fun String.indexOfDifference(other: String): Int? {
    val maxLength = maxOf(this.length, other.length)
    for (i in 0 until maxLength) {
        val char1 = this.getOrNull(i)
        val char2 = other.getOrNull(i)
        if (char1 != char2) {
            return i
        }
    }
    return null
}