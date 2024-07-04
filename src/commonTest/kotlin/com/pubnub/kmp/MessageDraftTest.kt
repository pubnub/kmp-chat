package com.pubnub.kmp

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
import com.pubnub.kmp.message_draft.MessageDraft
import com.pubnub.kmp.message_draft.UserSuggestionDataSource
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
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

        messageDraft.onSuggestionsChanged = { _, suggestedMentions ->
            assertEquals(channelMembers, suggestedMentions.users[5]?.map { it.name })
            assertEquals(channelSuggestions, suggestedMentions.channels[37]?.map { it.name })
        }
        messageDraft.currentText = "Hey, @Mar here. Please add me to the #Gam channel"
    }

    @Test
    fun addingMentionedUserAndChannel() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val maryJonson = User(chat = chat, id = "Mary Jonson", name = "Mary Jonson")
        val gamesChannel = ChannelImpl(chat = chat, id = "Games", name = "Games")

        messageDraft.currentText = "I'm @Mar and I would like to join the #Gam channel"
        messageDraft.addMentionedUser(maryJonson, 4)
        assertEquals(messageDraft.currentText, "I'm @Mary Jonson and I would like to join the #Gam channel")
        messageDraft.addMentionedChannel(gamesChannel, 46)
        assertEquals(messageDraft.currentText, "I'm @Mary Jonson and I would like to join the #Games channel")
    }

    @Test
    fun addingMentionedUserAndChannelToInvalidPositions() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val maryJonson = User(chat = chat, id = "Mary Jonson", name = "Mary Jonson")
        val gamesChannel = ChannelImpl(chat = chat, id = "Games", name = "Games")

        messageDraft.currentText = "Thank you @Mar for deciding to join our #Gam group"
        messageDraft.addMentionedUser(maryJonson, 20)
        messageDraft.addMentionedChannel(gamesChannel, 5)
        assertEquals(messageDraft.currentText, "Thank you @Mar for deciding to join our #Gam group")
    }

    @Test
    fun alteringTextWithAlreadyMentionedUserAndChannel() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val selectedUserToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val selectedUserFirstName = selectedUserToMention.name.orEmpty().split(" ").first().trim()
        val selectedUserFullName = selectedUserToMention.name.orEmpty()
        val selectedChannelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val selectedChannelBriefName = selectedChannelToMention.name.orEmpty().dropLast(3)
        val selectedChannelFullName = selectedChannelToMention.name.orEmpty()

        messageDraft.currentText = "Hey all. I'm @$selectedUserFirstName and I would like to join #$selectedChannelBriefName channel"
        messageDraft.addMentionedUser(selectedUserToMention, 13)
        assertEquals(messageDraft.currentText, "Hey all. I'm @$selectedUserFullName and I would like to join #$selectedChannelBriefName channel")
        messageDraft.addMentionedChannel(selectedChannelToMention, 54)
        assertEquals(messageDraft.currentText, "Hey all. I'm @$selectedUserFullName and I would like to join #$selectedChannelFullName channel")
        messageDraft.currentText = "Hey all. Nice to meet you. I'm @$selectedUserFullName who wants to join #$selectedChannelFullName channel"

        val messagePreview = messageDraft.getMessagePreview()
        assertEquals(messagePreview.mentionedUsers[31]!!.name, selectedUserFullName)
        assertEquals(messagePreview.referencedChannels[65]!!.name, selectedChannelFullName)
    }

    @Test
    fun anotherTextAlteringWithAlreadyMentionedUserAndChannel() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val selectedUserToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val selectedUserFirstName = selectedUserToMention.name.orEmpty().split(" ").first().trim()
        val selectedUserFullName = selectedUserToMention.name.orEmpty()
        val selectedChannelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val selectedChannelBriefName = selectedChannelToMention.name.orEmpty().dropLast(3)
        val selectedChannelFullName = selectedChannelToMention.name.orEmpty()

        messageDraft.currentText = "Hello. My name is @$selectedUserFirstName and I need access to #$selectedChannelBriefName channel"
        messageDraft.addMentionedUser(selectedUserToMention, 18)
        assertEquals(messageDraft.currentText, "Hello. My name is @$selectedUserFullName and I need access to #$selectedChannelBriefName channel")
        messageDraft.addMentionedChannel(selectedChannelToMention, 55)
        assertEquals(messageDraft.currentText, "Hello. My name is @$selectedUserFullName and I need access to #$selectedChannelFullName channel")
        messageDraft.currentText = "@$selectedUserFullName, have you been added to #$selectedChannelFullName?"

        val messagePreview = messageDraft.getMessagePreview()
        assertEquals(messagePreview.mentionedUsers[0]!!.name, selectedUserFullName)
        assertEquals(messagePreview.referencedChannels[40]!!.name, selectedChannelFullName)
    }

    @Test
    fun userMentionsThatNoLongerMatchDueToStringSubstitution() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val selectedUserToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val selectedUserFirstName = selectedUserToMention.name.orEmpty().split(" ").first().trim()
        val selectedUserFullName = selectedUserToMention.name.orEmpty()

        messageDraft.currentText = "Hello. My name is @$selectedUserFirstName and I'm the new marketing manager"
        messageDraft.addMentionedUser(selectedUserToMention, 18)
        assertEquals(messageDraft.currentText, "Hello. My name is @$selectedUserFullName and I'm the new marketing manager")

        messageDraft.currentText = "I would love to tell you about the strengths I can bring to this role"
        assertTrue(messageDraft.getMessagePreview().mentionedUsers.isEmpty())
        assertTrue(messageDraft.getMessagePreview().referencedChannels.isEmpty())
    }

    @Test
    fun testAppendingAnotherMention() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val firstSelUserToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val firstSelUserFirstName = firstSelUserToMention.name.orEmpty().split(" ").first().trim()
        val firstSelUserFullName = firstSelUserToMention.name.orEmpty()
        val secondSelUserToMention = User(chat = chat, id = "Markus Koller", name = "Markus Koller")
        val secondSelUserFirstName = secondSelUserToMention.name.orEmpty().split(" ").first().trim()
        val secondSelUserFullName = secondSelUserToMention.name.orEmpty()

        messageDraft.currentText = "My name is @$firstSelUserFirstName and I'm the new marketing manager"
        messageDraft.addMentionedUser(firstSelUserToMention, 11)
        messageDraft.currentText = "I'm @$firstSelUserFullName recommended by @$secondSelUserFirstName who I worked with before"
        messageDraft.addMentionedUser(secondSelUserToMention, 35)

        assertEquals(messageDraft.currentText, "I'm @$firstSelUserFullName recommended by @$secondSelUserFullName who I worked with before")
        assertEquals(messageDraft.getMessagePreview().mentionedUsers[4]?.name, firstSelUserFullName)
        assertEquals(messageDraft.getMessagePreview().mentionedUsers[35]?.name, secondSelUserFullName)
    }

    @Test
    fun messageDraftReQueryModifiedMentions() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val selectedUserToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
        val selectedUserFirstName = selectedUserToMention.name.orEmpty().split(" ").first().trim()
        val selectedUserFullName = selectedUserToMention.name.orEmpty()
        val selectedChannelToMention = ChannelImpl(chat = chat, id = "General", name = "General")
        val selectedChannelBriefName = selectedChannelToMention.name.orEmpty().dropLast(3)
        val selectedChannelFullName = selectedChannelToMention.name.orEmpty()

        messageDraft.currentText = "Hi, it's @$selectedUserFirstName who needs access to #$selectedChannelBriefName"
        messageDraft.addMentionedUser(selectedUserToMention, 9)
        messageDraft.addMentionedChannel(selectedChannelToMention, 45)
        assertEquals(messageDraft.currentText, "Hi, it's @$selectedUserFullName who needs access to #$selectedChannelFullName")

        messageDraft.onSuggestionsChanged = { _, suggestions ->
            assertEquals(suggestions.users[9]?.map { it.name.orEmpty() }, channelMembers)
            assertEquals(suggestions.channels[34]?.map { it.name.orEmpty() }, channelSuggestions)
        }
        messageDraft.currentText = "Hi, it's @${selectedUserFirstName.dropLast(3)} who needs access to #$selectedChannelBriefName"
    }

    @Test
    fun testOnSuggestionsChangedClosure() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        messageDraft.onSuggestionsChanged = { _, suggestions ->
            when (messageDraft.currentText) {
                "Hi, please welcome @Mar" -> {
                    assertEquals(suggestions.users[19]?.map { it.name.orEmpty() }, channelMembers.map { it })
                    messageDraft.currentText = "Hi, please welcome @Mar and add her to the #Gen channel"
                }
                "Hi, please welcome @Mar and add her to the #Gen channel" -> {
                    assertTrue(suggestions.users.isEmpty())
                    assertEquals(suggestions.channels[43]?.map { it.name.orEmpty() }, channelSuggestions.map { it })
                }
                else -> {
                    fail("Unexpected condition")
                }
            }
        }

        messageDraft.currentText = "Hi, please welcome @Mar"
    }

    @Test
    fun getHighlightedMention() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val selectedUserToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")

        messageDraft.currentText = "Please welcome @Mar in our team!"
        messageDraft.addMentionedUser(selectedUserToMention, 15)

        val highlightedMention = messageDraft.getHighlightedUserMention(15)
        val highlightedUser = highlightedMention?.user

        assertEquals(highlightedUser?.name, selectedUserToMention.name.orEmpty())
    }

    @Test
    fun getHighlightedMentionAtInvalidPosition() {
        chat = configureChat(withChannelMembers = channelMembers, andChannelSuggestions = channelSuggestions)

        val channel = ChannelImpl(chat, id = "test-channel", name = "test-channel")
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val selectedUserToMention = User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")

        messageDraft.currentText = "Please welcome @Mar in our team!"
        messageDraft.addMentionedUser(selectedUserToMention, 15)

        val highlightedMention = messageDraft.getHighlightedUserMention(10)
        val highlightedUser = highlightedMention?.user

        assertNull(highlightedMention)
        assertNull(highlightedUser)
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
