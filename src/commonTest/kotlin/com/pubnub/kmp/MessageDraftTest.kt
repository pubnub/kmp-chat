package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.UserId
import com.pubnub.api.endpoints.objects.channel.GetAllChannelMetadata
import com.pubnub.api.endpoints.objects.member.GetChannelMembers
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadata
import com.pubnub.api.models.consumer.objects.channel.PNChannelMetadataArrayResult
import com.pubnub.api.models.consumer.objects.member.PNMember
import com.pubnub.api.models.consumer.objects.member.PNMemberArrayResult
import com.pubnub.api.models.consumer.objects.uuid.PNUUIDMetadata
import com.pubnub.api.v2.callbacks.Consumer
import com.pubnub.api.v2.callbacks.Result
import com.pubnub.api.v2.createPNConfiguration
import com.pubnub.kmp.channel.ChannelImpl
import com.pubnub.kmp.message.MessageImpl
import com.pubnub.kmp.message_draft.MessageDraft
import com.pubnub.kmp.message_draft.UserSuggestionDataSource
import com.pubnub.kmp.types.EventContent
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MessageDraftTest {
    private val channelMembers = listOf("Marian Salazar", "Markus Koller", "Mary Jonson")
    private val channelSuggestions = listOf("Games", "General", "Game of Thrones")
    private lateinit var chat: Chat

    @Test
    fun checkingSuggestedChannelsAndUsers() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.onSuggestionsChanged = { _, suggestedMentions ->
            val suggestedUsers = suggestedMentions.users[messageDraft.currentText.indexOf("@")]?.map { it.name }
            val suggestedChannels = suggestedMentions.channels[messageDraft.currentText.indexOf("#")]?.map { it.name }
            assertEquals(channelMembers, suggestedUsers)
            assertEquals(channelSuggestions, suggestedChannels)
        }
        messageDraft.currentText = "Hey, @$userBriefName here. Please add me to the #$channelBriefName channel"
    }

    @Test
    fun addingMentionedUserAndChannel() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "I'm @$userBriefName and I would like to join the #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        assertEquals(messageDraft.currentText, "I'm @$userFullName and I would like to join the #$channelBriefName channel")
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        assertEquals(messageDraft.currentText, "I'm @$userFullName and I would like to join the #$channelFullName channel")

        val preview = messageDraft.getMessagePreview()
        assertEquals(preview.mentionedUsers[messageDraft.currentText.indexOf("@")]?.name, userFullName)
        assertEquals(preview.mentionedChannels[messageDraft.currentText.indexOf("#")]?.name, channelFullName)
    }

    @Test
    fun removingMentionedUserAndChannel() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "I'm @$userBriefName and I would like to join the #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        assertEquals(messageDraft.currentText, "I'm @$userFullName and I would like to join the #$channelFullName channel")

        messageDraft.removeMentionedUser(messageDraft.currentText.indexOf("@"))
        messageDraft.removeMentionedChannel(messageDraft.currentText.indexOf("#"))
        assertTrue(messageDraft.getMessagePreview().mentionedUsers.isEmpty())
        assertTrue(messageDraft.getMessagePreview().mentionedChannels.isEmpty())
    }

    @Test
    fun addingMentionedUserAndChannelToInvalidPositions() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Thank you @$userBriefName for deciding to join our #$channelBriefName group"
        assertFailsWith<PubNubException> { messageDraft.addMentionedUser(userToMention, 20) }
        assertFailsWith<PubNubException> { messageDraft.addMentionedChannel(channelToMention, 105) }
        assertEquals(messageDraft.currentText, "Thank you @$userBriefName for deciding to join our #$channelBriefName group")
    }

    @Test
    fun alteringTextWithAlreadyMentionedUserAndChannel() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Hey all. I'm @$userBriefName and I would like to join #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        messageDraft.currentText = "Hey all. Nice to meet you. I'm @$userFullName who wants to join #$channelFullName channel"

        val messagePreview = messageDraft.getMessagePreview()
        assertEquals(messagePreview.mentionedUsers[messageDraft.currentText.indexOf("@")]!!.name, userFullName)
        assertEquals(messagePreview.mentionedChannels[messageDraft.currentText.indexOf("#")]!!.name, channelFullName)
    }

    @Test
    fun anotherTextAlteringWithAlreadyMentionedUserAndChannel() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Hello. My name is @$userBriefName and I need access to #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        messageDraft.currentText = "@$userFullName, have you been added to #$channelFullName?"

        val messagePreview = messageDraft.getMessagePreview()
        assertEquals(messagePreview.mentionedUsers[messageDraft.currentText.indexOf("@")]!!.name, userFullName)
        assertEquals(messagePreview.mentionedChannels[messageDraft.currentText.indexOf("#")]!!.name, channelFullName)
    }

    @Test
    fun mentionsThatNoLongerMatchDueToStringSubstitution() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()

        messageDraft.currentText = "Hello. My name is @$userBriefName and I'm the new marketing manager"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))

        messageDraft.currentText = "I would love to tell you about the strengths I can bring to this role"
        assertTrue(messageDraft.getMessagePreview().mentionedUsers.isEmpty())
        assertTrue(messageDraft.getMessagePreview().mentionedChannels.isEmpty())
    }

    @Test
    fun testAppendingAnotherMention() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val secondUserToMention = User(chat = chat, id = "Markus Koller", name = "Markus Koller")
        val secondUserFullName = secondUserToMention.name.orEmpty()
        val secondUserBriefName = secondUserFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "My name is @$userBriefName and I'm the new marketing manager"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.currentText = "I'm @$userFullName recommended by @$secondUserBriefName who I worked with before"
        messageDraft.addMentionedUser(secondUserToMention, messageDraft.currentText.lastIndexOf("@"))
        assertEquals(messageDraft.currentText, "I'm @$userFullName recommended by @$secondUserFullName who I worked with before")

        val messagePreview = messageDraft.getMessagePreview()
        assertEquals(messagePreview.mentionedUsers[messageDraft.currentText.indexOf("@")]?.name, userFullName)
        assertEquals(messagePreview.mentionedUsers[messageDraft.currentText.lastIndexOf("@")]?.name, secondUserFullName)
    }

    @Test
    fun messageDraftReQueryModifiedMention() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Hi, it's @$userBriefName, your new marketing manager"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        assertEquals(messageDraft.currentText, "Hi, it's @$userFullName, your new marketing manager")

        messageDraft.onSuggestionsChanged = { _, suggestions ->
            assertEquals(suggestions.users[messageDraft.currentText.indexOf("@")]?.map { it.name.orEmpty() }, channelMembers)
            assertEquals(suggestions.channels[messageDraft.currentText.indexOf("#")]?.map { it.name.orEmpty() }, channelSuggestions)
        }
        messageDraft.currentText = "Hi, it's @${userFullName.dropLast(3)}, your new marketing manager. Add me to the #$channelBriefName channel"
        assertTrue { messageDraft.getMessagePreview().mentionedUsers.isEmpty() }
    }

    @Test
    fun testOnSuggestionsChangedClosure() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.onSuggestionsChanged = { _, suggestions ->
            when (messageDraft.currentText) {
                "Hi, please welcome @$userBriefName" -> {
                    assertEquals(suggestions.users[19]?.map { it.name.orEmpty() }, channelMembers.map { it })
                    assertTrue(suggestions.channels.isEmpty())
                    messageDraft.currentText = "Hi, please welcome @$userBriefName and add her to the #$channelBriefName channel"
                }
                "Hi, please welcome @$userBriefName and add her to the #$channelBriefName channel" -> {
                    assertEquals(suggestions.users[messageDraft.currentText.indexOf("@")]?.map { it.name.orEmpty() }, channelMembers.map { it })
                    assertEquals(suggestions.channels[messageDraft.currentText.indexOf("#")]?.map { it.name.orEmpty() }, channelSuggestions.map { it })
                }
                else -> {
                    fail("Unexpected condition")
                }
            }
        }

        messageDraft.currentText = "Hi, please welcome @$userBriefName"
    }

    @Test
    fun getHighlightedMention() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userBriefName = userToMention.name.orEmpty().split(" ").first().trim()

        messageDraft.currentText = "Please welcome @$userBriefName in our team!"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))

        val highlightedMention = messageDraft.getHighlightedUserMention(messageDraft.currentText.indexOf("@"))
        val highlightedUser = highlightedMention?.user

        assertEquals(highlightedUser?.name, userToMention.name.orEmpty())
    }

    @Test
    fun getHighlightedMentionAtInvalidPosition() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val userBriefName = userToMention.name.orEmpty().split(" ").first().trim()

        messageDraft.currentText = "Please welcome @$userBriefName in our team!"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))

        val highlightedMention = messageDraft.getHighlightedUserMention(30)
        val highlightedUser = highlightedMention?.user

        assertNull(highlightedMention)
        assertNull(highlightedUser)
    }

    @Test
    fun addLinkedText() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val link = "https://www.pubnub.com"

        messageDraft.currentText = "This is example text. Visit $link to get more"

        val indexOfLink = messageDraft.currentText.indexOf(link)
        val replacement = "our website"

        messageDraft.addLinkedText(replacement, link, indexOfLink)
        assertEquals(messageDraft.currentText, "This is example text. Visit our website to get more")
        assertEquals(messageDraft.getMessagePreview().textLinks.first().link, "https://www.pubnub.com")
        assertEquals(messageDraft.getMessagePreview().textLinks.first().startIndex, indexOfLink)
        assertEquals(messageDraft.getMessagePreview().textLinks.first().endIndex, indexOfLink + replacement.length)
    }

    @Test
    fun addLinkedTextToTxtWithAlreadyMentionedItems() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val channelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.dropLast(3)

        val link = "https://www.pubnub.com"
        val replacement = "our website"

        messageDraft.currentText = "Check $link. @$userBriefName is the member of #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        assertEquals(messageDraft.currentText, "Check $link. @$userFullName is the member of #$channelFullName channel")
        messageDraft.addLinkedText(replacement, link, messageDraft.currentText.indexOf("https://www.pubnub.com"))
        assertEquals(messageDraft.currentText, "Check our website. @$userFullName is the member of #$channelFullName channel")

        val preview = messageDraft.getMessagePreview()
        assertEquals(preview.mentionedUsers[messageDraft.currentText.indexOf("@")]?.name, userFullName)
        assertEquals(preview.mentionedChannels[messageDraft.currentText.indexOf("#")]?.name, channelFullName)
    }

    @Test
    fun removeLinkedText() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val link = "https://www.pubnub.com"

        messageDraft.currentText = "This is example text. Visit $link to get more"

        val indexOfLink = messageDraft.currentText.indexOf(link)
        val replacement = "our website"

        messageDraft.addLinkedText(replacement, link, indexOfLink)
        assertEquals(messageDraft.currentText, "This is example text. Visit our website to get more")
        messageDraft.removeLinkedText(33)
        assertEquals(messageDraft.currentText, "This is example text. Visit our website to get more")
        assertTrue(messageDraft.getMessagePreview().textLinks.isEmpty())
    }

    @Test
    fun addQuotedMessage() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val message = MessageImpl(chat, 138327382910238921, EventContent.TextMessageContent("Hey!"), channel.id, "userId")

        messageDraft.addQuote(message)
        assertEquals(messageDraft.getMessagePreview().quotedMessage, message)
    }

    @Test
    fun removeQuotedMessage() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val message = MessageImpl(chat, 138327382910238921, EventContent.TextMessageContent("Hey!"), channel.id, "userId")

        messageDraft.addQuote(message)
        messageDraft.removeQuote()
        assertNull(messageDraft.getMessagePreview().quotedMessage)
    }
}

private fun MessageDraftTest.configureChat(
    withChannelMembers: List<String>,
    andChannelSuggestions: List<String> = emptyList()
): Chat {
    val pubnub: PubNub = mock(MockMode.strict)
    val getChannelMembers: GetChannelMembers = mock(MockMode.strict)
    val getChannelSuggestions: GetAllChannelMetadata = mock(MockMode.strict)

    every {
        pubnub.getChannelMembers(any(), any(), any(), any(), any(), any(), any(), any(), any())
    } returns getChannelMembers

    every {
        pubnub.getAllChannelMetadata(any(), any(), any(), any(), any(), any())
    } returns getChannelSuggestions

    every {
        getChannelMembers.async(any())
    }.calls { (it: Consumer<Result<PNMemberArrayResult>>) ->
        it.accept(
            Result.success(
                PNMemberArrayResult(
                    status = 200,
                    data = withChannelMembers.map {
                        PNMember(
                            uuid = PNUUIDMetadata(
                                id = it,
                                name = it,
                                externalId = null,
                                profileUrl = null,
                                email = null,
                                custom = null,
                                updated = null,
                                eTag = null,
                                type = null,
                                status = null
                            ),
                            eTag = "",
                            status = null,
                            updated = ""
                        )
                    },
                    totalCount = withChannelMembers.count(),
                    next = null,
                    prev = null
                )
            )
        )
    }

    every {
        getChannelSuggestions.async(any())
    }.calls { (it: Consumer<Result<PNChannelMetadataArrayResult>>) ->
        it.accept(
            Result.success(
                PNChannelMetadataArrayResult(
                    status = 200,
                    data = andChannelSuggestions.map {
                        PNChannelMetadata(
                            id = it,
                            name = it,
                            description = null,
                            custom = null,
                            updated = null,
                            eTag = null,
                            type = null,
                            status = null
                        )
                    },
                    totalCount = withChannelMembers.count(),
                    next = null,
                    prev = null
                )
            )
        )
    }

    return ChatImpl(
        config = ChatConfigImpl(createPNConfiguration(UserId("userId"), "subscribeKey", "publishKey")),
        pubNub = pubnub
    )
}
