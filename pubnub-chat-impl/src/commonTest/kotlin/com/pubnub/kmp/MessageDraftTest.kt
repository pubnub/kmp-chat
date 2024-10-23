package com.pubnub.kmp

import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.chat.Channel
import com.pubnub.chat.MentionTarget
import com.pubnub.chat.MessageDraft
import com.pubnub.chat.MessageDraftChangeListener
import com.pubnub.chat.MessageElement
import com.pubnub.chat.SuggestedMention
import com.pubnub.chat.User
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.Mention
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.findChannelMentionMatches
import com.pubnub.chat.internal.findUserMentionMatches
import com.pubnub.test.await
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifyNoMoreCalls
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MessageDraftTest {
    val chat = mock<ChatInternal>(MockMode.strict)
    val channels: List<Channel> = Array<Channel>(10) { index ->
        val name = if (index < 5) {
            "example"
        } else {
            "sample"
        }
        ChannelImpl(chat, id = "$name.channel.$index", name = "$name Channel $index")
    }.toList()

    val users: List<User> = Array<User>(10) { index ->
        val name = if (index < 5) {
            "example"
        } else {
            "sample"
        }
        UserImpl(chat, id = "$name.user.$index", name = "$name User $index")
    }.toList()

    init {
        every { chat.getUserSuggestions("exa", any()) } returns users.filter { it.name!!.startsWith("exa") }.toSet()
            .asFuture()
        every { chat.getUserSuggestions("exam", any()) } returns users.filter { it.name!!.startsWith("exam") }.toSet()
            .asFuture()
        every { chat.getUserSuggestions("examp", any()) } returns users.filter { it.name!!.startsWith("examp") }.toSet()
            .asFuture()
        every { chat.getUserSuggestions("sam", any()) } returns users.filter { it.name!!.startsWith("sam") }.toSet()
            .asFuture()
        every { chat.getUserSuggestions("samp", any()) } returns users.filter { it.name!!.startsWith("samp") }.toSet()
            .asFuture()
        every { chat.getUserSuggestions("sampl", any()) } returns users.filter { it.name!!.startsWith("sampl") }.toSet()
            .asFuture()

        every { chat.getChannelSuggestions("exa", any()) } returns channels.filter { it.name!!.startsWith("exa") }
            .toSet().asFuture()
        every { chat.getChannelSuggestions("exam", any()) } returns channels.filter { it.name!!.startsWith("exam") }
            .toSet().asFuture()
        every { chat.getChannelSuggestions("examp", any()) } returns channels.filter { it.name!!.startsWith("examp") }
            .toSet().asFuture()
        every { chat.getChannelSuggestions("sam", any()) } returns channels.filter { it.name!!.startsWith("sam") }
            .toSet().asFuture()
        every { chat.getChannelSuggestions("samp", any()) } returns channels.filter { it.name!!.startsWith("samp") }
            .toSet().asFuture()
        every { chat.getChannelSuggestions("sampl", any()) } returns channels.filter { it.name!!.startsWith("sampl") }
            .toSet().asFuture()
    }

    @Test
    fun listeners_add_fire_remove() = runTest {
        val draft = MessageDraftImpl(channels.first(), isTypingIndicatorTriggered = false)
        val result = CompletableDeferred<Unit>()
        val result2 = CompletableDeferred<Unit>()

        val listener = MessageDraftChangeListener { _, _ ->
            if (!result.isCompleted) {
                result.complete(Unit)
            } else {
                result2.complete(Unit)
            }
        }
        draft.addChangeListener(listener)
        draft.update("aaa")
        result.await()
        draft.removeChangeListener(listener)
        draft.update("bbb")
        assertFalse { result2.isCompleted }
    }

    @Test
    fun isTypingIndicatorFired_true() {
        every {
            chat.emitEvent(
                any(),
                any(),
                any()
            )
        } returns PNFuture { it.accept(Result.success(PNPublishResult(123))) }
        val draft = MessageDraftImpl(channels.first(), isTypingIndicatorTriggered = true)
        draft.update("aaa")
        verify { chat.emitEvent(channels.first().id, any(), any()) }
    }

    @Test
    fun isTypingIndicatorFired_false() {
        every {
            chat.emitEvent(
                any(),
                any(),
                any()
            )
        } returns PNFuture { it.accept(Result.success(PNPublishResult(123))) }
        val draft = MessageDraftImpl(channels.first(), isTypingIndicatorTriggered = false)
        draft.update("aaa")
        verifyNoMoreCalls(chat)
    }

    @Test
    fun insertText() {
        val draft = MessageDraftImpl(channels.first(), isTypingIndicatorTriggered = false)
        val list = mutableListOf<List<MessageElement>>()
        draft.addChangeListener { messageElements, suggestedMentions ->
            list.add(messageElements)
        }

        draft.insertText(0, "aaaa")
        draft.addMention(2, 2, MentionTarget.User("abc"))
        draft.insertText(2, "bbb")
        draft.insertText(6, "ccc")

        assertEquals(
            listOf(
                listOf<MessageElement>(MessageElement.PlainText("aaaa")),
                listOf<MessageElement>(
                    MessageElement.PlainText("aa"),
                    MessageElement.Link("aa", MentionTarget.User("abc"))
                ),
                listOf<MessageElement>(
                    MessageElement.PlainText("aabbb"),
                    MessageElement.Link("aa", MentionTarget.User("abc"))
                ),
                listOf<MessageElement>(MessageElement.PlainText("aabbbaccca")),
            ),
            list
        )
    }

    @Test
    fun removeText() {
        val draft = MessageDraftImpl(channels.first(), isTypingIndicatorTriggered = false)
        val list = mutableListOf<List<MessageElement>>()
        draft.addChangeListener { messageElements, suggestedMentions ->
            list.add(messageElements)
        }

        draft.insertText(0, "aabbbaccc")
        draft.addMention(2, 2, MentionTarget.User("abc"))

        draft.removeText(0, 2)
        draft.removeText(1, 2)

        assertEquals(
            listOf(
                listOf<MessageElement>(MessageElement.PlainText("aabbbaccc")),
                listOf<MessageElement>(
                    MessageElement.PlainText("aa"),
                    MessageElement.Link("bb", MentionTarget.User("abc")),
                    MessageElement.PlainText("baccc")
                ),
                listOf<MessageElement>(
                    MessageElement.Link("bb", MentionTarget.User("abc")),
                    MessageElement.PlainText("baccc")
                ),
                listOf<MessageElement>(MessageElement.PlainText("baccc")),
            ),
            list
        )
    }

    @Test
    fun update() {
        val draft = MessageDraftImpl(channels.first(), isTypingIndicatorTriggered = false)
        val list = mutableListOf<List<MessageElement>>()
        draft.addChangeListener { messageElements, suggestedMentions ->
            list.add(messageElements)
        }

        draft.update("a bb")
        draft.addMention(2, 2, MentionTarget.User("abc"))
        draft.update("dd bb a bb ba")
        draft.update("aabbbaccc")
        draft.update("aaaccc")

        assertEquals(
            listOf(
                listOf<MessageElement>(MessageElement.PlainText("a bb")),
                listOf<MessageElement>(
                    MessageElement.PlainText("a "),
                    MessageElement.Link("bb", MentionTarget.User("abc"))
                ),
                listOf<MessageElement>(
                    MessageElement.PlainText("dd bb a "),
                    MessageElement.Link("bb", MentionTarget.User("abc")),
                    MessageElement.PlainText(" ba")
                ),
                listOf<MessageElement>(MessageElement.PlainText("aabbbaccc")),
                listOf<MessageElement>(MessageElement.PlainText("aaaccc")),
            ),
            list
        )
    }

    @Test
    fun render() {
        // middle mention
        val messageElements1 = listOf(
            MessageElement.PlainText("abc "),
            MessageElement.Link("example user 0", MentionTarget.User("example.user.0")),
            MessageElement.PlainText(" def 123 "),
        )
        assertEquals(
            "abc [example user 0](pn-user://example.user.0) def 123 ",
            MessageDraftImpl.render(messageElements1)
        )

        // last mention
        val messageElements2 = listOf(
            MessageElement.PlainText("abc "),
            MessageElement.Link("example user 0", MentionTarget.User("example.user.0")),
            MessageElement.PlainText(" def 123 "),
            MessageElement.Link("example chan 0", MentionTarget.Channel("example.chan.0")),
        )
        assertEquals(
            "abc [example user 0](pn-user://example.user.0) def 123 [example chan 0](pn-channel://example.chan.0)",
            MessageDraftImpl.render(messageElements2)
        )

        // first mention
        val messageElements3 = listOf(
            MessageElement.Link("link", MentionTarget.Url("http://aaa")),
            MessageElement.PlainText(" abc "),
            MessageElement.Link("example user 0", MentionTarget.User("example.user.0")),
            MessageElement.PlainText(" def 123 "),
            MessageElement.Link("example chan 0", MentionTarget.Channel("example.chan.0")),
        )
        assertEquals(
            "[link](http://aaa) abc [example user 0](pn-user://example.user.0) def 123 [example chan 0](pn-channel://example.chan.0)",
            MessageDraftImpl.render(messageElements3)
        )
    }

    @Test
    fun getMessageElements_from_text() {
        // middle mention
        val text1 = "abc [example user 0](pn-user://example.user.0) def 123 "
        val messageElements1 = listOf(
            MessageElement.PlainText("abc "),
            MessageElement.Link("example user 0", MentionTarget.User("example.user.0")),
            MessageElement.PlainText(" def 123 "),
        )
        assertEquals(messageElements1, MessageDraftImpl.getMessageElements(text1))

        // last mention
        val text2 =
            "abc [example user 0](pn-user://example.user.0) def 123 [example chan 0](pn-channel://example.chan.0)"
        val messageElements2 = listOf(
            MessageElement.PlainText("abc "),
            MessageElement.Link("example user 0", MentionTarget.User("example.user.0")),
            MessageElement.PlainText(" def 123 "),
            MessageElement.Link("example chan 0", MentionTarget.Channel("example.chan.0")),
        )

        assertEquals(messageElements2, MessageDraftImpl.getMessageElements(text2))

        // first mention
        val text3 =
            "[link](http://aaa) abc [example user 0](pn-user://example.user.0) def 123 [example chan 0](pn-channel://example.chan.0)"
        val messageElements3 = listOf(
            MessageElement.Link("link", MentionTarget.Url("http://aaa")),
            MessageElement.PlainText(" abc "),
            MessageElement.Link("example user 0", MentionTarget.User("example.user.0")),
            MessageElement.PlainText(" def 123 "),
            MessageElement.Link("example chan 0", MentionTarget.Channel("example.chan.0")),
        )

        assertEquals(messageElements3, MessageDraftImpl.getMessageElements(text3))
    }

    @Test
    fun getMessageElements_from_mentions() {
        // middle mention
        val text1 = "abc example user 0 def 123 "
        val mentions1 = listOf(Mention(4, 14, MentionTarget.User("example.user.0")))
        val messageElements1 = listOf(
            MessageElement.PlainText("abc "),
            MessageElement.Link("example user 0", MentionTarget.User("example.user.0")),
            MessageElement.PlainText(" def 123 "),
        )
        assertEquals(messageElements1, MessageDraftImpl.getMessageElements(text1, mentions1))

        // last mention
        val text2 = "abc example user 0 def 123 example chan 0"
        val mentions2 = listOf(
            Mention(4, 14, MentionTarget.User("example.user.0")),
            Mention(27, 14, MentionTarget.Channel("example.chan.0")),
        )
        val messageElements2 = listOf(
            MessageElement.PlainText("abc "),
            MessageElement.Link("example user 0", MentionTarget.User("example.user.0")),
            MessageElement.PlainText(" def 123 "),
            MessageElement.Link("example chan 0", MentionTarget.Channel("example.chan.0")),
        )

        assertEquals(messageElements2, MessageDraftImpl.getMessageElements(text2, mentions2))
    }

    @Test
    fun escaping_link_text() {
        val escaped = listOf(
            """http://www.example.com/abc\]fkdo""",
            """http://www.example.com/[abc\]f)kdo\]""",
        )
        val unescaped = listOf(
            """http://www.example.com/abc]fkdo""",
            """http://www.example.com/[abc]f)kdo]""",
        )
        escaped.zip(unescaped).forEach {
            assertEquals(it.first, MessageDraftImpl.escapeLinkText(it.second))
            assertEquals(it.second, MessageDraftImpl.unEscapeLinkText(it.first))
        }
    }

    @Test
    fun escaping_link_urls() {
        val escaped = listOf(
            """http://www.example.com/abc\)fkdo""",
            """http://www.example.com/(abc\)f]kdo\)""",
        )
        val unescaped = listOf(
            """http://www.example.com/abc)fkdo""",
            """http://www.example.com/(abc)f]kdo)""",
        )
        escaped.zip(unescaped).forEach {
            assertEquals(it.first, MessageDraftImpl.escapeLinkUrl(it.second))
            assertEquals(it.second, MessageDraftImpl.unEscapeLinkUrl(it.first))
        }
    }

    @Test
    fun testSuggestions() = runTest {
        val draft = MessageDraftImpl(
            channels.first(),
            MessageDraft.UserSuggestionSource.GLOBAL,
            isTypingIndicatorTriggered = false
        )
        val result = CompletableDeferred<List<SuggestedMention>>()
        draft.addChangeListener { messageElements, suggestedMentions ->
            backgroundScope.launch {
                if (!result.isCompleted) {
                    result.complete(suggestedMentions.await())
                }
            }
        }

        draft.update("abc @exa def 123")
        val suggestion = result.await().first()
        draft.insertSuggestedMention(suggestion, suggestion.replaceWith)

        assertEquals("@exa", suggestion.replaceFrom)
        assertEquals("example User 0", suggestion.replaceWith)
        assertEquals("example.user.0", (suggestion.target as? MentionTarget.User)?.userId)

        assertEquals(
            listOf(
                MessageElement.PlainText("abc "),
                MessageElement.Link("example User 0", MentionTarget.User("example.user.0")),
                MessageElement.PlainText(" def 123")
            ),
            draft.getMessageElements()
        )
    }

    @Test
    fun test_user_mention_regexes() {
        val stringsToExpectedMatches = listOf(
            "@user" to "@user",
            "@us()er" to "@us",
            "@user @fsjdoif" to "@user",
            "@123aaa @user" to "@user",
            "@user aaa" to "@user",
            "aaa @user aaa" to "@user",
            "aaa @user," to "@user",
            "aaa @user-user aaa" to "@user-user",
            "@user-user" to "@user-user",
            "@brzęczy" to "@brzęczy",
            "@ąśćżć" to "@ąśćżć",
        )

        stringsToExpectedMatches.forEach {
            assertEquals(it.second, findUserMentionMatches(it.first).first().value)
        }
    }

    @Test
    fun test_channel_mention_regexes() {
        val stringsToExpectedMatches = listOf(
            "#user" to "#user",
            "#us()er" to "#us",
            "#user #fsjdoif" to "#user",
            "#123aaa #user" to "#123aaa",
            "#user aaa" to "#user",
            "aaa #user aaa" to "#user",
            "aaa #user," to "#user",
            "aaa #user-user aaa" to "#user-user",
            "#user-user" to "#user-user",
            "#brzęczy" to "#brzęczy",
            "#ąśćżć" to "#ąśćżć",
        )

        stringsToExpectedMatches.forEach {
            assertEquals(it.second, findChannelMentionMatches(it.first).first().value)
        }
    }
}
