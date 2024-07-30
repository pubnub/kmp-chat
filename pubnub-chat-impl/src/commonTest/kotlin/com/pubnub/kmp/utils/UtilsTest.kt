package com.pubnub.kmp.utils

import com.pubnub.chat.internal.util.getPhraseToLookFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UtilsTest {
    @Test
    fun whenThereIsNoHashInTextShouldReturnNull() {
        val phraseWithoutHash = "noHahdfadaf"
        val result = getPhraseToLookFor(phraseWithoutHash, "#")
        assertNull(result)
    }

    @Test
    fun whenThereLessThan3CharAfterHashShouldReturnNull() {
        val phraseWithHash = "sas#h"
        val result = getPhraseToLookFor(phraseWithHash, "#")
        assertNull(result)
    }

    @Test
    fun whenThereAreMoreThanTwoWordsAfterHaskShouldReturnNull() {
        val phraseWithHashAnd3wordsAfter = "sas#one two three"
        val result = getPhraseToLookFor(phraseWithHashAnd3wordsAfter, "#")
        assertNull(result)
    }

    @Test
    fun shouldReturnOneWordWhenOneWordIsPresentAfterHash() {
        val oneWord = "one"
        val phraseWithHashAnd1wordAfter = "sas#$oneWord"
        val result = getPhraseToLookFor(phraseWithHashAnd1wordAfter, "#")
        assertEquals(oneWord, result)
    }

    @Test
    fun shouldReturnTwoWordsWhenTwoWordsArePresentAfterHash() {
        val firstWord = "one"
        val secondWord = "two"
        val phraseWithTwoWordsAfterHash = "sas#$firstWord $secondWord"
        val result = getPhraseToLookFor(phraseWithTwoWordsAfterHash, "#")
        assertEquals("$firstWord $secondWord", result)
    }

    @Test
    fun urlDecode() {
        val input = "a_-%3D%40.%21%24%23%25%26%5E%3B"
        val expected = "a_-=@.!\$#%&^;"

        assertEquals(expected, com.pubnub.chat.internal.util.urlDecode(input))
    }
}
