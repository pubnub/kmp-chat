import {
  Channel,
  ThreadChannel,
  Message,
  Chat,
  MessageDraft,
} from "../dist-test"
import {
  sleep,
  extractMentionedUserIds,
  createChatInstance,
  sendMessageAndWaitForHistory,
  generateRandomString,
  createRandomChannel,
  createRandomUser
} from "./utils"

import { jest } from "@jest/globals"

describe("Channel test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let chatPamServer: Chat
  let chatPamServerWithRefIntegrity: Chat
  let channel: Channel
  let messageDraft: MessageDraft

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    chatPamServer = await createChatInstance( { userId: generateRandomString(), clientType: 'PamServer' })
    chatPamServerWithRefIntegrity = await createChatInstance({ userId: generateRandomString(), clientType: 'PamServerWithRefIntegrity' })
    channel = await createRandomChannel(chat)
    messageDraft = channel.createMessageDraft()
  }, 15000)

  afterEach(async () => {
    await channel.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()
    await chatPamServer.currentUser.delete()
    await chatPamServer.sdk.disconnect()
    await chatPamServerWithRefIntegrity.currentUser.delete()
    await chatPamServerWithRefIntegrity.sdk.disconnect()

    jest.clearAllMocks()
  }, 15000)

  test("should create a channel", async () => {
    const fetchedChannel = await chat.getChannel(channel.id)
    expect(fetchedChannel).toBeDefined()
    expect(fetchedChannel.name).toEqual(channel.name)
    expect(fetchedChannel.description).toEqual(channel.description)
  }, 20000)

  test("Should be able to delete channel", async () => {
    const { status } = await channel.delete( {soft: true} ) as Channel
    const deleteResult = await channel.delete()
    const fetchedChannel = await chat.getChannel(channel.id)

    expect(status).toBe("deleted")
    expect(deleteResult).toBe(true)
    expect(fetchedChannel).toBeNull()
  }, 20000)

  test("should get channel history", async () => {
    const messageText1 = "Test message 1"
    const messageText2 = "Test message 2"

    await channel.sendText(messageText1)
    await channel.sendText(messageText2)
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory()
    expect(history.messages.length).toBe(2)

    const messageTexts = history.messages.map(message => message.content.text)
    expect(messageTexts).toEqual(expect.arrayContaining([messageText1, messageText2]))
  }, 20000)

  test("should get channel history with pagination", async () => {
    const messageText1 = "Test message 1"
    const messageText2 = "Test message 2"
    const messageText3 = "Test message 3"

    await channel.sendText(messageText1)
    await channel.sendText(messageText2)
    await channel.sendText(messageText3)
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory({ count: 2 })
    expect(history.messages.length).toBe(2)
    expect(history.isMore).toBeTruthy()

    const secondPage = await channel.getHistory({ startTimetoken: history.messages[0].timetoken })
    expect(secondPage.messages.length).toBeGreaterThanOrEqual(1)
  }, 20000)

  test("should fetch unread messages counts with pagination", async () => {
    const channel1 = await createRandomChannel(chat)
    const channel2 = await createRandomChannel(chat)

    await channel1.invite(chat.currentUser)
    await channel2.invite(chat.currentUser)
    await channel1.sendText("Some text")
    await channel2.sendText("Some text")

    const firstResults = await chat.fetchUnreadMessagesCounts({ limit: 1 })
    expect(firstResults.countsByChannel.length).toEqual(1)
    expect(firstResults.countsByChannel[0].count).toEqual(1)
    expect([channel1.id, channel2.id]).toContain(firstResults.countsByChannel[0].channel.id)

    const secondResults = await chat.fetchUnreadMessagesCounts({ page: { next: firstResults.page.next } })
    expect(secondResults.countsByChannel.length).toEqual(1)
    expect(secondResults.countsByChannel[0].count).toEqual(1)
    expect([channel1.id, channel2.id]).toContain(secondResults.countsByChannel[0].channel.id)

    const thirdResults = await chat.fetchUnreadMessagesCounts({ page: { next: secondResults.page.next } })
    expect(thirdResults.countsByChannel.length).toEqual(0)

    await channel1.leave()
    await channel2.leave()
    await channel1.delete()
    await channel2.delete()
  }, 35000)

  test("should fail when trying to send a message to a non-existent channel", async () => {
    const nonExistentChannel = (await chat.getChannel("non-existing-channel")) as Channel

    try {
      await nonExistentChannel.sendText("Test message")
      fail("Should have thrown an error")
    } catch (error) {
      expect(error).toBeInstanceOf(Error)
    }
  }, 20000)

  test("should fail when trying to send a message to a deleted channel", async () => {
    await channel.delete()

    try {
      await channel.sendText("Test message")
      fail("Should have thrown an error")
    } catch (error) {
      expect(error).toBeInstanceOf(Error)
    }
  }, 20000)

  test("should fail when trying to get history of a deleted channel", async () => {
    await channel.delete()

    try {
      await channel.getHistory()
      fail("Should have thrown an error")
    } catch (error) {
      expect(error).toBeInstanceOf(Error)
    }
  }, 20000)

  test("should create a direct, group and public chats with a predefined ID", async () => {
    const someFakeDirectId = generateRandomString()
    const someFakeGroupId = generateRandomString()
    const someFakePublicId = generateRandomString()
    const user = await createRandomUser(chat)

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
    await user.delete()
  }, 20000)

  test("should create direct conversation and send message", async () => {
    const user = await createRandomUser(chat)
    const directConversation = await chat.createDirectConversation({ user, channelData: { name: "Test Convo" }})

    expect(directConversation).toBeDefined()

    const messageText = "Hello from User1"
    await directConversation.channel.sendText(messageText)
    await sleep(300) // History calls have around 130ms of cache time

    const history = await directConversation.channel.getHistory()
    const messageInHistory = history.messages.some((message: Message) => message.content.text === messageText)
    expect(messageInHistory).toBeTruthy()

    await directConversation.channel.delete()
    await user.delete()
  }, 20000)

  test("should create group conversation", async () => {
    const [user1, user2, user3] = await Promise.all([
      createRandomUser(chat),
      createRandomUser(chat),
      createRandomUser(chat),
    ])

    const channelId = generateRandomString()
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

    await sleep(150) // History calls have around 130ms of cache time

    await Promise.all([
      user1.delete(),
      user2.delete(),
      user3.delete(),
      channel.delete({ soft: false }),
    ])
  }, 20000)

  test("should create a thread", async () => {
    const messageText = "Test message"
    await channel.sendText(messageText)
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeThread = await channel.getHistory()
    const messageBeforeThread = historyBeforeThread.messages[0]

    expect(messageBeforeThread.hasThread).toBe(false)

    const threadDraft = await messageBeforeThread.createThread()
    await threadDraft.sendText("Some random text in a thread")
    await sleep(150)

    const historyAfterThread = await channel.getHistory()
    const messageWithThread = historyAfterThread.messages[0]

    expect(messageWithThread.hasThread).toBe(true)

    const thread = await messageWithThread.getThread()
    const threadText = "Whatever text"
    await thread.sendText(threadText)
    await sleep(150) // History calls have around 130ms of cache time
    const threadMessages = await thread.getHistory()

    expect(threadMessages.messages.some((message) => message.text === threadText)).toBe(true)
  }, 20000)

  test("should not be able to create an empty Thread without any message", async () => {
    let errorOccurred = false

    try {
      await channel.createThread()
    } catch (error) {
      errorOccurred = true
    }

    expect(errorOccurred).toBe(true)
  }, 20000)

  test("streamMessageReport should receive reported message", async () => {
    const messageTextToReport = "Message to report"
    const reason = "rude";

    let receivedEvent

    const unsubscribe = channel.streamMessageReports((event) => {
      receivedEvent = event
    })

    const { timetoken } = await channel.sendText(messageTextToReport);
    await sleep(1000) // History calls have around 130ms of cache time
    const message = await channel.getMessage(timetoken);

    await message.report(reason);
    await sleep(500)

    expect(receivedEvent.payload.text).toEqual(messageTextToReport);
    expect(receivedEvent.payload.reason).toEqual(reason);

    unsubscribe()
  }, 20000);

  test("should stream channel updates and invoke the callback", async () => {
    let updatedChannel
    let callbackCount = 0

    channel = await channel.update({ type: "public" })

    const name = "Updated Channel"
    const callback = (ch) => {
      updatedChannel = ch
      callbackCount++
    }

    const stopUpdates = channel.streamUpdates(callback)
    await sleep(1500)
    await channel.update({ name })
    await sleep(500)

    expect(callbackCount).toBe(1)
    expect(updatedChannel).toBeDefined()
    expect(updatedChannel.name).toEqual(name)
    expect(updatedChannel.type).toEqual(channel.type)

    stopUpdates()
  }, 20000)

  test("should stream channel updates via Channel.streamUpdatesOn", async () => {
    let updatedChannels: Channel[] = []
    let callbackCount = 0

    channel = await channel.update({ type: "public" })

    const name = "Updated Channel via streamUpdatesOn"
    const callback = (channels: Channel[]) => {
      updatedChannels = channels
      callbackCount++
    }

    const stopUpdates = Channel.streamUpdatesOn([channel], callback)
    await sleep(1000)
    await channel.update({ name })
    await sleep(300)

    expect(callbackCount).toBe(1)
    expect(updatedChannels).toBeDefined()
    expect(updatedChannels.length).toBe(1)
    expect(updatedChannels[0].name).toEqual(name)
    expect(updatedChannels[0].type).toEqual(channel.type)

    stopUpdates()
  }, 20000)

  test("should stream thread channel updates via ThreadChannel.streamUpdatesOn", async () => {
    let updatedThreadChannels: ThreadChannel[] = []
    let callbackCount = 0

    const messageText = "Parent message for thread updates"
    await channel.sendText(messageText)
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]

    const threadChannel = await parentMessage.createThread()
    await threadChannel.sendText("Initial thread message")
    await sleep(150)

    const callback = (channels: ThreadChannel[]) => {
      updatedThreadChannels = channels
      callbackCount++
    }

    const stopUpdates = ThreadChannel.streamUpdatesOn([threadChannel], callback)
    const updatedName = "Updated Thread Channel"
    await threadChannel.update({ name: updatedName })
    await sleep(150)

    expect(callbackCount).toBe(1)
    expect(updatedThreadChannels).toBeDefined()
    expect(updatedThreadChannels.length).toBe(1)
    expect(updatedThreadChannels[0].name).toEqual(updatedName)

    stopUpdates()

    // await threadChannel.delete()
  }, 20000)

  test("should get unread messages count", async () => {
    const messageText1 = "Test message 1"
    const messageText2 = "Test message 2"

    await channel.sendText(messageText1)
    await channel.sendText(messageText2)
    await sleep(150) // History calls have around 130ms of cache time

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
  }, 20000)

  test("should add mentioned users to message draft", async () => {
    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User1" })

    const user2Id = `user2_${Date.now()}`
    const user2 = await chat.createUser(user2Id, { name: "User2" })

    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name} and @Use`)
    messageDraft.addMentionedUser(user2, 1)
    await messageDraft.onChange(`Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`)

    await messageDraft.send()
    await sleep(150)
    const history = await channel.getHistory()
    const messageInHistory = history.messages[0]

    const messageElements = messageInHistory.getMessageElements()
    expect(messageElements).toBeDefined()
    expect(messageElements.length).toBeGreaterThan(0)

    const mentionElements = messageElements.filter(element => element.type === "mention")
    expect(mentionElements.length).toBe(2)
    expect(mentionElements[0].content.id).toEqual(user1.id)
    expect(mentionElements[0].content.name).toEqual(user1.name)
    expect(mentionElements[1].content.id).toEqual(user2.id)
    expect(mentionElements[1].content.name).toEqual(user2.name)

    await Promise.all([user1.delete(), user2.delete()])
  }, 20000)

  test("should persist mentioned users when message is sent", async () => {
    const user1Id = `user1_${Date.now()}`
    const user1 = await chat.createUser(user1Id, { name: "User1" })
    const user2Id = `user2_${Date.now()}`
    const user2 = await chat.createUser(user2Id, { name: "User2" })
    const messageText = `Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`

    await messageDraft.onChange("Hello, @Use")
    messageDraft.addMentionedUser(user1, 0)
    await messageDraft.onChange(`Hello, @${user1.name} and @Use`)
    messageDraft.addMentionedUser(user2, 1)
    await messageDraft.onChange(messageText)

    await messageDraft.send()
    await sleep(150) // History calls have around 130ms of cache time

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

    await Promise.all([user1.delete(), user2.delete()])
  }, 20000)

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
    await messageDraft.onChange(`Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`)

    await messageDraft.send()
    await sleep(150) // History calls have around 130ms of cache time

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
  }, 20000)

  test("should send a message with words that start with @ but are not user mentions", async () => {
    const messageText = "Test message with words that start with @ but are not user mentions: @test, @example, @check."
    const messageInHistory = await sendMessageAndWaitForHistory(channel.createMessageDraft(messageText), channel)

    expect(messageInHistory).toBeDefined()
    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(0)
  }, 20000)

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
  }, 20000)

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
  }, 20000)

  test("should remove mentioned user from draft", async () => {
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
    messageDraft.removeMentionedUser(1)

    await messageDraft.send()
    await sleep(150)
    const history = await channel.getHistory()
    const messageInHistory = history.messages[0]

    const messageElements = messageInHistory.getMessageElements()
    expect(messageElements).toBeDefined()
    expect(messageElements.length).toBeGreaterThan(0)

    const mentionElements = messageElements.filter(element => element.type === "mention")
    expect(mentionElements.length).toBe(1)
    expect(mentionElements[0].content.id).toEqual(user1.id)
    expect(mentionElements[0].content.name).toEqual(user1.name)

    await Promise.all([user1.delete(), user2.delete()])
  }, 20000)

  test("should persist only remaining mentions after removal", async () => {
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
    messageDraft.removeMentionedUser(1)

    const messageInHistory = await sendMessageAndWaitForHistory(messageDraft, channel)

    expect(messageInHistory).toBeDefined()
    expect(Object.keys(messageInHistory.mentionedUsers).length).toBe(1)
    expect(messageInHistory.mentionedUsers["0"].id).toBe(user1.id)

    await Promise.all([user1.delete(), user2.delete()])
  }, 20000)

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
    await messageDraft.onChange(`Hello, @${user1.name}, how are you? @${user2.name}, are you there? Test: @Use`)
    messageDraft.addMentionedUser(user3, 2)

    expect(messageDraft.value).toEqual(`Hello, @${user1.name}, how are you? @User 2, are you there? Test: @${user3.name}`)
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
  }, 20000)

  test("should add and remove a quote", async () => {
    const messageText = "Test message"
    await channel.sendText(messageText)
    await sleep(150) // History calls have around 130ms of cache time
    const history = await channel.getHistory()
    const sentMessage: Message = history.messages[0]

    messageDraft.addQuote(sentMessage)
    expect(messageDraft.quotedMessage).toEqual(sentMessage)
    messageDraft.removeQuote()
    expect(messageDraft.quotedMessage).toBeUndefined()
  }, 20000)

  test("should throw an error when trying to quote a message from another channel", async () => {
    const otherChannel = await createRandomChannel(chat)

    try {
      const messageText = "Test message"
      await otherChannel.sendText(messageText)
      await sleep(150) // History calls have around 130ms of cache time
      const history = await otherChannel.getHistory()
      const otherMessage: Message = history.messages[0]
      messageDraft.addQuote(otherMessage)
      fail("Should have thrown an error")
    } catch (error) {
      expect(error.message).toEqual("You cannot quote messages from other channels")
    } finally {
      await otherChannel.delete()
    }
  }, 20000)

  test("should quote multiple messages", async () => {
    const messageText1 = "Test message 1"
    const messageText2 = "Test message 2"
    const messageText3 = "Test message 3"

    await channel.sendText(messageText1)
    await channel.sendText(messageText2)
    await channel.sendText(messageText3)
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory()
    const messageDraft = channel.createMessageDraft()

    messageDraft.addQuote(history.messages[0])
    messageDraft.addQuote(history.messages[1])
    messageDraft.addQuote(history.messages[2])

    expect(messageDraft.quotedMessage).toEqual(history.messages[2])
    messageDraft.removeQuote()
    expect(messageDraft.quotedMessage).toBeUndefined()
  }, 20000)

  test("should invite a user to the channel", async () => {
    const userToInvite = await createRandomUser(chat)
    const membership = await channel.invite(userToInvite)

    expect(membership).toBeDefined()
    expect(membership.user.id).toEqual(userToInvite.id)
    expect(membership.channel.id).toEqual(channel.id)

    await channel.leave()
    await userToInvite.delete()
  }, 20000)

  test("should invite multiple users to the channel", async () => {
    const usersToInvite = await Promise.all([createRandomUser(chat), createRandomUser(chat)])
    const invitedMemberships = await channel.inviteMultiple(usersToInvite)

    expect(invitedMemberships).toBeDefined()
    expect(invitedMemberships.length).toBe(usersToInvite.length)

    const invitedUserIds = usersToInvite.map(user => user.id)
    const membershipUserIds = invitedMemberships.map(membership => membership.user.id)

    invitedUserIds.forEach(userId => {
      expect(membershipUserIds).toContain(userId)
    })
    invitedMemberships.forEach(membership => {
      expect(membership.channel.id).toEqual(channel.id)
    })

    await Promise.all(invitedMemberships.map(membership => membership.channel.leave()))
    await Promise.all(usersToInvite.map((user) => user.delete()))
  }, 20000)

  test("should verify if user is a member of a channel", async () => {
    const user = await createRandomUser(chat)
    const channel = await createRandomChannel(chat)

    const membership = await channel.invite(user)
    await sleep(200)
    const membersData = await channel.getMembers()

    expect(membership).toBeDefined()
    expect(membersData.members.find((member) => member.user.id === user.id)).toBeTruthy()

    await channel.leave()
    await channel.delete()
    await user.delete()
  }, 20000)

  test("should verify if user is online on a channel", async () => {
    const chat2 = await createChatInstance({
      userId: "user-one",
    })
    const channel = await chat2.createChannel(generateRandomString(), {
      name: "Test Channel",
      description: "This is a test channel",
    })

    const disconnect = channel.connect(() => null)

    // Wait till PN decides this user is online
    await sleep(2000)

    expect(await chat2.currentUser.isPresentOn(channel.id)).toBe(true)
    expect(await channel.isPresent(chat2.currentUser.id)).toBe(true)
    expect(await chat2.isPresent(chat2.currentUser.id, channel.id)).toBe(true)
    expect(await chat2.whoIsPresent(channel.id)).toContain(chat2.currentUser.id)

    disconnect()
    await sleep(2000)
    expect(await chat2.currentUser.isPresentOn(channel.id)).toBe(false)

    await channel.delete()
    await chat2.currentUser.delete()
  }, 20000)

  test("should get present users on channel via channel.whoIsPresent with limit parameter", async () => {
    const chat2 = await createChatInstance({
      userId: "user-two",
    })
    const channel = await chat2.createChannel(generateRandomString(), {
      name: "Test Channel for whoIsPresent",
    })

    const disconnect = channel.connect(() => null)
    await sleep(2000)

    const presentUsers = await channel.whoIsPresent({ limit: 10 })

    expect(Array.isArray(presentUsers)).toBe(true)
    expect(presentUsers).toContain(chat2.currentUser.id)

    disconnect()
    await channel.delete()
    await chat2.currentUser.delete()
  }, 20000)

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

    await sleep(150) // History calls have around 130ms of cache time
    const mentionsResult = await chat.getCurrentUserMentions({ count: 10 })

    expect(mentionsResult).toBeDefined()

    await chat.deleteUser(user1.id)
    await chat.deleteUser(user2.id)
  }, 20000)

  test("should create direct conversation", async () => {
    const user1 = await createRandomUser(chat)
    const directConversation = await chat.createDirectConversation({
      user: user1,
      channelData: { name: "Test Convo" },
    })

    expect(directConversation).toBeDefined()
    expect(directConversation.channel).toBeDefined()
    expect(directConversation.channel.name).toEqual("Test Convo")
    expect(directConversation.hostMembership.user.id).toBe(chat.currentUser.id)
    expect(directConversation.inviteeMembership.user.id).toBe(user1.id)

    await Promise.all([user1.delete(), directConversation.channel.delete()])
  }, 20000)

  test("should create thread on message", async () => {
    const messageText = "Test message for thread creation"
    await channel.sendText(messageText)
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeThread = await channel.getHistory()
    const messageBeforeThread = historyBeforeThread.messages[0]
    expect(messageBeforeThread.hasThread).toBe(false)

    const threadChannel = await messageBeforeThread.createThread()
    await threadChannel.sendText("Initial message in the thread")
    await sleep(150)

    const historyAfterThread = await channel.getHistory()
    const messageWithThread = historyAfterThread.messages[0]
    expect(messageWithThread.hasThread).toBe(true)

    await messageWithThread.removeThread()
  }, 20000)

  test("should reply to thread", async () => {
    const messageText = "Test message for thread reply"
    await channel.sendText(messageText)
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory()
    const message = history.messages[0]

    const threadChannel = await message.createThread()
    await threadChannel.sendText("Initial message in the thread")
    await sleep(150)

    const replyText = "Replying to the thread"
    await threadChannel.sendText(replyText)
    await sleep(150)

    const threadMessages = await threadChannel.getHistory()
    expect(threadMessages.messages.some((message) => message.text === replyText)).toBe(true)

    // await threadChannel.delete()
  }, 20000)

  test("should delete thread", async () => {
    const messageText = "Test message for thread deletion"
    await channel.sendText(messageText)
    await sleep(150) // History calls have around 130ms of cache time

    const initialHistory = await channel.getHistory()
    const initialMessage = initialHistory.messages[0]

    const threadChannel = await initialMessage.createThread()
    await threadChannel.sendText("Initial message in the thread")
    await sleep(150)

    const historyWithThread = await channel.getHistory()
    const messageWithThread = historyWithThread.messages[0]

    expect(messageWithThread.hasThread).toBe(true)

    await messageWithThread.removeThread()
    await sleep(150)

    const historyAfterRemoval = await channel.getHistory()
    const messageAfterRemoval = historyAfterRemoval.messages[0]
    expect(messageAfterRemoval.hasThread).toBe(false)

    // await threadChannel.delete()
  }, 20000)

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
    await messageDraft.onChange(`Hello, @${user1.name} and @${user2.name} here is my mail test@pubnub.com`)

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
  }, 20000)

  test("should create a group chat channel", async () => {
    const user1 = await createRandomUser(chat)
    const user2 = await createRandomUser(chat)
    const user3 = await createRandomUser(chat)

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
  }, 25000)

  test("should create a public chat channel", async () => {
    const channelData = {
      name: "Public Chat Channel",
      description: "A channel for open conversations",
      custom: {
        key: "value",
      },
    }

    const publicChannel = await chat.createPublicConversation({
      channelId: generateRandomString(),
      channelData,
    })

    expect(publicChannel).toBeDefined()
    expect(publicChannel.name).toEqual(channelData.name)
    expect(publicChannel.description).toEqual(channelData.description)
    expect(publicChannel.custom).toEqual(channelData.custom)

    await publicChannel.delete()
  }, 20000)

  test("should set user restrictions on channel", async() => {
    const user = await chatPamServer.createUser(generateRandomString(), { name: "User123" });
    const channel = (await chatPamServer.createDirectConversation({ user: user})).channel;

    await channel.setRestrictions(user, { mute: true, reason: "rude" })
    await sleep(1250)

    const restrictions = await channel.getUserRestrictions(user);

    expect(restrictions.mute).toEqual(true)
    expect(restrictions.reason).toEqual("rude")

    await Promise.all([user.delete(), channel.delete()])
  }, 30000)

  test("should get user restrictions from channel", async() => {
    const userId = generateRandomString()
    const user = await chatPamServer.createUser(userId, { name: "User123" });
    const channel = (await chatPamServer.createDirectConversation({ user: user})).channel;

    await channel.setRestrictions(user, { mute: true, ban: false, reason: "rude" })
    await sleep(1000)

    const restrictions = await channel.getUserRestrictions(user);

    expect(restrictions.ban).toEqual(false);
    expect(restrictions.mute).toEqual(true);
    expect(restrictions.reason).toEqual("rude");

    await Promise.all([user.delete(), channel.delete()])
  }, 20000)

  test("should set (or lift) restrictions on a user", async () => {
    const notExistingUserId = generateRandomString()
    const moderationEventCallback = jest.fn()
    const notExistingChannelName = generateRandomString()

    const removeModerationListener = chatPamServerWithRefIntegrity.listenForEvents({
      channel: "PUBNUB_INTERNAL_MODERATION." + notExistingUserId,
      type: "moderation",
      callback: moderationEventCallback,
    })

    await chatPamServerWithRefIntegrity.setRestrictions(notExistingUserId, notExistingChannelName, { mute: true })
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
  }, 20000)

  test("chat.getChannels with filter returns results", async () => {
      const result = await chat.getChannels({ limit: 2, filter: `type == 'public'` })
      expect(result.channels.length).toBe(2)
  }, 20000)

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

    unsubscribe()
  }, 20000)

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

    unsubscribe()
  }, 20000)

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

    unsubscribe()
  }, 20000)

  test("use PubNub SDK types from Chat SDK", async () => {
    const channelMetadata = await chat.sdk.objects.getChannelMetadata({
      channel: channel.id,
      include: { customFields: true }
    })
    expect(channelMetadata).toBeDefined()
  }, 20000)

  test("should properly disconnect from channel and stop receiving messages", async () => {
    const testUser = await createRandomUser(chat)
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
  }, 30000)

  test("should update channel via chat.updateChannel", async () => {
    const newName = "Updated Channel Name"
    const newDescription = "Updated description"

    const updatedChannel = await chat.updateChannel(channel.id, {
      name: newName,
      description: newDescription
    })

    expect(updatedChannel.name).toEqual(newName)
    expect(updatedChannel.description).toEqual(newDescription)
    expect(updatedChannel.id).toEqual(channel.id)
  }, 20000)

  test("should delete channel via chat.deleteChannel", async () => {
    const testChannel = await createRandomChannel(chat)
    const channelId = testChannel.id
    const result = await chat.deleteChannel(channelId)

    expect(result).toBe(true)
    const deletedChannel = await chat.getChannel(channelId)
    expect(deletedChannel).toBeNull()
  }, 20000)

  test("should forward message to another channel", async () => {
    const targetChannel = await createRandomChannel(chat)
    const messageText = "Message to forward"

    await channel.sendText(messageText)
    await sleep(350)

    const history = await channel.getHistory()
    const messageToForward = history.messages[0]

    await targetChannel.forwardMessage(messageToForward)
    await sleep(350)

    const targetHistory = await targetChannel.getHistory()
    expect(targetHistory.messages.length).toBeGreaterThan(0)
    expect(targetHistory.messages[0].content.text).toEqual(messageText)

    await targetChannel.delete()
  }, 20000)

  test("should get pinned message from channel", async () => {
    const messageText = "Message to pin"
    await channel.sendText(messageText)
    await sleep(1000)

    const history = await channel.getHistory()
    const message = history.messages[0]

    const updatedChannel = await channel.pinMessage(message)
    const pinnedMessage = await updatedChannel.getPinnedMessage()

    expect(pinnedMessage).toBeDefined()
    expect(pinnedMessage?.content.text).toEqual(messageText)
  }, 20000)

  test("should mark all messages as read via chat.markAllMessagesAsRead", async () => {
    await channel.sendText("Message 1")
    await channel.sendText("Message 2")
    await channel.sendText("Message 3")
    await sleep(300)

    await chat.markAllMessagesAsRead()
    await sleep(300)

    const counts = await chat.getUnreadMessagesCounts()
    expect(counts.length).toBe(0)
  }, 20000)

  test("should unpin message from thread channel", async () => {
    await channel.sendText("Parent for thread")
    await sleep(150)

    const parentHistory = await channel.getHistory()
    const parentMessage = parentHistory.messages[0]

    const threadChannel = await parentMessage.createThread()
    await threadChannel.sendText("Thread message to pin")
    await sleep(300)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]

    await threadChannel.pinMessage(threadMessage)
    await sleep(300)

    const pinnedMessageAfterPin = await threadChannel.getPinnedMessage()
    expect(pinnedMessageAfterPin).toBeDefined()

    await threadChannel.unpinMessage()
    await sleep(150)

    const pinnedMessageAfterUnpin = await threadChannel.getPinnedMessage()
    expect(pinnedMessageAfterUnpin).toBeNull()

    // await threadChannel.delete()
  }, 20000)

  test("should pin thread message to parent channel", async () => {
    await channel.sendText("Parent message")
    await sleep(500)

    const history = await channel.getHistory()
    const message = history.messages[0]

    const threadChannel = await message.createThread()
    await threadChannel.sendText("Thread message")
    await sleep(1000)

    const threadChannelHistory = await threadChannel.getHistory()
    const threadMessage = threadChannelHistory.messages[0]

    const updatedParentChannel = await threadChannel.pinMessageToParentChannel(threadMessage)
    const pinnedInParent = await updatedParentChannel.getPinnedMessage()

    expect(pinnedInParent).toBeDefined()
    expect(pinnedInParent?.content.text).toEqual("Thread message")

    // await threadChannel.delete()
  }, 20000)

  test("should unpin thread message from parent channel", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const message = history.messages[0]

    const threadChannel = await message.createThread()
    await threadChannel.sendText("Thread message to unpin")
    await sleep(150)

    const threadChannelHistory = await threadChannel.getHistory()
    const threadMessage = threadChannelHistory.messages[0]

    await threadChannel.pinMessageToParentChannel(threadMessage)
    await sleep(150)

    const pinnedMessageAfterPin = await channel.getPinnedMessage()
    expect(pinnedMessageAfterPin).toBeDefined()

    await threadChannel.unpinMessageFromParentChannel()
    await sleep(150)

    const pinnedMessageAfterUnpin = await channel.getPinnedMessage()
    expect(pinnedMessageAfterUnpin).toBeNull()

    // await threadChannel.delete()
  }, 20000)

  test("should unpin message from channel", async () => {
    const messageText = "Message to pin and unpin"
    await channel.sendText(messageText)
    await sleep(1000)

    const history = await channel.getHistory()
    const message = history.messages[0]

    const updatedChannel = await channel.pinMessage(message)
    await sleep(500)

    const pinnedMessageBeforeUnpin = await updatedChannel.getPinnedMessage()
    expect(pinnedMessageBeforeUnpin).toBeDefined()

    await channel.unpinMessage()
    await sleep(500)

    const pinnedMessageAfterUnpin = await channel.getPinnedMessage()
    expect(pinnedMessageAfterUnpin).toBeNull()
  }, 20000)

  test("should handle typing indicators", async () => {
    const typingUsers: string[][] = []
    const stopTypingListener = channel.getTyping((userIds) => {
      typingUsers.push(userIds)
    })

    await sleep(500)
    await channel.startTyping()
    await sleep(500)

    expect(typingUsers.length).toBeGreaterThan(0)
    expect(typingUsers.some(users => users.includes(chat.currentUser.id))).toBeTruthy()

    stopTypingListener()
    await channel.stopTyping()
  }, 20000)

  test("should get users restrictions from channel", async () => {
    const user1 = await chatPamServer.createUser(generateRandomString(), { name: "User1" })
    const user2 = await chatPamServer.createUser(generateRandomString(), { name: "User2" })
    const testChannel = (await chatPamServer.createGroupConversation({ users: [user1, user2] })).channel

    await testChannel.setRestrictions(user1, { mute: true, reason: "spam" })
    await testChannel.setRestrictions(user2, { ban: true, reason: "abuse" })
    await sleep(1000)

    const restrictions = await testChannel.getUsersRestrictions({ limit: 10 })

    expect(restrictions).toBeDefined()
    expect(restrictions.restrictions.length).toBeGreaterThan(0)

    const user1Restriction = restrictions.restrictions.find(r => r.userId === user1.id)
    expect(user1Restriction).toBeDefined()
    expect(user1Restriction?.mute).toBe(true)

    await Promise.all([user1.delete(), user2.delete(), testChannel.delete()])
  }, 30000)

  test("should get message reports history", async () => {
    const messageText = "Message to report"
    const reason = "inappropriate"

    const { timetoken } = await channel.sendText(messageText)
    await sleep(500)
    const message = await channel.getMessage(timetoken)

    await message.report(reason)
    await sleep(500)
    const reportsHistory = await channel.getMessageReportsHistory({ count: 10 })

    expect(reportsHistory).toBeDefined()
    expect(reportsHistory.events.length).toBeGreaterThan(0)
    expect(reportsHistory.events[0].payload.reason).toEqual(reason)
  }, 20000)

  test("should upload and retrieve files from channel", async () => {
    const fileContent = "Test file content for PubNub"
    const buffer = Buffer.from(fileContent, 'utf-8')
    const file = chat.sdk.File.create({ name: "test.txt", mimeType: "text/plain", data: buffer })

    const messageText = "Message with file"
    await channel.sendText(messageText, { files: [file] })
    await sleep(1000)

    const filesResult = await channel.getFiles({ limit: 10 })
    expect(filesResult).toBeDefined()
    expect(filesResult.files.length).toBeGreaterThan(0)

    const uploadedFile = filesResult.files[0]
    expect(uploadedFile.name).toBe("test.txt")

    const deleteResult = await channel.deleteFile({ id: uploadedFile.id, name: uploadedFile.name })
    expect(deleteResult).toBeDefined()
    expect(deleteResult.status).toBe(200)
  }, 20000)

  test("should stream presence updates on a channel", async () => {
    const testChannel = await chat.createPublicConversation({
      channelId: generateRandomString(),
      channelData: { name: "Presence Test Channel" }
    })

    const presenceUpdates: string[][] = []
    const stopPresenceStream = await testChannel.streamPresence((userIds) => {
      presenceUpdates.push(userIds)
    })

    const disconnect = testChannel.connect(() => null)
    await sleep(3000) // Wait for presence to propagate

    expect(presenceUpdates.length).toBeGreaterThan(0)
    const latestPresence = presenceUpdates[presenceUpdates.length - 1]
    expect(latestPresence).toContain(chat.currentUser.id)

    stopPresenceStream()
    disconnect()

    await testChannel.leave()
    await testChannel.delete()
  }, 30000)

  test("should fetch read receipts on a channel", async () => {
    const testUser = await createRandomUser(chat)
    const directConversation = await chat.createDirectConversation({ user: testUser })

    const publishResult = await directConversation.channel.sendText("Test message for receipts")
    await directConversation.hostMembership.setLastReadMessageTimetoken(publishResult.timetoken.toString())
    await sleep(500)

    const response = await directConversation.channel.fetchReadReceipts()
    expect(Array.isArray(response.receipts)).toBe(true)
    expect(response.total).toBeDefined()
    expect(response.page).toBeDefined()

    const myReceipt = response.receipts.find((r) => r.userId === chat.currentUser.id)
    expect(myReceipt).toBeDefined()
    expect(myReceipt?.userId).toBe(chat.currentUser.id)
    expect(myReceipt?.lastReadTimetoken).toBe(publishResult.timetoken.toString())

    await directConversation.channel.leave()
    await testUser.delete()
    await directConversation.channel.delete()
  }, 30000)

  test("should stream read receipts on a channel", async () => {
    const testUser = await createRandomUser(chat)
    const directConversation = await chat.createDirectConversation({ user: testUser })

    let receivedReceipt: { userId: string; lastReadTimetoken: string } | undefined
    let callbackCount = 0

    const stopReceiptsStream = directConversation.channel.streamReadReceipts((receipt) => {
      receivedReceipt = receipt
      callbackCount++
    })

    await sleep(1000)
    const message = await directConversation.channel.sendText("Test message")
    await directConversation.hostMembership.setLastReadMessageTimetoken(message.timetoken.toString())
    await sleep(1000)

    expect(callbackCount).toBeGreaterThan(0)
    expect(receivedReceipt).toBeDefined()
    expect(receivedReceipt!.userId).toBe(chat.currentUser.id)

    stopReceiptsStream()

    await directConversation.channel.leave()
    await testUser.delete()
    await directConversation.channel.delete()
  }, 30000)

  test("should check if user is a member of channel via channel.hasMember", async () => {
    const user = await createRandomUser(chat)
    const testChannel = await createRandomChannel(chat)

    await testChannel.invite(user)
    await sleep(200)

    const hasMember = await testChannel.hasMember(user.id)
    expect(hasMember).toBe(true)

    const nonMemberUser = await createRandomUser(chat)
    const hasNonMember = await testChannel.hasMember(nonMemberUser.id)
    expect(hasNonMember).toBe(false)

    await Promise.all([user.delete(), nonMemberUser.delete(), testChannel.delete()])
  }, 20000)

  test("should get member from channel via channel.getMember", async () => {
    const user = await createRandomUser(chat)
    const testChannel = await createRandomChannel(chat)

    await testChannel.invite(user)
    await sleep(200)

    const membership = await testChannel.getMember(user.id)
    expect(membership).toBeDefined()
    expect(membership?.user.id).toBe(user.id)
    expect(membership?.channel.id).toBe(testChannel.id)

    const nonMemberUser = await createRandomUser(chat)
    const nonMemberMembership = await testChannel.getMember(nonMemberUser.id)
    expect(nonMemberMembership).toBeNull()

    await Promise.all([user.delete(), nonMemberUser.delete(), testChannel.delete()])
  }, 20000)
})