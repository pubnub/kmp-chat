import { Chat, Channel, User, Event } from "../dist-test"
import { createChatInstance, generateRandomString, sleep, createRandomChannel, createRandomUser } from "./utils"
import { jest } from "@jest/globals"

describe("Chat tests", () => {
  jest.retryTimes(3)

  let chat: Chat

  beforeAll(async () => {
    chat = await createChatInstance({
      userId: generateRandomString()
    })
  }, 15000)

  afterEach(() => {
    jest.clearAllMocks()
  })

  afterAll(async () => {
    await chat.currentUser.delete()
  }, 15000)

  test("should emit and listen for custom events", async () => {
    const channel = await createRandomChannel(chat)
    let receivedEvent: any

    const unsubscribe = chat.listenForEvents({
      channel: channel.id,
      type: "custom",
      method: "publish",
      callback: (event) => { receivedEvent = event }
    })

    await sleep(1000)
    await chat.emitEvent({
      channel: channel.id,
      type: "custom",
      method: "publish",
      payload: { testData: "test value" }
    })

    await sleep(500)
    expect(receivedEvent).toBeDefined()
    expect(receivedEvent?.payload.testData).toEqual("test value")

    unsubscribe()
    await channel.delete()
  }, 20000)

  test("should get events history", async () => {
    const channel = await createRandomChannel(chat)

    await chat.emitEvent({
      channel: channel.id,
      type: "report",
      payload: { text: "test", reason: "spam" }
    })

    await sleep(500)
    const { events, isMore } = await chat.getEventsHistory({
      channel: channel.id,
      count: 10
    })

    expect(events).toBeDefined()
    expect(events.length).toEqual(1)
    expect(events[0].payload.text).toEqual("test")
    expect(events[0].payload.reason).toEqual("spam")
    expect(isMore).toBeFalsy()

    await channel.delete()
  }, 20000)

  test("should create and get user via chat methods", async () => {
    const userId = generateRandomString()
    const userData = { name: "Test User", email: "test@example.com" }

    const createdUser = await chat.createUser(userId, userData)
    const fetchedUser = await chat.getUser(userId)

    expect(fetchedUser).toBeDefined()
    expect(fetchedUser?.id).toBe(userId)
    expect(fetchedUser?.name).toBe(userData.name)
    expect(fetchedUser?.email).toBe(userData.email)

    await createdUser.delete()
  }, 20000)

  test("should update user via chat.updateUser", async () => {
    const user = await createRandomUser(chat)
    const newName = "Updated Name"
    const updatedUser = await chat.updateUser(user.id, { name: newName })

    expect(updatedUser.id).toBe(user.id)
    expect(updatedUser.name).toBe(newName)

    await user.delete()
  }, 20000)

  test("should delete user via chat.deleteUser", async () => {
    const user = await createRandomUser(chat)
    const userId = user.id

    const result = await chat.deleteUser(userId)
    expect(result).toBe(true)
    const fetchedUser = await chat.getUser(userId)
    expect(fetchedUser).toBeNull()
  }, 20000)

  test("should get multiple users with filter", async () => {
    const user1 = await createRandomUser(chat)
    const user2 = await createRandomUser(chat)
    await sleep(200)

    const filter = `id == '${user1.id}' || id == '${user2.id}'`
    const result = await chat.getUsers({ filter })

    expect(result.users.length).toBeGreaterThanOrEqual(2)
    const userIds = result.users.map(u => u.id)
    expect(userIds).toContain(user1.id)
    expect(userIds).toContain(user2.id)

    await Promise.all([user1.delete(), user2.delete()])
  }, 20000)

  test("should get channel via chat.getChannel", async () => {
    const channel = await createRandomChannel(chat)
    const fetchedChannel = await chat.getChannel(channel.id)

    expect(fetchedChannel).toBeDefined()
    expect(fetchedChannel?.id).toBe(channel.id)
    expect(fetchedChannel?.name).toBe(channel.name)

    await channel.delete()
  }, 20000)

  test("should update channel via chat.updateChannel", async () => {
    const channel = await createRandomChannel(chat)
    const newName = "Updated Channel Name"
    const updatedChannel = await chat.updateChannel(channel.id, { name: newName })

    expect(updatedChannel.id).toBe(channel.id)
    expect(updatedChannel.name).toBe(newName)

    await channel.delete()
  }, 20000)

  test("should delete channel via chat.deleteChannel", async () => {
    const channel = await createRandomChannel(chat)
    const channelId = channel.id

    const result = await chat.deleteChannel(channelId)
    expect(result).toBe(true)
    const fetchedChannel = await chat.getChannel(channelId)
    expect(fetchedChannel).toBeNull()
  }, 20000)

  test("should get multiple channels with filter", async () => {
    const timestamp = Date.now()
    const channel1 = await chat.createChannel(`test-${timestamp}-1`, { name: "Test Channel 1" })
    const channel2 = await chat.createChannel(`test-${timestamp}-2`, { name: "Test Channel 2" })
    await sleep(200)

    const filter = `id == 'test-${timestamp}-1' || id == 'test-${timestamp}-2'`
    const result = await chat.getChannels({ filter })

    expect(result.channels.length).toBeGreaterThanOrEqual(2)
    const channelIds = result.channels.map(ch => ch.id)
    expect(channelIds).toContain(channel1.id)
    expect(channelIds).toContain(channel2.id)

    await Promise.all([channel1.delete(), channel2.delete()])
  }, 20000)

  test("should create public conversation", async () => {
    const channelId = generateRandomString(10)
    const channelData = { name: "Public Test Channel" }

    const publicChannel = await chat.createPublicConversation({
      channelId,
      channelData
    })

    expect(publicChannel).toBeDefined()
    expect(publicChannel.id).toBe(channelId)
    expect(publicChannel.name).toBe(channelData.name)

    await publicChannel.delete()
  }, 20000)

  test("should create direct conversation", async () => {
    const otherUser = await createRandomUser(chat)

    const { channel, hostMembership, inviteeMembership } =
      await chat.createDirectConversation({
        user: otherUser,
        channelData: { name: "Direct Chat" }
      })

    expect(channel).toBeDefined()
    expect(hostMembership.user.id).toBe(chat.currentUser.id)
    expect(inviteeMembership.user.id).toBe(otherUser.id)

    await channel.delete()
    await otherUser.delete()
  }, 20000)

  test("should create group conversation", async () => {
    const user1 = await createRandomUser(chat)
    const user2 = await createRandomUser(chat)

    const { channel, hostMembership, inviteesMemberships } =
      await chat.createGroupConversation({
        users: [user1, user2],
        channelData: { name: "Group Chat" }
      })

    expect(channel).toBeDefined()
    expect(hostMembership.user.id).toBe(chat.currentUser.id)
    expect(inviteesMemberships.length).toBe(2)

    await channel.delete()
    await Promise.all([user1.delete(), user2.delete()])
  }, 20000)

  test("should check presence via chat.isPresent", async () => {
    const channel = await createRandomChannel(chat)
    const disconnect = channel.connect(() => {})
    await sleep(2000)

    const isPresent = await chat.isPresent(chat.currentUser.id, channel.id)
    expect(isPresent).toBe(true)
    disconnect()
    await sleep(2000)

    const isPresentAfter = await chat.isPresent(chat.currentUser.id, channel.id)
    expect(isPresentAfter).toBe(false)

    await channel.delete()
  }, 25000)

  test("should get present users via chat.whoIsPresent", async () => {
    const channel = await createRandomChannel(chat)
    const disconnect = channel.connect(() => {})
    await sleep(2000)

    const presentUsers = await chat.whoIsPresent(channel.id)

    expect(Array.isArray(presentUsers)).toBe(true)
    expect(presentUsers).toContain(chat.currentUser.id)

    disconnect()
    await channel.delete()
  }, 20000)

  test("should get present channels via chat.wherePresent", async () => {
    const channel1 = await createRandomChannel(chat)
    const channel2 = await createRandomChannel(chat)

    const disconnect1 = channel1.connect(() => {})
    const disconnect2 = channel2.connect(() => {})
    await sleep(2000)

    const presentChannels = await chat.wherePresent(chat.currentUser.id)

    expect(Array.isArray(presentChannels)).toBe(true)
    expect(presentChannels).toContain(channel1.id)
    expect(presentChannels).toContain(channel2.id)

    disconnect1()
    disconnect2()

    await Promise.all([channel1.delete(), channel2.delete()])
  }, 25000)

  test("should get unread messages counts", async () => {
    const channel = await createRandomChannel(chat)
    await channel.invite(chat.currentUser)
    await channel.sendText("Test message")
    await sleep(500)

    const counts = await chat.getUnreadMessagesCounts()

    expect(Array.isArray(counts)).toBe(true)
    const channelCount = counts.find(c => c.channel.id === channel.id)
    const unreadCount = channelCount.count

    expect(channelCount).toBeDefined()
    expect(unreadCount).toEqual(1)

    await channel.delete()
  }, 20000)

  test("should fetch unread messages counts with pagination", async () => {
    const channel = await createRandomChannel(chat)
    await channel.invite(chat.currentUser)
    await channel.sendText("Test message")
    await sleep(500)

    const result = await chat.fetchUnreadMessagesCounts({ limit: 5 })
    expect(result.countsByChannel).toBeDefined()
    const countByChannel = result.countsByChannel.find(c => c.channel.id === channel.id)
    expect(countByChannel.count).toEqual(1)
    expect(Array.isArray(result.countsByChannel)).toBe(true)
    expect(result.page).toBeDefined()

    await channel.delete()
  }, 20000)

  test("should mark all messages as read", async () => {
    const channel = await createRandomChannel(chat)
    await channel.invite(chat.currentUser)
    await channel.sendText("Test message")
    await sleep(500)

    const result = await chat.markAllMessagesAsRead()
    await sleep(500)

    expect(result).toBeDefined()
    const counts = await chat.getUnreadMessagesCounts()
    expect(counts.length).toBe(0)

    await channel.delete()
  }, 20000)

  test("should set restrictions via chat.setRestrictions", async () => {
    const chatPamServer = await createChatInstance({
      userId: generateRandomString(),
      clientType: 'PamServer'
    })
    const testUser = await chatPamServer.createUser(generateRandomString(10), { name: "Test User" })
    const channel = await chatPamServer.createChannel(generateRandomString(10), { name: "Test Channel" })

    await chatPamServer.setRestrictions(testUser.id, channel.id, {
      ban: true,
      reason: "test ban"
    })

    await sleep(500)

    const restrictions = await channel.getUserRestrictions(testUser)
    expect(restrictions.ban).toBe(true)
    expect(restrictions.reason).toBe("test ban")

    await testUser.delete()
    await channel.delete()
    await chatPamServer.currentUser.delete()
  }, 25000)

  test("should get current user mentions", async () => {
    const channel = await createRandomChannel(chat)
    const messageDraft = channel.createMessageDraft()

    await messageDraft.onChange(`Hello @${chat.currentUser.name}`)
    messageDraft.addMentionedUser(chat.currentUser, 0)
    await messageDraft.send()
    await sleep(500)

    const result = await chat.getCurrentUserMentions({ count: 10 })

    expect(result).toBeDefined()
    expect(Array.isArray(result.enhancedMentionsData)).toBe(true)
    expect(result.enhancedMentionsData).toBeDefined()
    expect(result.enhancedMentionsData.find(m => m.userId == chat.currentUser.id)).toBeDefined()

    await channel.delete()
  }, 20000)

  test("should create and manage channel group", async () => {
    const groupId = `group_${generateRandomString()}`
    const channel1 = await createRandomChannel(chat)
    const channel2 = await createRandomChannel(chat)

    const channelGroup = chat.getChannelGroup(groupId)
    await channelGroup.addChannels([channel1, channel2])
    await sleep(500)

    const { channels } = await channelGroup.listChannels({ limit: 10 })
    expect(channels.length).toBeGreaterThanOrEqual(2)
    const channelIds = channels.map((c) => c.id)
    expect(channelIds).toContain(channel1.id)
    expect(channelIds).toContain(channel2.id)

    await chat.removeChannelGroup(groupId)
    await Promise.all([channel1.delete(), channel2.delete()])
  }, 20000)

  test("should mute and unmute users", async () => {
    const userToMute = await createRandomUser(chat)
    await chat.mutedUsersManager.muteUser(userToMute.id)
    await sleep(200)

    let mutedUsers = chat.mutedUsersManager.mutedUsers
    expect(mutedUsers.length).toBe(1)
    expect(mutedUsers).toContain(userToMute.id)

    await chat.mutedUsersManager.unmuteUser(userToMute.id)
    await sleep(200)

    mutedUsers = chat.mutedUsersManager.mutedUsers
    expect(mutedUsers.length).toEqual(0)
    expect(mutedUsers).not.toContain(userToMute.id)

    await userToMute.delete()
  }, 20000)

  test("should provide access to current user", () => {
    expect(chat.currentUser).toBeDefined()
    expect(chat.currentUser.id).toBe(chat.sdk.userId)
  }, 20000)

  test("should get channel suggestions", async () => {
    const prefix = "suggest-channel-"

    const existingChannels = await chat.getChannels({ filter: `id LIKE '${prefix}%'` })
    await Promise.all(existingChannels.channels.map(ch => ch.delete().catch(() => {})))
    await sleep(200)

    const channel1 = await createRandomChannel(chat, prefix)
    const channel2 = await createRandomChannel(chat, prefix)
    const channel3 = await createRandomChannel(chat, prefix)
    await sleep(500)

    const suggestions = await chat.getChannelSuggestions("#" + prefix)
    expect(Array.isArray(suggestions)).toBe(true)
    expect(suggestions.length).toBeGreaterThanOrEqual(3)

    const suggestionIds = suggestions.map(ch => ch.id)
    expect(suggestionIds).toContain(channel1.id)
    expect(suggestionIds).toContain(channel2.id)
    expect(suggestionIds).toContain(channel3.id)

    await Promise.all([channel1.delete(), channel2.delete(), channel3.delete()])
  }, 20000)

  test("should get user suggestions", async () => {
    const prefix = "suggest-user-"

    const existingUsers = await chat.getUsers({ filter: `id LIKE '${prefix}%'` })
    await Promise.all(existingUsers.users.map(u => u.delete().catch(() => {})))
    await sleep(200)

    const user1 = await createRandomUser(chat, prefix)
    const user2 = await createRandomUser(chat, prefix)
    const user3 = await createRandomUser(chat, prefix)
    await sleep(500)

    const suggestions = await chat.getUserSuggestions("@" + prefix)
    expect(Array.isArray(suggestions)).toBe(true)
    expect(suggestions.length).toBeGreaterThanOrEqual(3)

    const suggestionIds = suggestions.map(u => u.id)
    expect(suggestionIds).toContain(user1.id)
    expect(suggestionIds).toContain(user2.id)
    expect(suggestionIds).toContain(user3.id)

    await Promise.all([user1.delete(), user2.delete(), user3.delete()])
  }, 20000)
})
