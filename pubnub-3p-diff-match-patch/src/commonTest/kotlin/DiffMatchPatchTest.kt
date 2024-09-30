/*
 * Diff Match and Patch -- Test harness
 * Copyright 2018 The diff-match-patch Authors.
 * https://github.com/google/diff-match-patch
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package name.fraser.neil.plaintext

import kotlinx.datetime.Clock
import name.fraser.neil.plaintext.DiffMatchPatch.LinesToCharsResult
import kotlin.test.Test

class DiffMatchPatchTest {
    private val dmp: DiffMatchPatch = DiffMatchPatch()
    private val DELETE = DiffMatchPatch.Operation.DELETE
    private val EQUAL = DiffMatchPatch.Operation.EQUAL
    private val INSERT = DiffMatchPatch.Operation.INSERT


    //  DIFF TEST FUNCTIONS
    @Test
    fun testDiffCommonPrefix() {
        // Detect any common prefix.
        assertEquals("diff_commonPrefix: Null case.", 0, dmp.diff_commonPrefix("abc", "xyz"))

        assertEquals("diff_commonPrefix: Non-null case.", 4, dmp.diff_commonPrefix("1234abcdef", "1234xyz"))

        assertEquals("diff_commonPrefix: Whole case.", 4, dmp.diff_commonPrefix("1234", "1234xyz"))
    }

    @Test
    fun testDiffCommonSuffix() {
        // Detect any common suffix.
        assertEquals("diff_commonSuffix: Null case.", 0, dmp.diff_commonSuffix("abc", "xyz"))

        assertEquals("diff_commonSuffix: Non-null case.", 4, dmp.diff_commonSuffix("abcdef1234", "xyz1234"))

        assertEquals("diff_commonSuffix: Whole case.", 4, dmp.diff_commonSuffix("1234", "xyz1234"))
    }

    @Test
    fun testDiffCommonOverlap() {
        // Detect any suffix/prefix overlap.
        assertEquals("diff_commonOverlap: Null case.", 0, dmp.diff_commonOverlap("", "abcd"))

        assertEquals("diff_commonOverlap: Whole case.", 3, dmp.diff_commonOverlap("abc", "abcd"))

        assertEquals("diff_commonOverlap: No overlap.", 0, dmp.diff_commonOverlap("123456", "abcd"))

        assertEquals("diff_commonOverlap: Overlap.", 3, dmp.diff_commonOverlap("123456xxx", "xxxabcd"))

        // Some overly clever languages (C#) may treat ligatures as equal to their
        // component letters.  E.g. U+FB01 == 'fi'
        assertEquals("diff_commonOverlap: Unicode.", 0, dmp.diff_commonOverlap("fi", "\ufb01i"))
    }

    @Test
    fun testDiffHalfmatch() {
        // Detect a halfmatch.
        dmp.diffTimeout = 1f
        assertNull("diff_halfMatch: No match #1.", dmp.diff_halfMatch("1234567890", "abcdef"))

        assertNull("diff_halfMatch: No match #2.", dmp.diff_halfMatch("12345", "23"))

        assertArrayEquals(
            "diff_halfMatch: Single Match #1.",
            arrayOf<String>("12", "90", "a", "z", "345678"),
            dmp.diff_halfMatch("1234567890", "a345678z")
        )

        assertArrayEquals(
            "diff_halfMatch: Single Match #2.",
            arrayOf<String>("a", "z", "12", "90", "345678"),
            dmp.diff_halfMatch("a345678z", "1234567890")
        )

        assertArrayEquals(
            "diff_halfMatch: Single Match #3.",
            arrayOf<String>("abc", "z", "1234", "0", "56789"),
            dmp.diff_halfMatch("abc56789z", "1234567890")
        )

        assertArrayEquals(
            "diff_halfMatch: Single Match #4.",
            arrayOf<String>("a", "xyz", "1", "7890", "23456"),
            dmp.diff_halfMatch("a23456xyz", "1234567890")
        )

        assertArrayEquals(
            "diff_halfMatch: Multiple Matches #1.",
            arrayOf<String>("12123", "123121", "a", "z", "1234123451234"),
            dmp.diff_halfMatch("121231234123451234123121", "a1234123451234z")
        )

        assertArrayEquals(
            "diff_halfMatch: Multiple Matches #2.",
            arrayOf<String>("", "-=-=-=-=-=", "x", "", "x-=-=-=-=-=-=-="),
            dmp.diff_halfMatch("x-=-=-=-=-=-=-=-=-=-=-=-=", "xx-=-=-=-=-=-=-=")
        )

        assertArrayEquals(
            "diff_halfMatch: Multiple Matches #3.",
            arrayOf<String>("-=-=-=-=-=", "", "", "y", "-=-=-=-=-=-=-=y"),
            dmp.diff_halfMatch("-=-=-=-=-=-=-=-=-=-=-=-=y", "-=-=-=-=-=-=-=yy")
        )

        // Optimal diff would be -q+x=H-i+e=lloHe+Hu=llo-Hew+y not -qHillo+x=HelloHe-w+Hulloy
        assertArrayEquals(
            "diff_halfMatch: Non-optimal halfmatch.",
            arrayOf<String>("qHillo", "w", "x", "Hulloy", "HelloHe"),
            dmp.diff_halfMatch("qHilloHelloHew", "xHelloHeHulloy")
        )

        dmp.diffTimeout = 0f
        assertNull("diff_halfMatch: Optimal no halfmatch.", dmp.diff_halfMatch("qHilloHelloHew", "xHelloHeHulloy"))
    }

    @Test
    fun testDiffLinesToChars() {
        // Convert lines down to characters.
        val tmpVector = ArrayList<String>()
        tmpVector.add("")
        tmpVector.add("alpha\n")
        tmpVector.add("beta\n")
        assertLinesToCharsResultEquals(
            "diff_linesToChars: Shared lines.",
            LinesToCharsResult("\u0001\u0002\u0001", "\u0002\u0001\u0002", tmpVector),
            dmp.diff_linesToChars("alpha\nbeta\nalpha\n", "beta\nalpha\nbeta\n")
        )

        tmpVector.clear()
        tmpVector.add("")
        tmpVector.add("alpha\r\n")
        tmpVector.add("beta\r\n")
        tmpVector.add("\r\n")
        assertLinesToCharsResultEquals(
            "diff_linesToChars: Empty string and blank lines.",
            LinesToCharsResult("", "\u0001\u0002\u0003\u0003", tmpVector),
            dmp.diff_linesToChars("", "alpha\r\nbeta\r\n\r\n\r\n")
        )

        tmpVector.clear()
        tmpVector.add("")
        tmpVector.add("a")
        tmpVector.add("b")
        assertLinesToCharsResultEquals(
            "diff_linesToChars: No linebreaks.",
            LinesToCharsResult("\u0001", "\u0002", tmpVector),
            dmp.diff_linesToChars("a", "b")
        )

        // More than 256 to reveal any 8-bit limitations.
        val n = 300
        tmpVector.clear()
        val lineList = StringBuilder()
        val charList = StringBuilder()
        for (i in 1 until n + 1) {
            tmpVector.add(i.toString() + "\n")
            lineList.append(i.toString() + "\n")
            charList.append(i.toChar().toString())
        }
        assertEquals("Test initialization fail #1.", n, tmpVector.size)
        val lines = lineList.toString()
        val chars = charList.toString()
        assertEquals("Test initialization fail #2.", n, chars.length)
        tmpVector.add(0, "")
        assertLinesToCharsResultEquals(
            "diff_linesToChars: More than 256.",
            LinesToCharsResult(chars, "", tmpVector),
            dmp.diff_linesToChars(lines, "")
        )
    }

    @Test
    fun testDiffCharsToLines() {
        // First check that Diff equality works.
        assertTrue(
            "diff_charsToLines: Equality #1.", DiffMatchPatch.Diff(EQUAL, "a").equals(
                DiffMatchPatch.Diff(
                    EQUAL, "a"
                )
            )
        )

        assertEquals(
            "diff_charsToLines: Equality #2.",
            DiffMatchPatch.Diff(EQUAL, "a"),
            DiffMatchPatch.Diff(EQUAL, "a")
        )

        // Convert chars up to lines.
        var diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "\u0001\u0002\u0001"),
            DiffMatchPatch.Diff(INSERT, "\u0002\u0001\u0002")
        )
        val tmpVector = ArrayList<String>()
        tmpVector.add("")
        tmpVector.add("alpha\n")
        tmpVector.add("beta\n")
        dmp.diff_charsToLines(diffs, tmpVector)
        assertEquals(
            "diff_charsToLines: Shared lines.", diffList(
                DiffMatchPatch.Diff(EQUAL, "alpha\nbeta\nalpha\n"), DiffMatchPatch.Diff(
                    INSERT, "beta\nalpha\nbeta\n"
                )
            ), diffs
        )

        // More than 256 to reveal any 8-bit limitations.
        val n = 300
        tmpVector.clear()
        var lineList = StringBuilder()
        val charList = StringBuilder()
        for (i in 1 until n + 1) {
            tmpVector.add(i.toString() + "\n")
            lineList.append(i.toString() + "\n")
            charList.append(i.toChar().toString())
        }
        assertEquals("Test initialization fail #3.", n, tmpVector.size)
        val lines = lineList.toString()
        var chars = charList.toString()
        assertEquals("Test initialization fail #4.", n, chars.length)
        tmpVector.add(0, "")
        diffs = diffList(DiffMatchPatch.Diff(DELETE, chars))
        dmp.diff_charsToLines(diffs, tmpVector)
        assertEquals("diff_charsToLines: More than 256.", diffList(DiffMatchPatch.Diff(DELETE, lines)), diffs)

        // More than 65536 to verify any 16-bit limitation.
        lineList = StringBuilder()
        for (i in 0..65999) {
            lineList.append(i.toString() + "\n")
        }
        chars = lineList.toString()
        val results = dmp.diff_linesToChars(chars, "")
        diffs = diffList(DiffMatchPatch.Diff(INSERT, results.chars1))
        dmp.diff_charsToLines(diffs, results.lineArray)
        assertEquals("diff_charsToLines: More than 65536.", chars, diffs.first().text)
    }

    @Test
    fun testDiffCleanupMerge() {
        // Cleanup a messy diff.
        var diffs = diffList()
        dmp.diff_cleanupMerge(diffs)
        assertEquals("diff_cleanupMerge: Null case.", diffList(), diffs)

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(DELETE, "b"), DiffMatchPatch.Diff(
                INSERT, "c"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: No change case.", diffList(
                DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(
                    DELETE, "b"
                ), DiffMatchPatch.Diff(INSERT, "c")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(EQUAL, "b"), DiffMatchPatch.Diff(
                EQUAL, "c"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals("diff_cleanupMerge: Merge equalities.", diffList(DiffMatchPatch.Diff(EQUAL, "abc")), diffs)

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(DELETE, "b"), DiffMatchPatch.Diff(
                DELETE, "c"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals("diff_cleanupMerge: Merge deletions.", diffList(DiffMatchPatch.Diff(DELETE, "abc")), diffs)

        diffs = diffList(
            DiffMatchPatch.Diff(INSERT, "a"), DiffMatchPatch.Diff(INSERT, "b"), DiffMatchPatch.Diff(
                INSERT, "c"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals("diff_cleanupMerge: Merge insertions.", diffList(DiffMatchPatch.Diff(INSERT, "abc")), diffs)

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(INSERT, "b"), DiffMatchPatch.Diff(
                DELETE, "c"
            ), DiffMatchPatch.Diff(INSERT, "d"), DiffMatchPatch.Diff(EQUAL, "e"), DiffMatchPatch.Diff(
                EQUAL, "f"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Merge interweave.", diffList(
                DiffMatchPatch.Diff(DELETE, "ac"), DiffMatchPatch.Diff(
                    INSERT, "bd"
                ), DiffMatchPatch.Diff(EQUAL, "ef")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(INSERT, "abc"), DiffMatchPatch.Diff(
                DELETE, "dc"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Prefix and suffix detection.", diffList(
                DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(
                    DELETE, "d"
                ), DiffMatchPatch.Diff(INSERT, "b"), DiffMatchPatch.Diff(EQUAL, "c")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "x"), DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(
                INSERT, "abc"
            ), DiffMatchPatch.Diff(DELETE, "dc"), DiffMatchPatch.Diff(EQUAL, "y")
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Prefix and suffix detection with equalities.", diffList(
                DiffMatchPatch.Diff(
                    EQUAL, "xa"
                ), DiffMatchPatch.Diff(DELETE, "d"), DiffMatchPatch.Diff(INSERT, "b"), DiffMatchPatch.Diff(
                    EQUAL, "cy"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(INSERT, "ba"), DiffMatchPatch.Diff(
                EQUAL, "c"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Slide edit left.", diffList(
                DiffMatchPatch.Diff(INSERT, "ab"), DiffMatchPatch.Diff(
                    EQUAL, "ac"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "c"), DiffMatchPatch.Diff(INSERT, "ab"), DiffMatchPatch.Diff(
                EQUAL, "a"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Slide edit right.", diffList(
                DiffMatchPatch.Diff(EQUAL, "ca"), DiffMatchPatch.Diff(
                    INSERT, "ba"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(DELETE, "b"), DiffMatchPatch.Diff(
                EQUAL, "c"
            ), DiffMatchPatch.Diff(DELETE, "ac"), DiffMatchPatch.Diff(EQUAL, "x")
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Slide edit left recursive.", diffList(
                DiffMatchPatch.Diff(DELETE, "abc"), DiffMatchPatch.Diff(
                    EQUAL, "acx"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "x"), DiffMatchPatch.Diff(DELETE, "ca"), DiffMatchPatch.Diff(
                EQUAL, "c"
            ), DiffMatchPatch.Diff(DELETE, "b"), DiffMatchPatch.Diff(EQUAL, "a")
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Slide edit right recursive.", diffList(
                DiffMatchPatch.Diff(EQUAL, "xca"), DiffMatchPatch.Diff(
                    DELETE, "cba"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "b"), DiffMatchPatch.Diff(INSERT, "ab"), DiffMatchPatch.Diff(
                EQUAL, "c"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Empty merge.", diffList(
                DiffMatchPatch.Diff(INSERT, "a"), DiffMatchPatch.Diff(
                    EQUAL, "bc"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, ""), DiffMatchPatch.Diff(INSERT, "a"), DiffMatchPatch.Diff(
                EQUAL, "b"
            )
        )
        dmp.diff_cleanupMerge(diffs)
        assertEquals(
            "diff_cleanupMerge: Empty equality.", diffList(
                DiffMatchPatch.Diff(INSERT, "a"), DiffMatchPatch.Diff(
                    EQUAL, "b"
                )
            ), diffs
        )
    }

    @Test
    fun testDiffCleanupSemanticLossless() {
        // Slide diffs to match logical boundaries.
        var diffs = diffList()
        dmp.diff_cleanupSemanticLossless(diffs)
        assertEquals("diff_cleanupSemanticLossless: Null case.", diffList(), diffs)

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "AAA\r\n\r\nBBB"),
            DiffMatchPatch.Diff(INSERT, "\r\nDDD\r\n\r\nBBB"),
            DiffMatchPatch.Diff(
                EQUAL, "\r\nEEE"
            )
        )
        dmp.diff_cleanupSemanticLossless(diffs)
        assertEquals(
            "diff_cleanupSemanticLossless: Blank lines.", diffList(
                DiffMatchPatch.Diff(EQUAL, "AAA\r\n\r\n"), DiffMatchPatch.Diff(
                    INSERT, "BBB\r\nDDD\r\n\r\n"
                ), DiffMatchPatch.Diff(EQUAL, "BBB\r\nEEE")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "AAA\r\nBBB"),
            DiffMatchPatch.Diff(INSERT, " DDD\r\nBBB"),
            DiffMatchPatch.Diff(
                EQUAL, " EEE"
            )
        )
        dmp.diff_cleanupSemanticLossless(diffs)
        assertEquals(
            "diff_cleanupSemanticLossless: Line boundaries.", diffList(
                DiffMatchPatch.Diff(EQUAL, "AAA\r\n"), DiffMatchPatch.Diff(
                    INSERT, "BBB DDD\r\n"
                ), DiffMatchPatch.Diff(EQUAL, "BBB EEE")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "The c"), DiffMatchPatch.Diff(INSERT, "ow and the c"), DiffMatchPatch.Diff(
                EQUAL, "at."
            )
        )
        dmp.diff_cleanupSemanticLossless(diffs)
        assertEquals(
            "diff_cleanupSemanticLossless: Word boundaries.", diffList(
                DiffMatchPatch.Diff(EQUAL, "The "), DiffMatchPatch.Diff(
                    INSERT, "cow and the "
                ), DiffMatchPatch.Diff(EQUAL, "cat.")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "The-c"), DiffMatchPatch.Diff(INSERT, "ow-and-the-c"), DiffMatchPatch.Diff(
                EQUAL, "at."
            )
        )
        dmp.diff_cleanupSemanticLossless(diffs)
        assertEquals(
            "diff_cleanupSemanticLossless: Alphanumeric boundaries.", diffList(
                DiffMatchPatch.Diff(EQUAL, "The-"), DiffMatchPatch.Diff(
                    INSERT, "cow-and-the-"
                ), DiffMatchPatch.Diff(EQUAL, "cat.")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(
                EQUAL, "ax"
            )
        )
        dmp.diff_cleanupSemanticLossless(diffs)
        assertEquals(
            "diff_cleanupSemanticLossless: Hitting the start.", diffList(
                DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(
                    EQUAL, "aax"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "xa"), DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(
                EQUAL, "a"
            )
        )
        dmp.diff_cleanupSemanticLossless(diffs)
        assertEquals(
            "diff_cleanupSemanticLossless: Hitting the end.", diffList(
                DiffMatchPatch.Diff(EQUAL, "xaa"), DiffMatchPatch.Diff(
                    DELETE, "a"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "The xxx. The "),
            DiffMatchPatch.Diff(INSERT, "zzz. The "),
            DiffMatchPatch.Diff(
                EQUAL, "yyy."
            )
        )
        dmp.diff_cleanupSemanticLossless(diffs)
        assertEquals(
            "diff_cleanupSemanticLossless: Sentence boundaries.", diffList(
                DiffMatchPatch.Diff(EQUAL, "The xxx."), DiffMatchPatch.Diff(
                    INSERT, " The zzz."
                ), DiffMatchPatch.Diff(EQUAL, " The yyy.")
            ), diffs
        )
    }

    @Test
    fun testDiffCleanupSemantic() {
        // Cleanup semantically trivial equalities.
        var diffs = diffList()
        dmp.diff_cleanupSemantic(diffs)
        assertEquals("diff_cleanupSemantic: Null case.", diffList(), diffs)

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "ab"), DiffMatchPatch.Diff(INSERT, "cd"), DiffMatchPatch.Diff(
                EQUAL, "12"
            ), DiffMatchPatch.Diff(DELETE, "e")
        )
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: No elimination #1.", diffList(
                DiffMatchPatch.Diff(DELETE, "ab"), DiffMatchPatch.Diff(
                    INSERT, "cd"
                ), DiffMatchPatch.Diff(EQUAL, "12"), DiffMatchPatch.Diff(DELETE, "e")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "abc"), DiffMatchPatch.Diff(INSERT, "ABC"), DiffMatchPatch.Diff(
                EQUAL, "1234"
            ), DiffMatchPatch.Diff(DELETE, "wxyz")
        )
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: No elimination #2.", diffList(
                DiffMatchPatch.Diff(DELETE, "abc"), DiffMatchPatch.Diff(
                    INSERT, "ABC"
                ), DiffMatchPatch.Diff(EQUAL, "1234"), DiffMatchPatch.Diff(DELETE, "wxyz")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(EQUAL, "b"), DiffMatchPatch.Diff(
                DELETE, "c"
            )
        )
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: Simple elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "abc"), DiffMatchPatch.Diff(
                    INSERT, "b"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "ab"), DiffMatchPatch.Diff(EQUAL, "cd"), DiffMatchPatch.Diff(
                DELETE, "e"
            ), DiffMatchPatch.Diff(EQUAL, "f"), DiffMatchPatch.Diff(INSERT, "g")
        )
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: Backpass elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "abcdef"), DiffMatchPatch.Diff(
                    INSERT, "cdfg"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(INSERT, "1"), DiffMatchPatch.Diff(EQUAL, "A"), DiffMatchPatch.Diff(
                DELETE, "B"
            ), DiffMatchPatch.Diff(INSERT, "2"), DiffMatchPatch.Diff(EQUAL, "_"), DiffMatchPatch.Diff(
                INSERT, "1"
            ), DiffMatchPatch.Diff(EQUAL, "A"), DiffMatchPatch.Diff(DELETE, "B"), DiffMatchPatch.Diff(
                INSERT, "2"
            )
        )
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: Multiple elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "AB_AB"), DiffMatchPatch.Diff(
                    INSERT, "1A2_1A2"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "The c"), DiffMatchPatch.Diff(DELETE, "ow and the c"), DiffMatchPatch.Diff(
                EQUAL, "at."
            )
        )
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: Word boundaries.", diffList(
                DiffMatchPatch.Diff(EQUAL, "The "), DiffMatchPatch.Diff(
                    DELETE, "cow and the "
                ), DiffMatchPatch.Diff(EQUAL, "cat.")
            ), diffs
        )

        diffs = diffList(DiffMatchPatch.Diff(DELETE, "abcxx"), DiffMatchPatch.Diff(INSERT, "xxdef"))
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: No overlap elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "abcxx"), DiffMatchPatch.Diff(
                    INSERT, "xxdef"
                )
            ), diffs
        )

        diffs = diffList(DiffMatchPatch.Diff(DELETE, "abcxxx"), DiffMatchPatch.Diff(INSERT, "xxxdef"))
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: Overlap elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "abc"), DiffMatchPatch.Diff(
                    EQUAL, "xxx"
                ), DiffMatchPatch.Diff(INSERT, "def")
            ), diffs
        )

        diffs = diffList(DiffMatchPatch.Diff(DELETE, "xxxabc"), DiffMatchPatch.Diff(INSERT, "defxxx"))
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: Reverse overlap elimination.", diffList(
                DiffMatchPatch.Diff(INSERT, "def"), DiffMatchPatch.Diff(
                    EQUAL, "xxx"
                ), DiffMatchPatch.Diff(DELETE, "abc")
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "abcd1212"),
            DiffMatchPatch.Diff(INSERT, "1212efghi"),
            DiffMatchPatch.Diff(
                EQUAL, "----"
            ),
            DiffMatchPatch.Diff(DELETE, "A3"),
            DiffMatchPatch.Diff(INSERT, "3BC")
        )
        dmp.diff_cleanupSemantic(diffs)
        assertEquals(
            "diff_cleanupSemantic: Two overlap eliminations.", diffList(
                DiffMatchPatch.Diff(DELETE, "abcd"), DiffMatchPatch.Diff(
                    EQUAL, "1212"
                ), DiffMatchPatch.Diff(INSERT, "efghi"), DiffMatchPatch.Diff(EQUAL, "----"), DiffMatchPatch.Diff(
                    DELETE, "A"
                ), DiffMatchPatch.Diff(EQUAL, "3"), DiffMatchPatch.Diff(INSERT, "BC")
            ), diffs
        )
    }

    @Test
    fun testDiffCleanupEfficiency() {
        // Cleanup operationally trivial equalities.
        dmp.diffEditCost = 4
        var diffs = diffList()
        dmp.diff_cleanupEfficiency(diffs)
        assertEquals("diff_cleanupEfficiency: Null case.", diffList(), diffs)

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "ab"), DiffMatchPatch.Diff(INSERT, "12"), DiffMatchPatch.Diff(
                EQUAL, "wxyz"
            ), DiffMatchPatch.Diff(DELETE, "cd"), DiffMatchPatch.Diff(INSERT, "34")
        )
        dmp.diff_cleanupEfficiency(diffs)
        assertEquals(
            "diff_cleanupEfficiency: No elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "ab"), DiffMatchPatch.Diff(
                    INSERT, "12"
                ), DiffMatchPatch.Diff(EQUAL, "wxyz"), DiffMatchPatch.Diff(DELETE, "cd"), DiffMatchPatch.Diff(
                    INSERT, "34"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "ab"), DiffMatchPatch.Diff(INSERT, "12"), DiffMatchPatch.Diff(
                EQUAL, "xyz"
            ), DiffMatchPatch.Diff(DELETE, "cd"), DiffMatchPatch.Diff(INSERT, "34")
        )
        dmp.diff_cleanupEfficiency(diffs)
        assertEquals(
            "diff_cleanupEfficiency: Four-edit elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "abxyzcd"), DiffMatchPatch.Diff(
                    INSERT, "12xyz34"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(INSERT, "12"), DiffMatchPatch.Diff(EQUAL, "x"), DiffMatchPatch.Diff(
                DELETE, "cd"
            ), DiffMatchPatch.Diff(INSERT, "34")
        )
        dmp.diff_cleanupEfficiency(diffs)
        assertEquals(
            "diff_cleanupEfficiency: Three-edit elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "xcd"), DiffMatchPatch.Diff(
                    INSERT, "12x34"
                )
            ), diffs
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "ab"), DiffMatchPatch.Diff(INSERT, "12"), DiffMatchPatch.Diff(
                EQUAL, "xy"
            ), DiffMatchPatch.Diff(INSERT, "34"), DiffMatchPatch.Diff(EQUAL, "z"), DiffMatchPatch.Diff(
                DELETE, "cd"
            ), DiffMatchPatch.Diff(INSERT, "56")
        )
        dmp.diff_cleanupEfficiency(diffs)
        assertEquals(
            "diff_cleanupEfficiency: Backpass elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "abxyzcd"), DiffMatchPatch.Diff(
                    INSERT, "12xy34z56"
                )
            ), diffs
        )

        dmp.diffEditCost = 5
        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "ab"), DiffMatchPatch.Diff(INSERT, "12"), DiffMatchPatch.Diff(
                EQUAL, "wxyz"
            ), DiffMatchPatch.Diff(DELETE, "cd"), DiffMatchPatch.Diff(INSERT, "34")
        )
        dmp.diff_cleanupEfficiency(diffs)
        assertEquals(
            "diff_cleanupEfficiency: High cost elimination.", diffList(
                DiffMatchPatch.Diff(DELETE, "abwxyzcd"), DiffMatchPatch.Diff(
                    INSERT, "12wxyz34"
                )
            ), diffs
        )
        dmp.diffEditCost = 4
    }

    @Test
    fun testDiffPrettyHtml() {
        // Pretty print.
        val diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a\n"), DiffMatchPatch.Diff(DELETE, "<B>b</B>"), DiffMatchPatch.Diff(
                INSERT, "c&d"
            )
        )
        assertEquals(
            "diff_prettyHtml:",
            "<span>a&para;<br></span><del style=\"background:#ffe6e6;\">&lt;B&gt;b&lt;/B&gt;</del><ins style=\"background:#e6ffe6;\">c&amp;d</ins>",
            dmp.diff_prettyHtml(diffs)
        )
    }

    @Test
    fun testDiffText() {
        // Compute the source and destination texts.
        val diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "jump"), DiffMatchPatch.Diff(DELETE, "s"), DiffMatchPatch.Diff(
                INSERT, "ed"
            ), DiffMatchPatch.Diff(EQUAL, " over "), DiffMatchPatch.Diff(DELETE, "the"), DiffMatchPatch.Diff(
                INSERT, "a"
            ), DiffMatchPatch.Diff(EQUAL, " lazy")
        )
        assertEquals("diff_text1:", "jumps over the lazy", dmp.diff_text1(diffs))
        assertEquals("diff_text2:", "jumped over a lazy", dmp.diff_text2(diffs))
    }

    @Test
    fun testDiffXIndex() {
        // Translate a location in text1 to text2.
        var diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(INSERT, "1234"), DiffMatchPatch.Diff(
                EQUAL, "xyz"
            )
        )
        assertEquals("diff_xIndex: Translation on equality.", 5, dmp.diff_xIndex(diffs, 2))

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(DELETE, "1234"), DiffMatchPatch.Diff(
                EQUAL, "xyz"
            )
        )
        assertEquals("diff_xIndex: Translation on deletion.", 1, dmp.diff_xIndex(diffs, 3))
    }

    @Test
    fun testDiffLevenshtein() {
        var diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "abc"), DiffMatchPatch.Diff(INSERT, "1234"), DiffMatchPatch.Diff(
                EQUAL, "xyz"
            )
        )
        assertEquals("diff_levenshtein: Levenshtein with trailing equality.", 4, dmp.diff_levenshtein(diffs))

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "xyz"), DiffMatchPatch.Diff(DELETE, "abc"), DiffMatchPatch.Diff(
                INSERT, "1234"
            )
        )
        assertEquals("diff_levenshtein: Levenshtein with leading equality.", 4, dmp.diff_levenshtein(diffs))

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "abc"), DiffMatchPatch.Diff(EQUAL, "xyz"), DiffMatchPatch.Diff(
                INSERT, "1234"
            )
        )
        assertEquals("diff_levenshtein: Levenshtein with middle equality.", 7, dmp.diff_levenshtein(diffs))
    }

    @Test
    fun testDiffBisect() {
        // Normal.
        val a = "cat"
        val b = "map"
        // Since the resulting diff hasn't been normalized, it would be ok if
        // the insertion and deletion pairs are swapped.
        // If the order changes, tweak this test as required.
        var diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "c"), DiffMatchPatch.Diff(INSERT, "m"), DiffMatchPatch.Diff(
                EQUAL, "a"
            ), DiffMatchPatch.Diff(DELETE, "t"), DiffMatchPatch.Diff(INSERT, "p")
        )
        assertEquals("diff_bisect: Normal.", diffs, dmp.diff_bisect(a, b, Long.MAX_VALUE))

        // Timeout.
        diffs = diffList(DiffMatchPatch.Diff(DELETE, "cat"), DiffMatchPatch.Diff(INSERT, "map"))
        assertEquals("diff_bisect: Timeout.", diffs, dmp.diff_bisect(a, b, 0))
    }

    @Test
    fun testDiffMain() {
        // Perform a trivial diff.
        var diffs = diffList()
        assertEquals("diff_main: Null case.", diffs, dmp.diff_main("", "", false))

        diffs = diffList(DiffMatchPatch.Diff(EQUAL, "abc"))
        assertEquals("diff_main: Equality.", diffs, dmp.diff_main("abc", "abc", false))

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "ab"), DiffMatchPatch.Diff(INSERT, "123"), DiffMatchPatch.Diff(
                EQUAL, "c"
            )
        )
        assertEquals("diff_main: Simple insertion.", diffs, dmp.diff_main("abc", "ab123c", false))

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(DELETE, "123"), DiffMatchPatch.Diff(
                EQUAL, "bc"
            )
        )
        assertEquals("diff_main: Simple deletion.", diffs, dmp.diff_main("a123bc", "abc", false))

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(INSERT, "123"), DiffMatchPatch.Diff(
                EQUAL, "b"
            ), DiffMatchPatch.Diff(INSERT, "456"), DiffMatchPatch.Diff(EQUAL, "c")
        )
        assertEquals("diff_main: Two insertions.", diffs, dmp.diff_main("abc", "a123b456c", false))

        diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(DELETE, "123"), DiffMatchPatch.Diff(
                EQUAL, "b"
            ), DiffMatchPatch.Diff(DELETE, "456"), DiffMatchPatch.Diff(EQUAL, "c")
        )
        assertEquals("diff_main: Two deletions.", diffs, dmp.diff_main("a123b456c", "abc", false))

        // Perform a real diff.
        // Switch off the timeout.
        dmp.diffTimeout = 0f
        diffs = diffList(DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(INSERT, "b"))
        assertEquals("diff_main: Simple case #1.", diffs, dmp.diff_main("a", "b", false))

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "Apple"), DiffMatchPatch.Diff(INSERT, "Banana"), DiffMatchPatch.Diff(
                EQUAL, "s are a"
            ), DiffMatchPatch.Diff(INSERT, "lso"), DiffMatchPatch.Diff(EQUAL, " fruit.")
        )
        assertEquals(
            "diff_main: Simple case #2.",
            diffs,
            dmp.diff_main("Apples are a fruit.", "Bananas are also fruit.", false)
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "a"), DiffMatchPatch.Diff(INSERT, "\u0680"), DiffMatchPatch.Diff(
                EQUAL, "x"
            ), DiffMatchPatch.Diff(DELETE, "\t"), DiffMatchPatch.Diff(INSERT, "\u0000")
        )
        assertEquals("diff_main: Simple case #3.", diffs, dmp.diff_main("ax\t", "\u0680x\u0000", false))

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "1"), DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(
                DELETE, "y"
            ), DiffMatchPatch.Diff(EQUAL, "b"), DiffMatchPatch.Diff(DELETE, "2"), DiffMatchPatch.Diff(
                INSERT, "xab"
            )
        )
        assertEquals("diff_main: Overlap #1.", diffs, dmp.diff_main("1ayb2", "abxab", false))

        diffs = diffList(
            DiffMatchPatch.Diff(INSERT, "xaxcx"), DiffMatchPatch.Diff(EQUAL, "abc"), DiffMatchPatch.Diff(
                DELETE, "y"
            )
        )
        assertEquals("diff_main: Overlap #2.", diffs, dmp.diff_main("abcy", "xaxcxabc", false))

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "ABCD"),
            DiffMatchPatch.Diff(EQUAL, "a"),
            DiffMatchPatch.Diff(
                DELETE, "="
            ),
            DiffMatchPatch.Diff(INSERT, "-"),
            DiffMatchPatch.Diff(EQUAL, "bcd"),
            DiffMatchPatch.Diff(
                DELETE, "="
            ),
            DiffMatchPatch.Diff(INSERT, "-"),
            DiffMatchPatch.Diff(EQUAL, "efghijklmnopqrs"),
            DiffMatchPatch.Diff(
                DELETE, "EFGHIJKLMNOefg"
            )
        )
        assertEquals(
            "diff_main: Overlap #3.",
            diffs,
            dmp.diff_main("ABCDa=bcd=efghijklmnopqrsEFGHIJKLMNOefg", "a-bcd-efghijklmnopqrs", false)
        )

        diffs = diffList(
            DiffMatchPatch.Diff(INSERT, " "), DiffMatchPatch.Diff(EQUAL, "a"), DiffMatchPatch.Diff(
                INSERT, "nd"
            ), DiffMatchPatch.Diff(EQUAL, " [[Pennsylvania]]"), DiffMatchPatch.Diff(DELETE, " and [[New")
        )
        assertEquals(
            "diff_main: Large equality.",
            diffs,
            dmp.diff_main("a [[Pennsylvania]] and [[New", " and [[Pennsylvania]]", false)
        )

        dmp.diffTimeout = 0.1f // 100ms
        var a =
            "`Twas brillig, and the slithy toves\nDid gyre and gimble in the wabe:\nAll mimsy were the borogoves,\nAnd the mome raths outgrabe.\n"
        var b =
            "I am the very model of a modern major general,\nI've information vegetable, animal, and mineral,\nI know the kings of England, and I quote the fights historical,\nFrom Marathon to Waterloo, in order categorical.\n"
        // Increase the text lengths by 1024 times to ensure a timeout.
        for (i in 0..9) {
            a += a
            b += b
        }
        val startTime = Clock.System.now().toEpochMilliseconds()
        dmp.diff_main(a, b)
        val endTime = Clock.System.now().toEpochMilliseconds()
        // Test that we took at least the timeout period.
        assertTrue("diff_main: Timeout min.", dmp.diffTimeout * 1000 <= endTime - startTime)
        // Test that we didn't take forever (be forgiving).
        // Theoretically this test could fail very occasionally if the
        // OS task swaps or locks up for a second at the wrong moment.
        assertTrue("diff_main: Timeout max.", dmp.diffTimeout * 1000 * 2 > endTime - startTime)
        dmp.diffTimeout = 0f

        // Test the linemode speedup.
        // Must be long to pass the 100 char cutoff.
        a =
            "1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n"
        b =
            "abcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\nabcdefghij\n"
        assertEquals("diff_main: Simple line-mode.", dmp.diff_main(a, b, true), dmp.diff_main(a, b, false))

        a =
            "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
        b =
            "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghij"
        assertEquals("diff_main: Single line-mode.", dmp.diff_main(a, b, true), dmp.diff_main(a, b, false))

        a =
            "1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n1234567890\n"
        b =
            "abcdefghij\n1234567890\n1234567890\n1234567890\nabcdefghij\n1234567890\n1234567890\n1234567890\nabcdefghij\n1234567890\n1234567890\n1234567890\nabcdefghij\n"
        val texts_linemode = diff_rebuildtexts(dmp.diff_main(a, b, true))
        val texts_textmode = diff_rebuildtexts(dmp.diff_main(a, b, false))
        assertArrayEquals("diff_main: Overlap line-mode.", texts_textmode, texts_linemode)

        // Test null inputs.
//        try {
//            dmp.diff_main(null, null)
//            fail("diff_main: Null inputs.")
//        } catch (ex: IllegalArgumentException) {
//            // Error expected.
//        }
    }


    //  MATCH TEST FUNCTIONS
    @Test
    fun testMatchBitap() {
        // Bitap algorithm.
        dmp.matchDistance = 100
        dmp.matchThreshold = 0.5f
        assertEquals("match_bitap: Exact match #1.", 5, dmp.match_bitap("abcdefghijk", "fgh", 5))

        assertEquals("match_bitap: Exact match #2.", 5, dmp.match_bitap("abcdefghijk", "fgh", 0))

        assertEquals("match_bitap: Fuzzy match #1.", 4, dmp.match_bitap("abcdefghijk", "efxhi", 0))

        assertEquals("match_bitap: Fuzzy match #2.", 2, dmp.match_bitap("abcdefghijk", "cdefxyhijk", 5))

        assertEquals("match_bitap: Fuzzy match #3.", -1, dmp.match_bitap("abcdefghijk", "bxy", 1))

        assertEquals("match_bitap: Overflow.", 2, dmp.match_bitap("123456789xx0", "3456789x0", 2))

        assertEquals("match_bitap: Before start match.", 0, dmp.match_bitap("abcdef", "xxabc", 4))

        assertEquals("match_bitap: Beyond end match.", 3, dmp.match_bitap("abcdef", "defyy", 4))

        assertEquals("match_bitap: Oversized pattern.", 0, dmp.match_bitap("abcdef", "xabcdefy", 0))

        dmp.matchThreshold = 0.4f
        assertEquals("match_bitap: Threshold #1.", 4, dmp.match_bitap("abcdefghijk", "efxyhi", 1))

        dmp.matchThreshold = 0.3f
        assertEquals("match_bitap: Threshold #2.", -1, dmp.match_bitap("abcdefghijk", "efxyhi", 1))

        dmp.matchThreshold = 0.0f
        assertEquals("match_bitap: Threshold #3.", 1, dmp.match_bitap("abcdefghijk", "bcdef", 1))

        dmp.matchThreshold = 0.5f
        assertEquals("match_bitap: Multiple select #1.", 0, dmp.match_bitap("abcdexyzabcde", "abccde", 3))

        assertEquals("match_bitap: Multiple select #2.", 8, dmp.match_bitap("abcdexyzabcde", "abccde", 5))

        dmp.matchDistance = 10 // Strict location.
        assertEquals(
            "match_bitap: Distance test #1.",
            -1,
            dmp.match_bitap("abcdefghijklmnopqrstuvwxyz", "abcdefg", 24)
        )

        assertEquals(
            "match_bitap: Distance test #2.",
            0,
            dmp.match_bitap("abcdefghijklmnopqrstuvwxyz", "abcdxxefg", 1)
        )

        dmp.matchDistance = 1000 // Loose location.
        assertEquals(
            "match_bitap: Distance test #3.",
            0,
            dmp.match_bitap("abcdefghijklmnopqrstuvwxyz", "abcdefg", 24)
        )
    }

    @Test
    fun testMatchMain() {
        // Full match.
        assertEquals("match_main: Equality.", 0, dmp.match_main("abcdef", "abcdef", 1000))

        assertEquals("match_main: Null text.", -1, dmp.match_main("", "abcdef", 1))

        assertEquals("match_main: Null pattern.", 3, dmp.match_main("abcdef", "", 3))

        assertEquals("match_main: Exact match.", 3, dmp.match_main("abcdef", "de", 3))

        assertEquals("match_main: Beyond end match.", 3, dmp.match_main("abcdef", "defy", 4))

        assertEquals("match_main: Oversized pattern.", 0, dmp.match_main("abcdef", "abcdefy", 0))

        dmp.matchThreshold = 0.7f
        assertEquals(
            "match_main: Complex match.",
            4,
            dmp.match_main("I am the very model of a modern major general.", " that berry ", 5)
        )
        dmp.matchThreshold = 0.5f
    }


    //  PATCH TEST FUNCTIONS
    @Test
    fun testPatchObj() {
        // Patch Object.
        val p = DiffMatchPatch.Patch()
        p.start1 = 20
        p.start2 = 21
        p.length1 = 18
        p.length2 = 17
        p.diffs = diffList(
            DiffMatchPatch.Diff(EQUAL, "jump"), DiffMatchPatch.Diff(DELETE, "s"), DiffMatchPatch.Diff(
                INSERT, "ed"
            ), DiffMatchPatch.Diff(EQUAL, " over "), DiffMatchPatch.Diff(DELETE, "the"), DiffMatchPatch.Diff(
                INSERT, "a"
            ), DiffMatchPatch.Diff(EQUAL, "\nlaz")
        )
        val strp = """
            @@ -21,18 +22,17 @@
             jump
            -s
            +ed
              over 
            -the
            +a
             
            laz

        """.trimIndent()
        assertEquals("Patch: toString.", strp, p.toString())
    }

    @Suppress("deprecation")
    @Test
    fun testPatchMake() {
        var patches: MutableList<DiffMatchPatch.Patch>?
        patches = dmp.patch_make("", "")
        assertEquals("patch_make: Null case.", "", dmp.patch_toText(patches))

        var text1 = "The quick brown fox jumps over the lazy dog."
        var text2 = "That quick brown fox jumped over a lazy dog."
        var expectedPatch =
            "@@ -1,8 +1,7 @@\n Th\n-at\n+e\n  qui\n@@ -21,17 +21,18 @@\n jump\n-ed\n+s\n  over \n-a\n+the\n  laz\n"
        // The second patch must be "-21,17 +21,18", not "-22,17 +21,18" due to rolling context.
        patches = dmp.patch_make(text2, text1)
        assertEquals("patch_make: Text2+Text1 inputs.", expectedPatch, dmp.patch_toText(patches))

        expectedPatch =
            "@@ -1,11 +1,12 @@\n Th\n-e\n+at\n  quick b\n@@ -22,18 +22,17 @@\n jump\n-s\n+ed\n  over \n-the\n+a\n  laz\n"
        patches = dmp.patch_make(text1, text2)
        assertEquals("patch_make: Text1+Text2 inputs.", expectedPatch, dmp.patch_toText(patches))

        var diffs = dmp.diff_main(text1, text2, false)
        patches = dmp.patch_make(diffs)
        assertEquals("patch_make: Diff input.", expectedPatch, dmp.patch_toText(patches))

        patches = dmp.patch_make(text1, diffs)
        assertEquals("patch_make: Text1+Diff inputs.", expectedPatch, dmp.patch_toText(patches))

        patches = dmp.patch_make(text1, text2, diffs)
        assertEquals("patch_make: Text1+Text2+Diff inputs (deprecated).", expectedPatch, dmp.patch_toText(patches))

        patches = dmp.patch_make("`1234567890-=[]\\;',./", "~!@#$%^&*()_+{}|:\"<>?")
        assertEquals(
            "patch_toText: Character encoding.",
            """
                @@ -1,21 +1,21 @@
                -`1234567890-=[]\;',./
                +~!@#${'$'}%^&*()_+{}|:"<>?

            """.trimIndent(),
            dmp.patch_toText(patches)
        )

        diffs = diffList(
            DiffMatchPatch.Diff(DELETE, "`1234567890-=[]\\;',./"),
            DiffMatchPatch.Diff(INSERT, "~!@#$%^&*()_+{}|:\"<>?")
        )
//        assertEquals(
//            "patch_fromText: Character decoding.",
//            diffs,
//            dmp.patch_fromText("@@ -1,21 +1,21 @@\n-%601234567890-=%5B%5D%5C;',./\n+~!@#$%25%5E&*()_+%7B%7D%7C:%22%3C%3E?\n")[0].diffs
//        )

        text1 = ""
        for (x in 0..99) {
            text1 += "abcdef"
        }
        text2 = text1 + "123"
        expectedPatch = "@@ -573,28 +573,31 @@\n cdefabcdefabcdefabcdefabcdef\n+123\n"
        patches = dmp.patch_make(text1, text2)
        assertEquals("patch_make: Long string with repeats.", expectedPatch, dmp.patch_toText(patches))
    }

    @Test
    fun testPatchSplitMax() {
        // Assumes that Match_MaxBits is 32.
        var patches: MutableList<DiffMatchPatch.Patch>
        patches = dmp.patch_make(
            "abcdefghijklmnopqrstuvwxyz01234567890",
            "XabXcdXefXghXijXklXmnXopXqrXstXuvXwxXyzX01X23X45X67X89X0"
        )
        dmp.patch_splitMax(patches)
        assertEquals(
            "patch_splitMax: #1.",
            "@@ -1,32 +1,46 @@\n+X\n ab\n+X\n cd\n+X\n ef\n+X\n gh\n+X\n ij\n+X\n kl\n+X\n mn\n+X\n op\n+X\n qr\n+X\n st\n+X\n uv\n+X\n wx\n+X\n yz\n+X\n 012345\n@@ -25,13 +39,18 @@\n zX01\n+X\n 23\n+X\n 45\n+X\n 67\n+X\n 89\n+X\n 0\n",
            dmp.patch_toText(patches)
        )

        patches = dmp.patch_make(
            "abcdef1234567890123456789012345678901234567890123456789012345678901234567890uvwxyz",
            "abcdefuvwxyz"
        )
        val oldToText = dmp.patch_toText(patches)
        dmp.patch_splitMax(patches)
        assertEquals("patch_splitMax: #2.", oldToText, dmp.patch_toText(patches))

        patches = dmp.patch_make("1234567890123456789012345678901234567890123456789012345678901234567890", "abc")
        dmp.patch_splitMax(patches)
        assertEquals(
            "patch_splitMax: #3.",
            "@@ -1,32 +1,4 @@\n-1234567890123456789012345678\n 9012\n@@ -29,32 +1,4 @@\n-9012345678901234567890123456\n 7890\n@@ -57,14 +1,3 @@\n-78901234567890\n+abc\n",
            dmp.patch_toText(patches)
        )

        patches = dmp.patch_make(
            "abcdefghij , h : 0 , t : 1 abcdefghij , h : 0 , t : 1 abcdefghij , h : 0 , t : 1",
            "abcdefghij , h : 1 , t : 1 abcdefghij , h : 1 , t : 1 abcdefghij , h : 0 , t : 1"
        )
        dmp.patch_splitMax(patches)
        assertEquals(
            "patch_splitMax: #4.",
            "@@ -2,32 +2,32 @@\n bcdefghij , h : \n-0\n+1\n  , t : 1 abcdef\n@@ -29,32 +29,32 @@\n bcdefghij , h : \n-0\n+1\n  , t : 1 abcdef\n",
            dmp.patch_toText(patches)
        )
    }

    @Test
    fun testMatchAlphabet() {
        val bitmask: MutableMap<Char?, Int?> = mutableMapOf()
        bitmask['a'] = 4
        bitmask['b'] = 2
        bitmask['c'] = 1
        assertEquals("match_alphabet: Unique.", bitmask, dmp.match_alphabet("abc"))

        // Initialise the bitmasks for Bitap.
        bitmask['a'] = 37
        bitmask['b'] = 18
        bitmask['c'] = 8
        assertEquals("match_alphabet: Duplicates.", bitmask, dmp.match_alphabet("abcaba"))
    }

    @Test
    fun testPatchAddPadding() {
        var patches: MutableList<DiffMatchPatch.Patch>
        patches = dmp.patch_make("", "test")
        assertEquals("patch_addPadding: Both edges full.", "@@ -0,0 +1,4 @@\n+test\n", dmp.patch_toText(patches))
        dmp.patch_addPadding(patches)
        assertEquals(
            "patch_addPadding: Both edges full.",
            """
                @@ -1,8 +1,12 @@
                 
                +test
                 

            """.trimIndent(),
            dmp.patch_toText(patches)
        )

        patches = dmp.patch_make("XY", "XtestY")
        assertEquals(
            "patch_addPadding: Both edges partial.",
            "@@ -1,2 +1,6 @@\n X\n+test\n Y\n",
            dmp.patch_toText(patches)
        )
        dmp.patch_addPadding(patches)
        assertEquals(
            "patch_addPadding: Both edges partial.",
            """
                @@ -2,8 +2,12 @@
                 X
                +test
                 Y

            """.trimIndent(),
            dmp.patch_toText(patches)
        )

        patches = dmp.patch_make("XXXXYYYY", "XXXXtestYYYY")
        assertEquals(
            "patch_addPadding: Both edges none.",
            "@@ -1,8 +1,12 @@\n XXXX\n+test\n YYYY\n",
            dmp.patch_toText(patches)
        )
        dmp.patch_addPadding(patches)
        assertEquals(
            "patch_addPadding: Both edges none.",
            "@@ -5,8 +5,12 @@\n XXXX\n+test\n YYYY\n",
            dmp.patch_toText(patches)
        )
    }

    @Test
    fun testPatchApply() {
        dmp.matchDistance = 1000
        dmp.matchThreshold = 0.5f
        dmp.patchDeleteThreshold = 0.5f
        var patches: MutableList<DiffMatchPatch.Patch>
        patches = dmp.patch_make("", "")
        var results = dmp.patch_apply(patches, "Hello world.")
        var boolArray = results[1] as BooleanArray
        var resultStr = results[0].toString() + "\t" + boolArray.size
        assertEquals("patch_apply: Null case.", "Hello world.\t0", resultStr)

        patches = dmp.patch_make(
            "The quick brown fox jumps over the lazy dog.",
            "That quick brown fox jumped over a lazy dog."
        )
        results = dmp.patch_apply(patches, "The quick brown fox jumps over the lazy dog.")
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0] + "\t" + boolArray[1]
        assertEquals("patch_apply: Exact match.", "That quick brown fox jumped over a lazy dog.\ttrue\ttrue", resultStr)

        results = dmp.patch_apply(patches, "The quick red rabbit jumps over the tired tiger.")
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0] + "\t" + boolArray[1]
        assertEquals(
            "patch_apply: Partial match.",
            "That quick red rabbit jumped over a tired tiger.\ttrue\ttrue",
            resultStr
        )

        results = dmp.patch_apply(patches, "I am the very model of a modern major general.")
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0] + "\t" + boolArray[1]
        assertEquals(
            "patch_apply: Failed match.",
            "I am the very model of a modern major general.\tfalse\tfalse",
            resultStr
        )

        patches = dmp.patch_make("x1234567890123456789012345678901234567890123456789012345678901234567890y", "xabcy")
        results = dmp.patch_apply(
            patches,
            "x123456789012345678901234567890-----++++++++++-----123456789012345678901234567890y"
        )
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0] + "\t" + boolArray[1]
        assertEquals("patch_apply: Big delete, small change.", "xabcy\ttrue\ttrue", resultStr)

        patches = dmp.patch_make("x1234567890123456789012345678901234567890123456789012345678901234567890y", "xabcy")
        results = dmp.patch_apply(
            patches,
            "x12345678901234567890---------------++++++++++---------------12345678901234567890y"
        )
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0] + "\t" + boolArray[1]
        assertEquals(
            "patch_apply: Big delete, big change 1.",
            "xabc12345678901234567890---------------++++++++++---------------12345678901234567890y\tfalse\ttrue",
            resultStr
        )

        dmp.patchDeleteThreshold = 0.6f
        patches = dmp.patch_make("x1234567890123456789012345678901234567890123456789012345678901234567890y", "xabcy")
        results = dmp.patch_apply(
            patches,
            "x12345678901234567890---------------++++++++++---------------12345678901234567890y"
        )
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0] + "\t" + boolArray[1]
        assertEquals("patch_apply: Big delete, big change 2.", "xabcy\ttrue\ttrue", resultStr)
        dmp.patchDeleteThreshold = 0.5f

        // Compensate for failed patch.
        dmp.matchThreshold = 0.0f
        dmp.matchDistance = 0
        patches = dmp.patch_make(
            "abcdefghijklmnopqrstuvwxyz--------------------1234567890",
            "abcXXXXXXXXXXdefghijklmnopqrstuvwxyz--------------------1234567YYYYYYYYYY890"
        )
        results = dmp.patch_apply(patches, "ABCDEFGHIJKLMNOPQRSTUVWXYZ--------------------1234567890")
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0] + "\t" + boolArray[1]
        assertEquals(
            "patch_apply: Compensate for failed patch.",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ--------------------1234567YYYYYYYYYY890\tfalse\ttrue",
            resultStr
        )
        dmp.matchThreshold = 0.5f
        dmp.matchDistance = 1000

        patches = dmp.patch_make("", "test")
        var patchStr = dmp.patch_toText(patches)
        dmp.patch_apply(patches, "")
        assertEquals("patch_apply: No side effects.", patchStr, dmp.patch_toText(patches))

        patches = dmp.patch_make("The quick brown fox jumps over the lazy dog.", "Woof")
        patchStr = dmp.patch_toText(patches)
        dmp.patch_apply(patches, "The quick brown fox jumps over the lazy dog.")
        assertEquals("patch_apply: No side effects with major delete.", patchStr, dmp.patch_toText(patches))

        patches = dmp.patch_make("", "test")
        results = dmp.patch_apply(patches, "")
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0]
        assertEquals("patch_apply: Edge exact match.", "test\ttrue", resultStr)

        patches = dmp.patch_make("XY", "XtestY")
        results = dmp.patch_apply(patches, "XY")
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0]
        assertEquals("patch_apply: Near edge exact match.", "XtestY\ttrue", resultStr)

        patches = dmp.patch_make("y", "y123")
        results = dmp.patch_apply(patches, "x")
        boolArray = results[1] as BooleanArray
        resultStr = results[0].toString() + "\t" + boolArray[0]
        assertEquals("patch_apply: Edge partial match.", "x123\ttrue", resultStr)
    }

    private fun assertEquals(error_msg: String, a: Any, b: Any) {
        kotlin.test.assertEquals(a, b, error_msg)
//        if (a.toString() != b.toString()) {
//            throw Error(
//                ("""assertEquals fail:
// Expected: $a
// Actual: $b
//$error_msg""")
//            )
//        }
    }

    private fun assertTrue(error_msg: String, a: Boolean) {
        if (!a) {
            throw Error("assertTrue fail: $error_msg")
        }
    }

    private fun assertNull(error_msg: String, n: Any?) {
        if (n != null) {
            throw Error("assertNull fail: $error_msg")
        }
    }

    private fun fail(error_msg: String) {
        throw Error("Fail: $error_msg")
    }

    private fun assertArrayEquals(error_msg: String, a: Array<out Any>?, b: Array<out Any>?) {
        val list_a = a?.asList()
        val list_b = b?.asList()
        requireNotNull(list_a)
        requireNotNull(list_b)
        assertEquals(error_msg, list_a, list_b)
    }

    private fun assertLinesToCharsResultEquals(
        error_msg: String,
        a: LinesToCharsResult, b: LinesToCharsResult,
    ) {
        assertEquals(error_msg, a.chars1, b.chars1)
        assertEquals(error_msg, a.chars2, b.chars2)
        assertEquals(error_msg, a.lineArray, b.lineArray)
    }

    // Construct the two texts which made up the diff originally.
    private fun diff_rebuildtexts(diffs: MutableList<DiffMatchPatch.Diff>): Array<String> {
        val text = arrayOf("", "")
        for (myDiff in diffs) {
            if (myDiff.operation != DiffMatchPatch.Operation.INSERT) {
                text[0] += myDiff.text
            }
            if (myDiff.operation != DiffMatchPatch.Operation.DELETE) {
                text[1] += myDiff.text
            }
        }
        return text
    }

    // Private function for quickly building lists of diffs.
    private fun diffList(vararg diffs: DiffMatchPatch.Diff): MutableList<DiffMatchPatch.Diff> {
        return diffs.asList().toMutableList()
    }
}