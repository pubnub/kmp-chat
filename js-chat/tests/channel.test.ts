import {
  Channel,
  Message,
  Chat,
  MessageDraft,
  INTERNAL_MODERATION_PREFIX,
  Membership,
} from "../dist-test"
import {
  sleep,
  extractMentionedUserIds,
  createRandomUser,
  createRandomChannel,
  createChatInstance,
  sendMessageAndWaitForHistory,
  makeid,
  generateRandomString,
} from "./utils"

import { jest } from "@jest/globals"

describe("Channel test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let chatPamServer: Chat
  let chatPamServerWithRefIntegrity: Chat
  let channel: Channel
  let messageDraft: MessageDraft

  beforeAll(async () => {
    chat = await createChatInstance()
    chatPamServer = await createChatInstance( { shouldCreateNewInstance: true, clientType: 'PamServer' })
    chatPamServerWithRefIntegrity = await createChatInstance( { shouldCreateNewInstance: true, clientType: 'PamServerWithRefIntegrity' })
  })

  beforeEach(async () => {
    channel = await createRandomChannel()
    messageDraft = channel.createMessageDraft()
  })

  afterEach(async () => {
    await channel.delete()
    jest.clearAllMocks()
  })

  test("should create a channel", async () => {
    expect(channel).toBeDefined()
    const fetchedChannel = await chat.getChannel(channel.id)
    expect(fetchedChannel).toBeDefined()
    expect(fetchedChannel.name).toEqual(channel.name)
    expect(fetchedChannel.description).toEqual(channel.description)
  })

  test("Should be able to delete channel", async () => {
    const deleteOptions = { soft: true }
    const { status } = (await channel.delete(deleteOptions)) as Channel
    expect(status).toBe("deleted")

    const deleteResult = await channel.delete()
    expect(deleteResult).toBe(true)
    const fetchedChannel = await chat.getChannel(channel.id)
    expect(fetchedChannel).toBeNull()
  })

  test("should get channel history", async () => {
    const messageText1 = "Test message 1"
    const messageText2 = "Test message 2"

    await channel.sendText(messageText1)
    await channel.sendText(messageText2)
    await sleep(150) // history calls have around 130ms of cache time

    const history = await channel.getHistory()
    expect(history.messages.length).toBe(2)

    const message1InHistory = history.messages.some(
      (message) => message.content.text === messageText1
    )
    const message2InHistory = history.messages.some(
      (message) => message.content.text === messageText2
    )
    expect(message1InHistory).toBeTruthy()
    expect(message2InHistory).toBeTruthy()
  })

  test("should get channel history with pagination", async () => {
    const messageText1 = "Test message 1"
    const messageText2 = "Test message 2"
    const messageText3 = "Test message 3"

    await channel.sendText(messageText1)
    await channel.sendText(messageText2)
    await channel.sendText(messageText3)
    await sleep(150) // history calls have around 130ms of cache time

    const history = await channel.getHistory({ count: 2 })
    expect(history.messages.length).toBe(2)
    expect(history.isMore).toBeTruthy()

    const secondPage = await channel.getHistory({ startTimetoken: history.messages[0].timetoken })
    expect(secondPage.messages.length).toBeGreaterThanOrEqual(1)
  })

  test("should fetch unread messages counts with pagination", async () => {
    const channel1 = await createRandomChannel()
    const channel2 = await createRandomChannel()

    await channel1.invite(chat.currentUser)
    await channel2.invite(chat.currentUser)
    await channel1.sendText("Some text")
    await channel2.sendText("Some text")

    const firstResults = await chat.fetchUnreadMessagesCounts({ limit: 1 })
    expect(firstResults.countsByChannel.length).toEqual(1)
    expect(firstResults.countsByChannel.at(0).count).toEqual(1)
    expect(firstResults.countsByChannel.at(0).channel.id in arrayOf(channel1.id, channel2.id)).toBeTruthy()

    const secondResults = await chat.fetchUnreadMessagesCounts({ page: { next: firstResults.page.next } })
    expect(secondResults.countsByChannel.length).toEqual(1)
    expect(secondResults.countsByChannel.at(0).count).toEqual(1)
    expect(secondResults.countsByChannel.at(0).channel.id in arrayOf(channel1.id, channel2.id)).toBeTruthy()

    const thirdResults = await chat.fetchUnreadMessagesCounts({ page: { next: secondResults.page.next } })
    expect(thirdResults.countsByChannel.length).toEqual(0)

    await channel1.leave()
    await channel2.leave()
    await channel1.delete()
    await channel2.delete()
  })

  test("should fail when trying to send a message to a non-existent channel", async () => {
    const nonExistentChannel = (await chat.getChannel("non-existing-channel")) as Channel

    try {
      await nonExistentChannel.sendText("Test message")
      fail("Should have thrown an error")
    } catch (error) {
      expect(error).toBeInstanceOf(Error)
    }
  })

  test("should fail when trying to send a message to a deleted channel", async () => {
    await channel.delete()

    try {
      await channel.sendText("Test message")
      fail("Should have thrown an error")
    } catch (error) {
      expect(error).toBeInstanceOf(Error)
    }
  })

  test("should fail when trying to get history of a deleted channel", async () => {
    await channel.delete()

    try {
      await channel.getHistory()
      fail("Should have thrown an error")
    } catch (error) {
      expect(error).toBeInstanceOf(Error)
    }
  })

  test("should edit membership metadata", async () => {
    const { membership, disconnect } = await channel.join(() => null)
    const updatedMembership = await membership.update({
      custom: { role: "admin" },
    })
    expect(updatedMembership.custom?.role).toBe("admin")

    await channel.leave()
    disconnect()
  })

  test("should create a direct, group and public chats with a predefined ID", async () => {
    const someFakeDirectId = "someFakeDirectId"
    const someFakeGroupId = "someFakeGroupId"
    const someFakePublicId = "someFakePublicId"

    const existingChannels = await Promise.all(
      [someFakeDirectId, someFakeGroupId, someFakePublicId].map((id) => chat.getChannel(id))
    )

    for (const existingChannel of existingChannels) {
      if (existingChannel) {
        await existingChannel.delete({ soft: false })
      }
    }

    const user = await createRandomUser()

    const newChannels = await Promise.all([
      chat.createDirectConversation({
        user,
        channelId: someFakeDirectId,
        channelData: {},
      }),
      chat.createGroupConversation({
        users: [user],
        channelId: someFakeGroupId,
        channelData: {},
      }),
      chat.createPublicConversation({
        channelId: someFakePublicId,
        channelData: {},
      }),
    ])

    expect(newChannels[0].channel.id).toBe(someFakeDirectId)
    expect(newChannels[1].channel.id).toBe(someFakeGroupId)
    expect(newChannels[2].id).toBe(someFakePublicId)

    await newChannels[0].channel.delete({ soft: false })
    await newChannels[1].channel.delete({ soft: false })
    await newChannels[2].delete({ soft: false })
  })

  test("should create a direct, group and public chats with default IDs", async () => {
    const user = await createRandomUser()

    const newChannels = await Promise.all([
      chat.createDirectConversation({
        user,
        channelData: {},
      }),
      chat.createGroupConversation({
        users: [user],
        channelData: {},
      }),
      chat.createPublicConversation({
        channelData: {},
      }),
    ])
    expect(newChannels[0].channel.id.startsWith("direct.")).toBeTruthy()
    expect(newChannels[1].channel.id).toBeDefined()
    expect(newChannels[2].id).toBeDefined()

    await newChannels[0].channel.delete({ soft: false })
    await newChannels[1].channel.delete({ soft: false })
    await newChannels[2].delete({ soft: false })
  })

  test("should create direct conversation and send message", async () => {
    const user = await createRandomUser()
    expect(user).toBeDefined()
    const inviteCallback = jest.fn()

    const removeInvitationListener = chat.listenForEvents({
      channel: user.id,
      type: "invite",
      callback: inviteCallback,
    })

    const directConversation = await chat.createDirectConversation({
      user,
      channelData: { name: "Test Convo" },
    })
    expect(directConversation).toBeDefined()

    const messageText = "Hello from User1"
    await directConversation.channel.sendText(messageText)
    await sleep(150) // history calls have around 130ms of cache time
    const history = await directConversation.channel.getHistory()
    const messageInHistory = history.messages.some(
      (message: Message) => message.content.text === messageText
    )
    expect(messageInHistory).toBeTruthy()
    expect(inviteCallback).toHaveBeenCalledTimes(1)
    expect(inviteCallback).toHaveBeenCalledWith(
      expect.objectContaining({
        payload: expect.objectContaining({
          channelType: "direct",
          channelId: directConversation.channel.id
        }),
      })
    )
    await user.delete()
    removeInvitationListener()
  })

  test("should create group conversation", async () => {
    const [user1, user2, user3] = await Promise.all([
      createRandomUser(),
      createRandomUser(),
      createRandomUser(),
    ])
    const inviteCallback = jest.fn()

    const removeInvitationListener = chat.listenForEvents({
      channel: user1.id,
      type: "invite",
      callback: inviteCallback,
    })

    const channelId = "group_channel_1234"
    const channelData = {
      name: "Test Group Channel",
      description: "This is a test group channel.",
      custom: {
        groupInfo: "Additional group information",
      },
    }

    const membershipData = {
      custom: {
        role: "member",
      },
    }

    const result = await chat.createGroupConversation({
      users: [user1, user2, user3],
      channelId,
      channelData,
      membershipData,
    })

    const { channel, hostMembership, inviteesMemberships } = result

    expect(channel).toBeDefined()
    expect(hostMembership).toBeDefined()
    expect(inviteesMemberships).toBeDefined()
    expect(channel.name).toEqual("Test Group Channel")
    expect(channel.description).toEqual("This is a test group channel.")
    expect(channel.custom.groupInfo).toEqual("Additional group information")
    expect(inviteesMemberships.length).toEqual(3)
    await sleep(150) // history calls have around 130ms of cache time
    expect(inviteCallback).toHaveBeenCalledTimes(1)
    expect(inviteCallback).toHaveBeenCalledWith(
      expect.objectContaining({
        payload: expect.objectContaining({
          channelType: "group",
          channelId: result.channel.id,
        }),
      })
    )

    await Promise.all([
      user1.delete(),
      user2.delete(),
      user3.delete(),
      channel.delete({ soft: false }),
    ])
    removeInvitationListener()
  })

  test("should create a thread", async () => {
    const messageText = "Test message"
    await channel.sendText(messageText)
    await sleep(150) // history calls have around 130ms of cache time

    let history = await channel.getHistory()
    let sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(false)

    const threadDraft = await sentMessage.createThread()

    await threadDraft.sendText("Some random text in a thread")

    history = await channel.getHistory()
    sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(true)

    const thread = await sentMessage.getThread()
    const threadText = "Whatever text"
    await thread.sendText(threadText)
    await sleep(150) // history calls have around 130ms of cache time
    const threadMessages = await thread.getHistory()
    expect(threadMessages.messages.some((message) => message.text === threadText)).toBe(true)
  })

  test("should not be able to create an empty Thread without any message", async () => {
    let errorOccurred = false
    try {
      await channel.createThread()
    } catch (error) {
      errorOccurred = true
    }

    expect(errorOccurred).toBe(true)
  })

  test("streamMessageReport should receive reported message", async () => {
    const messageTextToReport = "lalalal"
    const reason = "rude";
    let receivedEvent

    const unsubscribe = channel.streamMessageReports((event) => {
      receivedEvent = event
    })

    const { timetoken } = await channel.sendText(messageTextToReport);
    await sleep(1000) // delayForHistory
    const message = await channel.getMessage(timetoken);

    await message.report(reason);
    await sleep(500)

    expect(receivedEvent.payload.text).toEqual(messageTextToReport);
    expect(receivedEvent.payload.reason).toEqual(reason);
    unsubscribe(); // Cleanup
  });

  test("should stream channel updates and invoke the callback", async () => {
    let updatedChannel
    channel = await channel.update({ type: "public" })
    const name = "Updated Channel"
    const callback = jest.fn((chanel) => (updatedChannel = chanel))

    const stopUpdates = channel.streamUpdates(callback)
    await channel.update({ name })
    await sleep(150)

    expect(callback).toHaveBeenCalled()
    expect(callback).toHaveBeenCalledWith(updatedChannel)
    expect(updatedChannel.name).toEqual(name)
    expect(updatedChannel.type).toEqual(channel.type)
    stopUpdates()
  })

  test("should stream membership updates and invoke the callback", async () => {
    const { membership, disconnect } = await channel.join(() => null)
    expect(membership).toBeDefined()

    let updatedMembership
    const role = "admin"
    const callback = jest.fn((membership) => (updatedMembership = membership))

    const stopUpdates = membership.streamUpdates(callback)
    await membership.update({ custom: { role } })
    await sleep(150)

    expect(callback).toHaveBeenCalled()
    expect(callback).toHaveBeenCalledWith(updatedMembership)
    expect(updatedMembership.custom.role).toEqual(role)

    await channel.leave()
    stopUpdates()
    disconnect()
  })

  test("should get unread messages count", async () => {
    const messageText1 = "Test message 1"
    const messageText2 = "Test message 2"

    await channel.sendText(messageText1)
    await channel.sendText(messageText2)
    await sleep(150) // history calls have around 130ms of cache time

    const channelJoinData = await channel.join(() => null)
    let { membership } = channelJoinData
    const { disconnect } = channelJoinData
    let unreadCount = await membership.getUnreadMessagesCount()
    expect(unreadCount).toBe(0)

    const { messages } = await channel.getHistory()
    membership = await membership.setLastReadMessageTimetoken(messages[0].timetoken)
    unreadCount = await membership.getUnreadMessagesCount()
    expect(unreadCount).toBe(1)

    await channel.leave()
    disconnect()
  })

  test("should mention users in a message and validate mentioned users", async () => {
    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User1" })

    const user2Id = `user2_${Date.now()}`
    const user2 = await chat.createUser(user2Id, { name: "User2" })

    const messageText = `Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`

    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name} and @Use`)
    messageDraft.addMentionedUser(user2, 1)
    await messageDraft.onChange(
      `Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`
    )

    await messageDraft.send()
    await sleep(150) // history calls have around 130ms of cache time

    const history = await channel.getHistory()

    const messageInHistory = history.messages.find(
      (message: any) => message.content.text === messageText
    )

    expect(messageInHistory).toBeDefined()

    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(2)
    expect(messageInHistory.mentionedUsers["0"].id).toEqual(user1.id)
    expect(messageInHistory.mentionedUsers["1"].id).toEqual(user2.id)

    const extractedNamesFromText = extractMentionedUserIds(messageText)
    expect(messageInHistory.mentionedUsers["0"].name).toEqual(extractedNamesFromText[0])
    expect(messageInHistory.mentionedUsers["1"].name).toEqual(extractedNamesFromText[1])

    await chat.deleteUser(user1.id)
    await chat.deleteUser(user2.id)
  })

  test("should mention users with multi-word names in a message and validate mentioned users", async () => {


    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User One" })

    const user2Id = `user2_${Date.now()}`
    const user2 = await chat.createUser(user2Id, { name: "User Two" })

    const messageText = `Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`

    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name} and @Use`)
    messageDraft.addMentionedUser(user2, 1)
    await messageDraft.onChange(
      `Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`
    )

    await messageDraft.send()
    await sleep(150) // history calls have around 130ms of cache time

    const history = await channel.getHistory()

    const messageInHistory = history.messages.find(
      (message: any) => message.content.text === messageText
    )

    expect(messageInHistory).toBeDefined()

    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(2)
    expect(messageInHistory.mentionedUsers["0"].id).toEqual(user1.id)
    expect(messageInHistory.mentionedUsers["1"].id).toEqual(user2.id)

    await chat.deleteUser(user1.id)
    await chat.deleteUser(user2.id)
  })

  test("should send a message with words that start with @ but are not user mentions", async () => {


    const messageText =
      "Hello, this is a test message with words that start with @ but are not user mentions: @test, @example, @check."

    const messageInHistory = await sendMessageAndWaitForHistory(
      channel.createMessageDraft(messageText),
      channel
    )

    expect(messageInHistory).toBeDefined()

    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(0)
  })

  test("should try to mention users with incorrect usernames and validate no users are mentioned", async () => {


    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User One" })

    const incorrectUserId = user1Id.substring(0, user1Id.length - 1)

    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name}, I tried to mention you`)
    const finalMessageTextToSend = `Hello, @${incorrectUserId}, I tried to mention you`
    await messageDraft.onChange(finalMessageTextToSend)

    const messageInHistory = await sendMessageAndWaitForHistory(messageDraft, channel)

    expect(messageInHistory).toBeDefined()

    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(0)

    await chat.deleteUser(user1.id)
  })

  test("should suggest global users who are not members of the channel", async () => {


    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User 1" })

    const user2Id = `user2_${Date.now()}`
    const user2 = await chat.createUser(user2Id, { name: "User 2" })

    await channel.invite(user1)

    messageDraft = channel.createMessageDraft({ userSuggestionSource: "global", userLimit: 100 })

    const originalGetUserSuggestions = Chat.prototype.getUserSuggestions
    const getUserSuggestionsSpy = jest.spyOn(Chat.prototype, "getUserSuggestions")

    getUserSuggestionsSpy.mockImplementation(originalGetUserSuggestions)

    const onChangeResponse = await messageDraft.onChange("Hello, @Use")

    // verification that users inside the keyset were suggested
    expect(getUserSuggestionsSpy).toHaveBeenCalledTimes(1)

    const foundUser1AmongSuggestedUsers = onChangeResponse.users.suggestedUsers.find(
      (suggestedUser) => suggestedUser.id === user1.id
    )

    expect(foundUser1AmongSuggestedUsers).toBeTruthy()
    // get members of the channel and verify if user that is member of channel exists in keyset
    const membersResponse = await channel.getMembers( { "sort": {"uuid.updated": "desc"} })
    const members = membersResponse.members
    expect(
      onChangeResponse.users.suggestedUsers.some(
        (suggestedUser) => !members.includes(suggestedUser.id)
      )
    ).toBeTruthy()

    await chat.deleteUser(user1.id)
    await chat.deleteUser(user2.id)
  })

  test("should mention the same user multiple times in a message and validate mentioned users", async () => {


    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User 1" })

    const messageText = `Hello, @${user1.name}, how are you? @${user1.name}, are you there?`

    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name}, how are you? @Use`)
    messageDraft.addMentionedUser(user1, 1)
    await messageDraft.onChange(messageText)

    const messageInHistory = await sendMessageAndWaitForHistory(messageDraft, channel)

    expect(messageInHistory).toBeDefined()

    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(2)
    expect(messageInHistory.mentionedUsers["0"].id).toEqual(user1.id)
    expect(messageInHistory.mentionedUsers["1"].id).toEqual(user1.id)

    await chat.deleteUser(user1.id)
  })

  test("should correctly add and remove mentioned user", async () => {


    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User 1" })
    const user2Id = `user2_${Date.now()}`
    const user2 = await chat.createUser(user2Id, { name: "User 2" })

    const messageDraft = channel.createMessageDraft()

    const originalMessage = `Hello, @${user1.name}, how are you? @${user2.name}, are you there?`
    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name}, how are you? @Use`)
    messageDraft.addMentionedUser(user2, 1)
    await messageDraft.onChange(originalMessage)

    const expectedMessage = `Hello, @${user1.name}, how are you? @User 2, are you there?`

    expect(messageDraft.value).toEqual(expectedMessage)

    messageDraft.removeMentionedUser(1)

    const messageInHistory = await sendMessageAndWaitForHistory(messageDraft, channel)

    expect(messageInHistory).toBeDefined()

    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(1)
    expect(messageInHistory.mentionedUsers["0"].id).toBe(user1.id)

    await chat.deleteUser(user1.id)
  })

  test("should correctly add and remove the middle mentioned user", async () => {


    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User 1" })
    const user2Id = `user2_${Date.now()}`
    const user2 = await chat.createUser(user2Id, { name: "User 2" })
    const user3Id = `user3_${Date.now()}`
    const user3 = await chat.createUser(user3Id, { name: "User 3" })

    const messageDraft = channel.createMessageDraft()

    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name}, how are you? @Use`)
    messageDraft.addMentionedUser(user2, 1)
    await messageDraft.onChange(
      `Hello, @${user1.name}, how are you? @${user2.name}, are you there? Test: @Use`
    )
    messageDraft.addMentionedUser(user3, 2)

    const expectedMessage = `Hello, @${user1.name}, how are you? @User 2, are you there? Test: @${user3.name}`

    expect(messageDraft.value).toEqual(expectedMessage)

    messageDraft.removeMentionedUser(1)

    const messageInHistory = await sendMessageAndWaitForHistory(messageDraft, channel)

    expect(messageInHistory).toBeDefined()

    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(2)
    expect(messageInHistory.mentionedUsers["0"].id).toBe(user1.id)
    expect(messageInHistory.mentionedUsers["1"]).toBeUndefined()
    expect(messageInHistory.mentionedUsers["2"].id).toBe(user3.id)

    await chat.deleteUser(user1.id)
    await chat.deleteUser(user2.id)
    await chat.deleteUser(user3.id)
  })

  test.skip("should mention user in a message and validate cache", async () => {


    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User 1" })

    messageDraft = channel.createMessageDraft({ userSuggestionSource: "global" })

    const originalGetUsers = Chat.prototype.getUsers
    const getUsersSpy = jest.spyOn(Chat.prototype, "getUsers")
    getUsersSpy.mockImplementation(originalGetUsers)

    await messageDraft.onChange("Hello, @Use")
    await messageDraft.onChange("Hello, @User")
    await messageDraft.onChange("Hello, @Use")
    await messageDraft.onChange("Hello, @User")
    expect(getUsersSpy).toHaveBeenCalledTimes(2)

    await chat.deleteUser(user1.id)
  })

  test("should add and remove a quote", async () => {


    const messageText = "Test message"
    await channel.sendText(messageText)
    await sleep(150) // history calls have around 130ms of cache time
    const history = await channel.getHistory()
    const sentMessage: Message = history.messages[0]

    messageDraft.addQuote(sentMessage)

    expect(messageDraft.quotedMessage).toEqual(sentMessage)

    messageDraft.removeQuote()

    expect(messageDraft.quotedMessage).toBeUndefined()
  })

  test("should throw an error when trying to quote a message from another channel", async () => {


    const otherChannel = await createRandomChannel()

    try {
      const messageText = "Test message"
      await otherChannel.sendText(messageText)
      await sleep(150) // history calls have around 130ms of cache time
      const history = await otherChannel.getHistory()
      const otherMessage: Message = history.messages[0]

      messageDraft.addQuote(otherMessage)

      fail("Should have thrown an error")
    } catch (error) {
      expect(error.message).toEqual("You cannot quote messages from other channels")
    } finally {
      await otherChannel.delete()
    }
  })

  test("should quote multiple messages", async () => {
    const messageText1 = "Test message 1"
    const messageText2 = "Test message 2"
    const messageText3 = "Test message 3"

    await channel.sendText(messageText1)
    await channel.sendText(messageText2)
    await channel.sendText(messageText3)
    await sleep(150) // history calls have around 130ms of cache time

    const history = await channel.getHistory()

    const messageDraft = channel.createMessageDraft()

    messageDraft.addQuote(history.messages[0])

    messageDraft.addQuote(history.messages[1])

    messageDraft.addQuote(history.messages[2])

    expect(messageDraft.quotedMessage).toEqual(history.messages[2])

    messageDraft.removeQuote()
    expect(messageDraft.quotedMessage).toBeUndefined()
  })

  test("should correctly stream read receipts", async () => {
    const { membership, disconnect } = await channel.join(() => null)
    disconnect()

    const mockCallback = jest.fn()
    const stopReceipts = await channel.streamReadReceipts(mockCallback)
    await sleep(1000)
    expect(mockCallback).toHaveBeenCalledTimes(1)
    expect(mockCallback).toHaveBeenCalledWith({
      [membership.lastReadMessageTimetoken]: ["test-user"],
    })

    const { timetoken } = await channel.sendText("New message")
    await sleep(150) // history calls have around 130ms of cache time
    const message = await channel.getMessage(timetoken)
    await membership.setLastReadMessageTimetoken(message.timetoken)
    await sleep(150) // history calls have around 130ms of cache time

    expect(mockCallback).toHaveBeenCalledWith({ [timetoken]: ["test-user"] })

    stopReceipts()
  })

  test("should invite a user to the channel", async () => {
    const userToInvite = await createRandomUser()
    const membership = await channel.invite(userToInvite)

    expect(membership).toBeDefined()
    expect(membership.user.id).toEqual(userToInvite.id)
    expect(membership.channel.id).toEqual(channel.id)

    await userToInvite.delete()
  })

  test("should invite multiple users to the channel", async () => {
    const usersToInvite = await Promise.all([createRandomUser(), createRandomUser()])

    const invitedMemberships = await channel.inviteMultiple(usersToInvite)

    expect(invitedMemberships).toBeDefined()

    invitedMemberships.forEach((membership, index) => {
      const invitedUser = usersToInvite[index]
      expect(membership.user.id).toEqual(invitedUser.id)
      expect(membership.channel.id).toEqual(channel.id)
    })

    await Promise.all(usersToInvite.map((user) => user.delete()))
  })

  test("should verify if user is a member of a channel", async () => {
    const user = await createRandomUser()
    const channel = await createRandomChannel()

    const membership = await channel.invite(user)
    await sleep(200)
    const membersData = await channel.getMembers()

    expect(membership).toBeDefined()
    expect(membersData.members.find((member) => member.user.id === user.id)).toBeTruthy()

    await user.delete()
  })

  test("should verify if user is online on a channel", async () => {
    const chat2 = await createChatInstance({
      userId: "user-one",
      shouldCreateNewInstance: true,
    })

    const channel = await chat2.createChannel(`channel_${makeid()}`, {
      name: "Test Channel",
      description: "This is a test channel",
    })
    const disconnect = channel.connect(() => null)
    // wait till PN decides this user is online
    await sleep(2000)

    expect(await chat2.currentUser.isPresentOn(channel.id)).toBe(true)
    expect(await channel.isPresent(chat2.currentUser.id)).toBe(true)
    expect(await chat2.isPresent(chat2.currentUser.id, channel.id)).toBe(true)
    expect(await chat2.whoIsPresent(channel.id)).toContain(chat2.currentUser.id)

    disconnect()
    await sleep(2000)
    expect(await chat2.currentUser.isPresentOn(channel.id)).toBe(false)

    const usedUser = await chat2.getUser("user-one")

    if (usedUser) {
      await usedUser.delete()
    }
  })

  test("should verify all user-related mentions using getCurrentUserMentions()", async () => {
    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User1" })

    const user2Id = `user2_${Date.now()}`
    const user2 = await chat.createUser(user2Id, { name: "User2" })

    const messageText = `Hello, @${user1.name} and @${user2.name} here is a test mention`
    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name} and @Use`)
    messageDraft.addMentionedUser(user2, 1)
    await messageDraft.onChange(messageText)
    await messageDraft.send()

    await sleep(150) // history calls have around 130ms of cache time

    const mentionsResult = await chat.getCurrentUserMentions({ count: 10 })

    expect(mentionsResult).toBeDefined()

    await chat.deleteUser(user1.id)
    await chat.deleteUser(user2.id)
  })

  test("Should fail when trying to edit membership metadata of a non-existent channel", async () => {
    const nonExistentChannelId = "nonexistentchannelid"

    try {
      const nonExistentChannel = await chat.getChannel(nonExistentChannelId)
      const membership = await nonExistentChannel.join(() => null)
      await membership.update({ custom: { role: "admin" } })
      membership.disconnect()
      fail("Editing membership metadata of a non-existent channel should fail")
    } catch (error) {
      expect(error.message).toContain("Cannot read properties of null (reading 'join')")
    }
  })

  test("Should create direct conversation, edit, and delete a message", async () => {
    const user1 = await createRandomUser()

    const directConversation = await chat.createDirectConversation({
      user: user1,
      channelData: { name: "Test Convo" },
    })

    const messageText = "Hello from User1"
    await directConversation.channel.sendText(messageText)

    await sleep(150)

    const history = await directConversation.channel.getHistory()
    const sentMessage = history.messages.find((message) => message.content.text === messageText)

    const editedMessageText = "Edited message from User1"
    const editedMessage = await sentMessage.editText(editedMessageText)

    expect(editedMessage.text).toEqual(editedMessageText)

    const deletionResult = await editedMessage.delete()

    expect(deletionResult).toBe(true)

    await user1.delete()
  })

  test("should create, reply to, and delete a thread", async () => {
    const messageText = "Test message for thread creation"
    await channel.sendText(messageText)
    await sleep(150) // history calls have around 130ms of cache time

    let history = await channel.getHistory()
    let sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(false)

    const threadDraft = await sentMessage.createThread()
    await threadDraft.sendText("Initial message in the thread")
    history = await channel.getHistory()
    sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(true)

    const thread = await sentMessage.getThread()
    const replyText = "Replying to the thread"
    await thread.sendText(replyText)
    await sleep(150) // history calls have around 130ms of cache time
    const threadMessages = await thread.getHistory()
    expect(threadMessages.messages.some((message) => message.text === replyText)).toBe(true)

    await sentMessage.removeThread()
    history = await channel.getHistory()
    sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(false)
  })

  test("should create, reply to, delete a thread, and recreate the deleted thread", async () => {
    const messageText = "Test message for thread recreation"
    await channel.sendText(messageText)
    await sleep(150) // history calls have around 130ms of cache time

    let history = await channel.getHistory()
    let sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(false)

    let threadDraft = await sentMessage.createThread()
    await threadDraft.sendText("Initial message in the thread")
    history = await channel.getHistory()
    sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(true)

    const thread = await sentMessage.getThread()
    const replyText = "Replying to the thread"
    await thread.sendText(replyText)
    await sleep(150) // history calls have around 130ms of cache time
    let threadMessages = await thread.getHistory()
    expect(threadMessages.messages.some((message) => message.text === replyText)).toBe(true)

    await sentMessage.removeThread()
    history = await channel.getHistory()
    sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(false)

    threadDraft = await sentMessage.createThread()
    await threadDraft.sendText("Recreated thread after deletion")
    history = await channel.getHistory()
    sentMessage = history.messages[0]
    expect(sentMessage.hasThread).toBe(true)

    const newReplyText = "Replying to the recreated thread"
    await thread.sendText(newReplyText)
    await sleep(150)
    threadMessages = await thread.getHistory()
    expect(threadMessages.messages.some((message) => message.text === newReplyText)).toBe(true)
  })

  test("Should mention users with special characters in their names and validate mentioned users", async () => {
    const specialChar1 = ":-)"
    const specialChar2 = "V$$ap_}><{"

    const user1Id = `user1_${Date.now()}`
    const user2Id = `user2_${Date.now()}`

    const user1 = await chat.createUser(user1Id, { name: `User${specialChar1}1` })
    const user2 = await chat.createUser(user2Id, { name: `User${specialChar2}2` })

    const messageText = `Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`

    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name} and @Use`)
    messageDraft.addMentionedUser(user2, 1)
    await messageDraft.onChange(
      `Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`
    )

    await messageDraft.send()
    await sleep(150) // Wait for the message to be sent and cached

    const history = await channel.getHistory()

    const messageInHistory = history.messages.find(
      (message: any) => message.content.text === messageText
    )

    expect(messageInHistory).toBeDefined()

    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(2)
    expect(messageInHistory.mentionedUsers["0"].id).toEqual(user1.id)
    expect(messageInHistory.mentionedUsers["1"].id).toEqual(user2.id)

    await chat.deleteUser(user1.id)
    await chat.deleteUser(user2.id)
  })

  test("should create a group chat channel", async () => {
    const user1 = await createRandomUser()
    const user2 = await createRandomUser()
    const user3 = await createRandomUser()

    const channelId = "tg5984fd"
    const channelData = {
      name: "Group Chat Channel",
      description: "A channel for team collaboration",
      custom: {
        key: "value",
      },
    }

    const { channel, hostMembership, inviteesMemberships } = await chat.createGroupConversation({
      users: [user1, user2, user3],
      channelId,
      channelData,
    })

    expect(channel).toBeDefined()
    expect(channel.name).toEqual(channelData.name)
    expect(channel.description).toEqual(channelData.description)

    expect(hostMembership).toBeDefined()

    expect(inviteesMemberships).toBeDefined()
    expect(inviteesMemberships.length).toBe(3)

    expect(channel.custom).toEqual(channelData.custom)

    await channel.delete()
    await chat.deleteUser(user1.id)
    await chat.deleteUser(user2.id)
    await chat.deleteUser(user3.id)
  }, 15000)

  test("should create a public chat channel", async () => {
    const channelData = {
      name: "Public Chat Channel",
      description: "A channel for open conversations",
      custom: {
        key: "value",
      },
    }

    const channelToDelete = await chat.getChannel("public-channel-1")
    if (channelToDelete) {
        await channelToDelete.delete()
    }

    const publicChannel = await chat.createPublicConversation({
      channelId: "public-channel-1",
      channelData,
    })

    expect(publicChannel).toBeDefined()
    expect(publicChannel.name).toEqual(channelData.name)
    expect(publicChannel.description).toEqual(channelData.description)

    expect(publicChannel.custom).toEqual(channelData.custom)

    await publicChannel.delete()
  })

  test("canGetUserRestriction", async() => {
    const userId = generateRandomString(10)
    let user = await chatPamServer.createUser(userId, { name: "User123" });
    const channel = (await chatPamServer.createDirectConversation({ user: user})).channel;
    await channel.setRestrictions(user, {mute: true, reason: "rude"})
    await sleep(1250)
    let restrictions = await channel.getUserRestrictions(user);

    expect(restrictions.ban).toEqual(false);
    expect(restrictions.mute).toEqual(true);
    expect(restrictions.reason).toEqual("rude");

    await user.delete()
    await channel.delete()
  })

  test("should set (or lift) restrictions on a user", async () => {
    const notExistingUserId = generateRandomString(10)
    const moderationEventCallback = jest.fn()
    const notExistingChannelName = generateRandomString(10)

    const removeModerationListener = chatPamServerWithRefIntegrity.listenForEvents({
      channel: "PUBNUB_INTERNAL_MODERATION." + notExistingUserId,
      type: "moderation",
      callback: moderationEventCallback,
    })

    await chatPamServerWithRefIntegrity.setRestrictions(notExistingUserId, notExistingChannelName, {mute: true})
    await sleep(250) // Wait for the message to be sent and cached
    expect(moderationEventCallback).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            channelId: `PUBNUB_INTERNAL_MODERATION_${notExistingChannelName}`,
            restriction: "muted",
          }),
        })
    )
    moderationEventCallback.mockReset()

    await chatPamServerWithRefIntegrity.setRestrictions(notExistingUserId, notExistingChannelName, { ban: true })
    await sleep(250) // Wait for the message to be sent and cached
    expect(moderationEventCallback).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            channelId: `PUBNUB_INTERNAL_MODERATION_${notExistingChannelName}`,
            restriction: "banned",
          }),
        })
    )
    moderationEventCallback.mockReset()

    await chatPamServerWithRefIntegrity.setRestrictions(notExistingUserId, notExistingChannelName, { ban: true, mute: true })
    await sleep(250) // Wait for the message to be sent and cached
    expect(moderationEventCallback).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            channelId: `PUBNUB_INTERNAL_MODERATION_${notExistingChannelName}`,
            restriction: "banned",
          }),
        })
    )
    moderationEventCallback.mockReset()

    await chatPamServerWithRefIntegrity.setRestrictions(notExistingUserId, notExistingChannelName, {})
    await sleep(250) // Wait for the message to be sent and cached
    expect(moderationEventCallback).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            channelId: `PUBNUB_INTERNAL_MODERATION_${notExistingChannelName}`,
            restriction: "lifted",
          }),
        })
    )

    removeModerationListener()
  })

  test("Should get the number of unread messages on channels", async () => {
    jest.spyOn(chat.sdk.objects, "getMemberships")
    const commonParams = {
      include: {
        totalCount: true,
        customFields: true,
        channelFields: true,
        customChannelFields: true,
        channelTypeField: true,
        statusField: true,
        channelStatusField: true,
        typeField: true,
      },
      limit: null,
      page: null,
      sort: {},
      uuid: chat.currentUser.id,
    }

    await chat.getUnreadMessagesCounts({ filter: "channel.id like 'hello*'" })
    expect(chat.sdk.objects.getMemberships).toHaveBeenCalledWith({
      ...commonParams,
      filter: `!(channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*') && (channel.id like 'hello*')`,
    })

    await chat.getUnreadMessagesCounts({ filter: "channel.name like '*test-channel'" })
    expect(chat.sdk.objects.getMemberships).toHaveBeenCalledWith({
      ...commonParams,
      filter: `!(channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*') && (channel.name like '*test-channel')`,
    })

    await chat.getUnreadMessagesCounts()
    expect(chat.sdk.objects.getMemberships).toHaveBeenCalledWith({
      ...commonParams,
      filter: `!(channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*')`,
    })
  })

  test("should set 'eTag' and 'updated' on the Membership object", async () => {
    const membershipsDTOMock = {
      status: 200,
      data: [
        {
          channel: {
            id: "0053d903-62d5-4f14-91cc-50aa90b1ab30",
            name: "0053d903-62d5-4f14-91cc-50aa90b1ab30",
            description: null,
            type: "group",
            status: null,
            custom: null,
            updated: "2024-02-28T13:04:28.210319Z",
            eTag: "41ba0b6a52df2cc52775a83674ad4ba1",
          },
          status: null,
          custom: null,
          updated: "2024-02-28T13:04:28.645304Z",
          eTag: "AZO/t53al7m8fw",
        },
      ],
      totalCount: 309,
      next: "MTAw",
    }

    jest.spyOn(chat.sdk.objects, "getMemberships").mockImplementation(async () => membershipsDTOMock)

    const memberships = await chat.currentUser.getMemberships()
    expect(memberships.memberships[0].updated).toBe(membershipsDTOMock.data[0].updated)
    expect(memberships.memberships[0].eTag).toBe(membershipsDTOMock.data[0].eTag)
  })

  test("should set 'eTag' and 'updated' on the Membership object in real time", async () => {
    const groupConversation = await chat.createGroupConversation({ users: [chat.currentUser] })
    const hostMembership = groupConversation.hostMembership
    let updatedMembership: Membership | undefined
    const removeListener = hostMembership.streamUpdates((updatedMembershipData) => {
      updatedMembership = updatedMembershipData
    })
    await hostMembership.update({ custom: { hello: "world" } })
    await sleep(2000)
    expect(updatedMembership?.custom?.hello).toBe("world")
    expect(updatedMembership?.updated).toBeDefined()
    expect(updatedMembership?.eTag).toBeDefined()
    removeListener()
  })

  test("chat.getChannels with filter returns results", async () => {
      let result = await chat.getChannels({ limit: 2, filter: `type == 'public'` })
      expect(result.channels.length).toBe(2)
  })

  test("send report event", async () => {
    let receivedEvent
    const reason = "rude"
    const text = "reporting"
    const unsubscribe = chat.listenForEvents({
      channel: channel.id,
      type: "report",
      callback: (event) => {
        receivedEvent = event
      },
    })

    await sleep(1000)
    await chat.emitEvent({
      channel: channel.id,
      type: 'report',
      payload: {
        text: text,
        reason: reason
      }
    })
    await sleep(500)
    expect(receivedEvent.payload.text).toEqual(text)
    expect(receivedEvent.payload.reason).toEqual(reason)

    unsubscribe(); // Cleanup
  })

  test("send receipt event", async () => {
    let receivedEvent
    const messageTimetokenValue = "123"
    const eventTypeReceipt = "receipt"

    const unsubscribe = chat.listenForEvents({
      type: eventTypeReceipt,
      channel: channel.id,
      callback: event => {
        receivedEvent = event
      }
    })

    await sleep(1000)
    await chat.emitEvent({
      type: eventTypeReceipt,
      channel: channel.id,
      payload: {
        messageTimetoken: messageTimetokenValue
      }

    })

    await sleep(500)
    expect(receivedEvent.payload.messageTimetoken).toEqual(messageTimetokenValue)
    expect(receivedEvent.type).toEqual(eventTypeReceipt)

    unsubscribe() // cleanup
  })

  test("send custom event", async () => {
    const inviteCallback = jest.fn()
    const unsubscribe = chat.listenForEvents({
      channel: channel.id,
      type: "custom",
      method: "publish",
      callback: inviteCallback,
    })

    await sleep(1000)
    await chat.emitEvent({
      channel: channel.id,
      type: 'custom',
      method: 'publish',
      payload: {
        action: "action",
        body: "payload"
      }
    })
    await sleep(2000)
    expect(inviteCallback).toHaveBeenCalledTimes(1)
    expect(inviteCallback).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            action: "action",
            body: "payload"
          }),
        })
    )

    unsubscribe(); // Cleanup
  })

  test("use PubNub SDK types from Chat SDK", async () => {
    let channelMetadata = await chat.sdk.objects.getChannelMetadata({
      channel: channel.id,
      include: { customFields: true }
    })
    expect(channelMetadata).toBeDefined()
  })

  test("should properly disconnect from channel and stop receiving messages", async () => {
    const testUser = await createRandomUser()
    const directConversation = await chat.createDirectConversation({
      user: testUser,
      channelData: { name: "Test Direct Channel" }
    })

    const receivedMessages: Message[] = []
    let callbackCount = 0
    const messageCallback = (message: Message) => {
      receivedMessages.push(message)
      callbackCount++
    }

    const disconnect = directConversation.channel.connect(messageCallback)
    await sleep(2000) // Increased wait time for connection to establish

    // Send first message - should be received
    const message1 = "Test message 1"
    await directConversation.channel.sendText(message1)
    await sleep(1000) // Increased wait time for message processing

    expect(callbackCount).toBe(1)
    expect(receivedMessages[0].content.text).toBe(message1)

    disconnect()
    await sleep(2000) // Increased wait time for disconnect to take effect

    // Send second message - should NOT be received if disconnect works properly
    const message2 = "Test message 2"
    await directConversation.channel.sendText(message2)
    await sleep(1000) // Increased wait time for message processing
    expect(callbackCount).toBe(1)
    expect(receivedMessages.length).toBe(1)
    expect(receivedMessages[0].content.text).toBe(message1)

    // Cleanup
    await testUser.delete()
    await directConversation.channel.delete()
  }, 30000) // Added 30 second timeout

})
