package com.pubnub.kmp

import com.pubnub.api.PubNubException
import com.pubnub.api.UserId
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.objects.PNKey
import com.pubnub.api.models.consumer.objects.PNMemberKey
import com.pubnub.api.models.consumer.objects.PNPage
import com.pubnub.api.models.consumer.objects.PNSortKey
import com.pubnub.api.models.consumer.push.PNPushAddChannelResult
import com.pubnub.api.models.consumer.push.PNPushRemoveChannelResult
import com.pubnub.api.v2.createPNConfiguration
import com.pubnub.kmp.channel.BaseChannel
import com.pubnub.kmp.channel.ChannelImpl
import com.pubnub.kmp.channel.GetChannelsResponse
import com.pubnub.kmp.membership.MembersResponse
import com.pubnub.kmp.message.MessageImpl
import com.pubnub.kmp.message_draft.MessageDraft
import com.pubnub.kmp.message_draft.UserSuggestionDataSource
import com.pubnub.kmp.restrictions.Restriction
import com.pubnub.kmp.types.ChannelType
import com.pubnub.kmp.types.CreateDirectConversationResult
import com.pubnub.kmp.types.CreateGroupConversationResult
import com.pubnub.kmp.types.EmitEventMethod
import com.pubnub.kmp.types.EventContent
import com.pubnub.kmp.user.GetUsersResponse
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MessageDraftTest {
    private lateinit var chat: FakeMessageDraftChat
    private lateinit var channel: FakeMessageDraftChannel

    private fun generalChannel(): Channel {
        return ChannelImpl(chat = chat, id = "General", name = "General")
    }

    private fun secondGeneralChannel(): Channel {
        return ChannelImpl(chat = chat, id = "General 222", name = "General 222")
    }

    private fun gamesChannel(): Channel {
        return ChannelImpl(chat = chat, id = "Games", name = "Games")
    }

    private fun secondGamesChannel(): Channel {
        return ChannelImpl(chat = chat, id = "Games 222", name = "Games 222")
    }

    private fun marianSalazar(): User {
        return  User(chat = chat, id = "Marian Salazar", name = "Marian Salazar")
    }

    private fun marianKoch(): User {
        return  User(chat = chat, id = "Marian Koch", name = "Marian Koch")
    }

    private fun markusKoller(): User {
        return  User(chat = chat, id = "Markus Koller", name = "Markus Koller")
    }

    private fun maryJonson(): User {
        return  User(chat = chat, id = "Mary Johnson", name = "Mary Johnson")
    }

    private fun maryJacobsen(): User {
        return  User(chat = chat, id = "Mary Jacobsen", name = "Mary Jacobsen")
    }

    @BeforeTest
    fun beforeEach() {
        val config = ChatConfigImpl(createPNConfiguration(UserId("userId"), "subscribeKey", "publishKey"))
        val underlyingChat = ChatImpl(config)

        chat = FakeMessageDraftChat(underlyingChat)
        chat.allChannelsSuggestions = listOf(generalChannel(), secondGeneralChannel(), gamesChannel(), secondGamesChannel())

        val underlyingChannel = ChannelImpl(id = "test", name = "test", chat = chat)
        val allChannelMembers = listOf(marianSalazar(), marianKoch(), maryJonson(), maryJacobsen())

        channel = FakeMessageDraftChannel(underlyingChannel, allChannelMembers)
    }

    @Test
    fun checkingSuggestedChannelsAndUsers() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)

        messageDraft.onSuggestionsChanged = { _, suggestedMentions ->
            val actualUsers = suggestedMentions.users[messageDraft.currentText.indexOf("@")]?.map { it.name }
            val actualChannels = suggestedMentions.channels[messageDraft.currentText.indexOf("#")]?.map { it.name }
            assertEquals(listOf(marianSalazar(), marianKoch()).map { it.name.orEmpty() }, actualUsers)
            assertEquals(listOf(gamesChannel(), secondGamesChannel()).map { it.name.orEmpty() }, actualChannels)
        }
        messageDraft.currentText = "Hey, @Mari here. Please add me to the #Game channel"
    }

    @Test
    fun addingMentionedUserAndChannel() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = gamesChannel()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "I'm @$userBriefName and I would like to join the #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        assertEquals("I'm @$userFullName and I would like to join the #$channelBriefName channel", messageDraft.currentText)
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        assertEquals("I'm @$userFullName and I would like to join the #$channelFullName channel", messageDraft.currentText)

        val preview = messageDraft.getMessagePreview()
        assertEquals(preview.mentionedUsers[messageDraft.currentText.indexOf("@")]?.name, userFullName)
        assertEquals(preview.mentionedChannels[messageDraft.currentText.indexOf("#")]?.name, channelFullName)
    }

    @Test
    fun removingMentionedUserAndChannel() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = gamesChannel()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "I'm @$userBriefName and I would like to join the #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        assertEquals("I'm @$userFullName and I would like to join the #$channelFullName channel", messageDraft.currentText)

        messageDraft.removeMentionedUser(messageDraft.currentText.indexOf("@"))
        messageDraft.removeMentionedChannel(messageDraft.currentText.indexOf("#"))
        assertEquals("I'm @$userFullName and I would like to join the #$channelFullName channel", messageDraft.currentText)
        assertTrue(messageDraft.getMessagePreview().mentionedUsers.isEmpty())
        assertTrue(messageDraft.getMessagePreview().mentionedChannels.isEmpty())
    }

    @Test
    fun addingMentionedUserAndChannelToInvalidPositions() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = gamesChannel()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Thank you @$userBriefName for deciding to join our #$channelBriefName group"
        assertFailsWith<PubNubException> { messageDraft.addMentionedUser(userToMention, 20) }
        assertFailsWith<PubNubException> { messageDraft.addMentionedChannel(channelToMention, 105) }
        assertEquals("Thank you @$userBriefName for deciding to join our #$channelBriefName group", messageDraft.currentText)
    }

    @Test
    fun alteringTextWithAlreadyMentionedUserAndChannel() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = gamesChannel()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Hey all. I'm @$userBriefName and I would like to join #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        messageDraft.currentText = "Hey all. Nice to meet you. I'm @$userFullName who wants to join #$channelFullName channel"

        val messagePreview = messageDraft.getMessagePreview()
        assertEquals(userFullName, messagePreview.mentionedUsers[messageDraft.currentText.indexOf("@")]!!.name)
        assertEquals(channelFullName, messagePreview.mentionedChannels[messageDraft.currentText.indexOf("#")]!!.name)
    }

    @Test
    fun anotherTextAlteringWithAlreadyMentionedUserAndChannel() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = gamesChannel()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Hello. My name is @$userBriefName and I need access to #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))
        messageDraft.currentText = "@$userFullName, have you been added to #$channelFullName?"

        val messagePreview = messageDraft.getMessagePreview()
        assertEquals(userFullName, messagePreview.mentionedUsers[messageDraft.currentText.indexOf("@")]!!.name)
        assertEquals(channelFullName, messagePreview.mentionedChannels[messageDraft.currentText.indexOf("#")]!!.name)
    }

    @Test
    fun mentionsThatNoLongerMatchDueToStringSubstitution() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = gamesChannel()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Hello. My name is @$userBriefName, add me to the #$channelBriefName channel"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.addMentionedChannel(channelToMention, messageDraft.currentText.indexOf("#"))

        messageDraft.currentText = "I would love to tell you about the strengths I can bring to this role"
        assertTrue(messageDraft.getMessagePreview().mentionedUsers.isEmpty())
        assertTrue(messageDraft.getMessagePreview().mentionedChannels.isEmpty())
    }

    @Test
    fun testAppendingAnotherMention() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val secondUserToMention = markusKoller()
        val secondUserFullName = secondUserToMention.name.orEmpty()
        val secondUserBriefName = secondUserFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "My name is @$userBriefName and I'm the new marketing manager"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        messageDraft.currentText = "I'm @$userFullName recommended by @$secondUserBriefName who I worked with before"
        messageDraft.addMentionedUser(secondUserToMention, messageDraft.currentText.lastIndexOf("@"))
        assertEquals("I'm @$userFullName recommended by @$secondUserFullName who I worked with before", messageDraft.currentText)

        val messagePreview = messageDraft.getMessagePreview()
        assertEquals(userFullName, messagePreview.mentionedUsers[messageDraft.currentText.indexOf("@")]?.name)
        assertEquals(secondUserFullName, messagePreview.mentionedUsers[messageDraft.currentText.lastIndexOf("@")]?.name)
    }

    @Test
    fun messageDraftReQueryModifiedMention() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = gamesChannel()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.currentText = "Hi, it's @$userBriefName, your new marketing manager"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))
        assertEquals("Hi, it's @$userFullName, your new marketing manager", messageDraft.currentText)

        messageDraft.onSuggestionsChanged = { _, suggestions ->
            val actualUsers = suggestions.users[messageDraft.currentText.indexOf("@")]?.map { it.name.orEmpty() }
            val actualChannels = suggestions.channels[messageDraft.currentText.indexOf("#")]?.map { it.name.orEmpty() }
            assertEquals(listOf(marianSalazar(), marianKoch()).map { it.name.orEmpty() }, actualUsers)
            assertEquals(listOf(gamesChannel(), secondGamesChannel()).map { it.name.orEmpty() }, actualChannels)
        }

        messageDraft.currentText = "Hi, it's @${userBriefName.dropLast(2)}, your new marketing manager. Add me to the #$channelBriefName channel"
    }

    @Test
    fun testOnSuggestionsChangedClosure() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userFullName = userToMention.name.orEmpty()
        val userBriefName = userFullName.split(" ").first().trim()
        val channelToMention = gamesChannel()
        val channelFullName = channelToMention.name.orEmpty()
        val channelBriefName = channelFullName.split(" ").first().trim().dropLast(2)

        messageDraft.onSuggestionsChanged = { _, suggestions ->
            val actualUsers = suggestions.users[messageDraft.currentText.indexOf("@")]?.map { it.name.orEmpty() }
            val actualChannels = suggestions.channels[messageDraft.currentText.indexOf("#")]?.map { it.name.orEmpty() }

            when (messageDraft.currentText) {
                "Hi, please welcome @$userBriefName" -> {
                    assertEquals(listOf(marianSalazar(), marianKoch()).map { it.name.orEmpty() }, actualUsers)
                    assertTrue(suggestions.channels.isEmpty())
                    messageDraft.currentText = "Hi, please welcome @$userBriefName and add her to the #$channelBriefName channel"
                }
                "Hi, please welcome @$userBriefName and add her to the #$channelBriefName channel" -> {
                    // Text has been appended to the end, so we expect the next query for user/channel occurrence
                    assertTrue(suggestions.users.isEmpty())
                    assertEquals(listOf(gamesChannel(), secondGamesChannel()).map { it.name.orEmpty() }, actualChannels)
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
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val userBriefName = userToMention.name.orEmpty().split(" ").first().trim()

        messageDraft.currentText = "Please welcome @$userBriefName in our team!"
        messageDraft.addMentionedUser(userToMention, messageDraft.currentText.indexOf("@"))

        val highlightedMention = messageDraft.getHighlightedUserMention(messageDraft.currentText.indexOf("@"))
        val highlightedUser = highlightedMention?.user

        assertEquals(highlightedUser?.name, userToMention.name.orEmpty())
    }

    @Test
    fun getHighlightedMentionAtInvalidPosition() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
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
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val userToMention = marianSalazar()
        val channelToMention = gamesChannel()
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
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val message = MessageImpl(chat, 138327382910238921, EventContent.TextMessageContent("Hey!"), channel.id, "userId")

        messageDraft.addQuote(message)
        assertEquals(messageDraft.getMessagePreview().quotedMessage, message)
    }

    @Test
    fun removeQuotedMessage() {
        val messageDraft = MessageDraft(chat, channel, UserSuggestionDataSource.CHANNEL)
        val message = MessageImpl(chat, 138327382910238921, EventContent.TextMessageContent("Hey!"), channel.id, "userId")

        messageDraft.addQuote(message)
        messageDraft.removeQuote()
        assertNull(messageDraft.getMessagePreview().quotedMessage)
    }
}

open class FakeChannel(
    private val underlying: BaseChannel<Channel, Message>
): BaseChannel<Channel, Message>(
    channelFactory = underlying.channelFactory,
    messageFactory = underlying.messageFactory,
    chat = underlying.chat,
    id = underlying.id
) {
    override fun copyWithStatusDeleted(): Channel {
        return underlying.copyWithStatusDeleted()
    }
}

class FakeMessageDraftChannel(
    private val underlying: BaseChannel<Channel, Message>,
    private val allChannelMembers: List<User> = emptyList()
): FakeChannel(underlying) {
    override fun getMembers(
        limit: Int?,
        page: PNPage?,
        filter: String?,
        sort: Collection<PNSortKey<PNMemberKey>>
    ): PNFuture<MembersResponse> {
        val regex = Regex("'([^']*)'")
        val matchResult = regex.find(filter!!)!!
        val memberships = allChannelMembers.filter {
            it.name.orEmpty().startsWith(matchResult.value.dropLast(1).drop(1))
        }.map {
            Membership(
                chat = underlying.chat,
                channel = underlying,
                user = it,
                custom = null,
                updated = null,
                eTag = null
            )
        }
        return MembersResponse(
            next = null,
            prev = null,
            total = memberships.count(),
            status = 200,
            members = memberships.toSet()
        ).asFuture()
    }
}

class FakeMessageDraftChat(underlying: Chat): FakeChat(underlying) {
    // Stores the list of all channel members which is filled before each test
    var allChannelsSuggestions: List<Channel> = emptyList()

    override fun getChannelSuggestions(filter: String?, limit: Int): PNFuture<List<Channel>> {
        val regex = Regex("'([^']*)'")
        val matchResult = regex.find(filter!!)!!

        return allChannelsSuggestions.filter {
            it.name.orEmpty().startsWith(matchResult.value.dropLast(1).drop(1))
        }.asFuture()
    }
}

open class FakeChat(underlying: Chat) : Chat {
    final override val config: ChatConfig
    final override val pubNub: PubNub
    final override val currentUser: User
    final override val editMessageActionName: String
    final override val deleteMessageActionName: String

    init {
        this.config = underlying.config
        this.pubNub = underlying.pubNub
        this.currentUser = underlying.currentUser
        this.editMessageActionName = underlying.editMessageActionName
        this.deleteMessageActionName = underlying.deleteMessageActionName
    }
    override fun createUser(user: User): PNFuture<User> {
        TODO("Not yet implemented")
    }

    override fun createUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        status: String?,
        type: String?
    ): PNFuture<User> {
        TODO("Not yet implemented")
    }

    override fun getUser(userId: String): PNFuture<User?> {
        TODO("Not yet implemented")
    }

    override fun getUsers(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?
    ): PNFuture<GetUsersResponse> {
        TODO("Not yet implemented")
    }

    override fun updateUser(
        id: String,
        name: String?,
        externalId: String?,
        profileUrl: String?,
        email: String?,
        custom: CustomObject?,
        status: String?,
        type: String?
    ): PNFuture<User> {
        TODO("Not yet implemented")
    }

    override fun deleteUser(id: String, soft: Boolean): PNFuture<User> {
        TODO("Not yet implemented")
    }

    override fun wherePresent(userId: String): PNFuture<List<String>> {
        TODO("Not yet implemented")
    }

    override fun isPresent(userId: String, channel: String): PNFuture<Boolean> {
        TODO("Not yet implemented")
    }

    override fun createChannel(
        id: String,
        name: String?,
        description: String?,
        custom: CustomObject?,
        type: ChannelType?,
        status: String?
    ): PNFuture<Channel> {
        TODO("Not yet implemented")
    }

    override fun getChannel(channelId: String): PNFuture<Channel?> {
        TODO("Not yet implemented")
    }

    override fun getChannels(
        filter: String?,
        sort: Collection<PNSortKey<PNKey>>,
        limit: Int?,
        page: PNPage?
    ): PNFuture<GetChannelsResponse> {
        TODO("Not yet implemented")
    }

    override fun updateChannel(
        id: String,
        name: String?,
        custom: CustomObject?,
        description: String?,
        updated: String?,
        status: String?,
        type: ChannelType?
    ): PNFuture<Channel> {
        TODO("Not yet implemented")
    }

    override fun deleteChannel(id: String, soft: Boolean): PNFuture<Channel> {
        TODO("Not yet implemented")
    }

    override fun forwardMessage(message: Message, channelId: String): PNFuture<PNPublishResult> {
        TODO("Not yet implemented")
    }

    override fun whoIsPresent(channelId: String): PNFuture<Collection<String>> {
        TODO("Not yet implemented")
    }

    override fun <T : EventContent> emitEvent(
        channel: String,
        payload: T,
        mergePayloadWith: Map<String, Any>?
    ): PNFuture<PNPublishResult> {
        TODO("Not yet implemented")
    }

    override fun createDirectConversation(
        invitedUser: User,
        channelId: String?,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?,
        custom: CustomObject?
    ): PNFuture<CreateDirectConversationResult> {
        TODO("Not yet implemented")
    }

    override fun createGroupConversation(
        invitedUsers: Collection<User>,
        channelId: String,
        channelName: String?,
        channelDescription: String?,
        channelCustom: CustomObject?,
        channelStatus: String?,
        custom: CustomObject?
    ): PNFuture<CreateGroupConversationResult> {
        TODO("Not yet implemented")
    }

    @ExperimentalStdlibApi
    override fun <T : EventContent> listenForEvents(
        type: KClass<T>,
        channel: String,
        customMethod: EmitEventMethod?,
        callback: (event: Event<T>) -> Unit
    ): AutoCloseable {
        TODO("Not yet implemented")
    }

    override fun setRestrictions(restriction: Restriction): PNFuture<Unit> {
        TODO("Not yet implemented")
    }

    override fun registerPushChannels(channels: List<String>): PNFuture<PNPushAddChannelResult> {
        TODO("Not yet implemented")
    }

    override fun unregisterPushChannels(channels: List<String>): PNFuture<PNPushRemoveChannelResult> {
        TODO("Not yet implemented")
    }

    override fun getThreadChannel(message: Message): PNFuture<ThreadChannel> {
        TODO("Not yet implemented")
    }

    override fun publish(
        channelId: String,
        message: EventContent,
        meta: Map<String, Any>?,
        shouldStore: Boolean?,
        usePost: Boolean,
        replicate: Boolean,
        ttl: Int?,
        mergeMessageWith: Map<String, Any>?
    ): PNFuture<PNPublishResult> {
        TODO("Not yet implemented")
    }

    override fun signal(
        channelId: String,
        message: EventContent,
        mergeMessageWith: Map<String, Any>?
    ): PNFuture<PNPublishResult> {
        TODO("Not yet implemented")
    }

    override fun getChannelSuggestions(filter: String?, limit: Int): PNFuture<List<Channel>> {
        TODO("Not yet implemented")
    }
}

