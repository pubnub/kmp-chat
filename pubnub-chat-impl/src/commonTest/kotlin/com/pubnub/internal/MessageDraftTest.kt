package com.pubnub.internal

import com.pubnub.chat.Channel
import com.pubnub.chat.MessageDraft
import com.pubnub.chat.User
import com.pubnub.chat.internal.ChatInternal
import com.pubnub.chat.internal.MessageDraftImpl
import com.pubnub.chat.internal.UserImpl
import com.pubnub.chat.internal.channel.ChannelImpl
import com.pubnub.chat.internal.messageElements
import com.pubnub.kmp.asFuture
import com.pubnub.test.await
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

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
        every { chat.getUserSuggestions("exa", any()) } returns users.filter { it.name!!.startsWith("exa") }.toSet().asFuture()
        every { chat.getUserSuggestions("exam", any()) } returns users.filter { it.name!!.startsWith("exam") }.toSet().asFuture()
        every { chat.getUserSuggestions("examp", any()) } returns users.filter { it.name!!.startsWith("examp") }.toSet().asFuture()
        every { chat.getUserSuggestions("sam", any()) } returns users.filter { it.name!!.startsWith("sam") }.toSet().asFuture()
        every { chat.getUserSuggestions("samp", any()) } returns users.filter { it.name!!.startsWith("samp") }.toSet().asFuture()
        every { chat.getUserSuggestions("sampl", any()) } returns users.filter { it.name!!.startsWith("sampl") }.toSet().asFuture()

        every { chat.getChannelSuggestions("exa", any()) } returns channels.filter { it.name!!.startsWith("exa") }.toSet().asFuture()
        every { chat.getChannelSuggestions("exam", any()) } returns channels.filter { it.name!!.startsWith("exam") }.toSet().asFuture()
        every { chat.getChannelSuggestions("examp", any()) } returns channels.filter { it.name!!.startsWith("examp") }.toSet().asFuture()
        every { chat.getChannelSuggestions("sam", any()) } returns channels.filter { it.name!!.startsWith("sam") }.toSet().asFuture()
        every { chat.getChannelSuggestions("samp", any()) } returns channels.filter { it.name!!.startsWith("samp") }.toSet().asFuture()
        every { chat.getChannelSuggestions("sampl", any()) } returns channels.filter { it.name!!.startsWith("sampl") }.toSet().asFuture()
    }

//    @Test
//    fun testChannelSuggestions() = runTest {
//        val draft = MessageDraftImpl(channels.first(), MessageDraft.UserSuggestionSource.GLOBAL, isTypingIndicatorTriggered = false)
//        val suggestions = draft.update("abc @exa def 123").await()
// //        println(suggestions[4]!!.map { (it as SuggestedMention.SuggestedUserMention).user.name })
//        val suggestedUserMention = suggestions[4]!![1] as SuggestedMention
//        draft.insertSuggestedMention(suggestedUserMention, suggestedUserMention.user.name!!)
//        println(draft.render()) // TODO make assertions
//    }
//
//    @Test // TODO make assertions
//    fun testTextInsertsRemovals() = runTest {
//        val draft = MessageDraftImpl(channels.first(), MessageDraft.UserSuggestionSource.GLOBAL, isTypingIndicatorTriggered = false)
//        every { chat.getUserSuggestions(any(), any()) } returns emptySet<User>().asFuture()
//
//        draft.update("abc @exa def 123")
// //        draft.addMention(Mention.UserMention(4, 4, users.first().id))
//        println(draft.render())
//
//        draft.update("cr sd rl @exa def 123")
//        println(draft.render())
//
//        draft.update("cr sd rl @fd @exa def 123")
//        println(draft.render())
//
//        draft.update("cr sd rl @exa @exa def 123")
//        println(draft.render())
//    }

    @Test
    fun messageElementsEncodeDecode() = runTest {
        val draft = MessageDraftImpl(channels.first(), MessageDraft.UserSuggestionSource.GLOBAL, isTypingIndicatorTriggered = false)
        val suggestions = draft.update("abc @exa def 123").await()
        val suggestion = suggestions[4]!!.first()
        draft.insertSuggestedMention(suggestion, suggestion.replaceTo)

        val rendered = draft.render()
        assertEquals("abc [example User 0](pn-user://example.user.0) def 123", rendered)

        val draftElements = draft.getMessageElements()
        val renderedElements = messageElements(rendered)
        assertEquals(draftElements, renderedElements)
    }
}
