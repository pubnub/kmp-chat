import { Chat, User } from "../dist-test"
import {createChatInstance, generateRandomString, sleep, createRandomUser, createRandomChannel} from "./utils"
import {jest} from "@jest/globals";

describe("User test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let user: User

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    user = await createRandomUser(chat)
  })

  afterEach(async () => {
    await user.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()

    jest.clearAllMocks()
  })

  test("Should automatically create chat user while initializing", () => {
    expect(chat.currentUser).toBeDefined()
    expect(chat.currentUser.id).toBe(chat.sdk.getUserId())
  })

  test("Should be able to create and fetch user", async () => {
    const fetchedUser = await chat.getUser(user.id)
    expect(fetchedUser).toBeDefined()
    expect(fetchedUser.name).toEqual(user.name)
  })

  test("Should be able to update user", async () => {
    const name = "Updated User"
    const updatedUser = await user.update({ name })
    expect(updatedUser.id).toBe(user.id)
    expect(updatedUser.name).toEqual(name)
  })

  test("Should be able to delete user", async () => {
    const deleteOptions = { soft: true }
    const { status } = (await user.delete(deleteOptions)) as User
    expect(status).toBe("deleted")

    const deleteResult = await user.delete()
    expect(deleteResult).toBe(true)
    const fetchedUser = await chat.getUser(user.id)
    expect(fetchedUser).toBeNull()
  })

  test("Should stream user updates and invoke the callback", async () => {
    let updatedUser: User | undefined
    let callbackCount = 0

    const name = "Updated User"

    const callback = (user: User) => {
      updatedUser = user
      callbackCount++
    }

    const stopUpdates = user.streamUpdates(callback)
    await user.update({ name })
    await sleep(150)

    expect(callbackCount).toBe(1)
    expect(updatedUser).toBeDefined()
    expect(updatedUser!.name).toEqual(name)
    expect(updatedUser!.id).toEqual(user.id)

    stopUpdates()
  }, 20000)

  test("should update the user even if they're a member of a particular channel", async () => {
    const someUser = await createRandomUser(chat)
    let capturedUser: User | undefined

    const someChannel = await chat.createPublicConversation({
      channelId: generateRandomString(),
      channelData: { name: "Public channel test" },
    })

    await chat.sdk.objects.setChannelMembers({
      channel: someChannel.id,
      uuids: [someUser.id],
    })

    const stopUpdates = User.streamUpdatesOn([someUser], (updatedUsers) => {
      capturedUser = updatedUsers[0]
    })

    await someUser.update({ name: "update number 1" })
    await sleep(1000)
    expect(capturedUser?.name).toBe("update number 1")

    await someUser.update({ name: "update number 2" })
    await sleep(1000)
    expect(capturedUser?.name).toBe("update number 2")

    stopUpdates()

    await someUser.delete()
    await someChannel.delete()
  }, 20000)

  test("should update the user even if they're not a member of a particular channel", async () => {
    const someUser = await createRandomUser(chat)
    let capturedUser: User | undefined

    const someChannel = await chat.createPublicConversation({
      channelId: generateRandomString(),
      channelData: { name: "Public channel test 2" },
    })

    const { members } = await someChannel.getMembers()
    expect(members.length).toBe(0)

    const stopUpdates = User.streamUpdatesOn([someUser], (updatedUsers) => {
      capturedUser = updatedUsers[0]
    })

    await someUser.update({ name: "update number 1" })
    await sleep(1000)
    expect(capturedUser?.name).toBe("update number 1")

    await someUser.update({ name: "update number 2" })
    await sleep(1000)
    expect(capturedUser?.name).toBe("update number 2")

    stopUpdates()

    await someUser.delete()
    await someChannel.delete()
  }, 20000)

//   test("should report a user", async () => {
//     const reportReason = "Inappropriate behavior"
//     await user.DEPRECATED_report(reportReason)
//     await sleep(150) // History calls have around 130ms of cache time
//
//     const adminChannel =
//       (await chat.getChannel(INTERNAL_ADMIN_CHANNEL)) ||
//       (await chat.createChannel(INTERNAL_ADMIN_CHANNEL, { name: "admin channel" }))
//     expect(adminChannel).toBeDefined()
//
//     const adminChannelHistory = await adminChannel.getHistory({ count: 1 })
//     const reportMessage = adminChannelHistory.messages[0]
//
//     expect(reportMessage?.content.type).toBe("report")
//     expect(reportMessage?.content.reportedUserId).toBe(user.id)
//     expect(reportMessage?.content.reason).toBe(reportReason)
//   })

  test("Should be able to create, fetch, and validate multiple users", async () => {
    const usersToCreate = []
    const numUsers = 3

    for (let i = 0; i < numUsers; i++) {
      const newUser = await createRandomUser(chat)
      usersToCreate.push(newUser)
    }

    for (const createdUser of usersToCreate) {
      const fetchedUser = await chat.getUser(createdUser.id)
      expect(fetchedUser).toBeDefined()
      expect(fetchedUser.id).toBe(createdUser.id)
      expect(fetchedUser.name).toEqual(createdUser.name)
    }

    for (const createdUser of usersToCreate) {
      await createdUser.delete()
    }
  }, 25000)

  test("Should fail to update a non-existent user", async () => {
    const nonExistentUserId = "nonexistentuserid"

    try {
      const nonExistentUser = await chat.getUser(nonExistentUserId)
      await nonExistentUser.update({})
      fail("Updating a non-existent user should fail")
    } catch (error) {
      expect(error.message).toContain("Cannot read properties of null (reading 'update')")
    }
  })

  test("Should fail to delete a non-existent user", async () => {
    const nonExistentUserId = "nonexistentuserid"

    try {
      const nonExistentUser = await chat.getUser(nonExistentUserId)
      await nonExistentUser.delete()
      fail("Deleting a non-existent user should fail")
    } catch (error) {
      expect(error.message).toContain("Cannot read properties of null (reading 'delete')")
    }
  })

  test("Should apply filter to 'getMemberships'", async () => {
    const timestamp = Date.now()
    const helloChannel1 = await chat.createChannel(`hello-channel-${timestamp}-1`, { name: "Hello Channel 1" })
    const helloChannel2 = await chat.createChannel(`hello-channel-${timestamp}-2`, { name: "Hello Channel 2" })
    const testChannel = await chat.createChannel(`other-channel-${timestamp}`, { name: "Filter Test Channel" })

    await helloChannel1.join(() => {})
    await helloChannel2.join(() => {})
    await testChannel.join(() => {})
    await sleep(500)

    const helloFilter = `channel.name LIKE 'hello*'`
    const helloResult = await chat.currentUser.getMemberships({ filter: helloFilter })
    const helloChannelNames = helloResult.memberships.map(m => m.channel.name)

    expect(helloChannelNames).toContain(helloChannel1.name)
    expect(helloChannelNames).toContain(helloChannel2.name)
    expect(helloChannelNames).not.toContain(testChannel.name)

    await helloChannel1.leave()
    await helloChannel2.leave()
    await testChannel.leave()

    await Promise.all([
      helloChannel1.delete(),
      helloChannel2.delete(),
      testChannel.delete()
    ])
  }, 30000)

  test("should get multiple users via chat.getUsers", async () => {
    const user1 = await createRandomUser(chat)
    const user2 = await createRandomUser(chat)
    const user3 = await createRandomUser(chat)

    const filter = `id == '${user1.id}' || id == '${user2.id}' || id == '${user3.id}'`
    const result = await chat.getUsers({ filter })

    expect(result.users.length).toBeGreaterThanOrEqual(3)
    const userIds = result.users.map(u => u.id)
    expect(userIds).toContain(user1.id)
    expect(userIds).toContain(user2.id)
    expect(userIds).toContain(user3.id)

    await Promise.all([user1.delete(), user2.delete(), user3.delete()])
  })

  test("should update user via chat.updateUser", async () => {
    const newName = "Updated via wrapper"
    const newProfileUrl = "https://example.com/profile.jpg"

    const updatedUser = await chat.updateUser(user.id, {
      name: newName,
      profileUrl: newProfileUrl
    })

    expect(updatedUser.id).toBe(user.id)
    expect(updatedUser.name).toEqual(newName)
    expect(updatedUser.profileUrl).toEqual(newProfileUrl)
  })

  test("should get channels where user is present via user.wherePresent", async () => {
    const channel1 = await chat.createChannel(`test-channel-${Date.now()}-1`, { name: "Test Channel 1" })
    const channel2 = await chat.createChannel(`test-channel-${Date.now()}-2`, { name: "Test Channel 2" })

    const disconnect1 = channel1.connect(() => {})
    const disconnect2 = channel2.connect(() => {})
    await sleep(4000)

    const presentChannels = await chat.currentUser.wherePresent()
    expect(presentChannels.length).toBeGreaterThan(0)
    expect(presentChannels).toContain(channel1.id)
    expect(presentChannels).toContain(channel2.id)

    disconnect1()
    disconnect2()

    await Promise.all([channel1.delete(), channel2.delete()])
  }, 20000)

  test("should get last active timestamp via user.lastActiveTimestamp property", async () => {
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        storeUserActivityTimestamps: true,
        storeUserActivityInterval: 600000,
      }
    })

    const channel = await chat.createChannel(`test-channel-${Date.now()}`, { name: "Test Channel" })
    const disconnect = channel.connect(() => {})
    await sleep(5000)

    const timestamp = chat.currentUser.lastActiveTimestamp
    expect(timestamp).toBeDefined()
    expect(timestamp).toBeGreaterThan(0)

    disconnect()

    await channel.delete()
    await chat.currentUser.delete()
  }, 20000)

  test("should get active status via user.active property", async () => {
    const activityInterval = 60000
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        storeUserActivityTimestamps: true,
        storeUserActivityInterval: activityInterval
      }
    })

    const channel = await chat.createChannel(`test-channel-${Date.now()}`, { name: "Test Channel" })
    const disconnect = channel.connect(() => {})
    await sleep(1000)

    const isActiveWhileConnected = chat.currentUser.active
    expect(isActiveWhileConnected).toBe(true)

    disconnect()

    await channel.delete()
    await chat.currentUser.delete()
  }, 15000)

  test("should set restrictions on user via user.setRestrictions", async () => {
    const chatPamServer = await createChatInstance( { userId: generateRandomString(), clientType: 'PamServer' })
    const testUser = await chatPamServer.createUser(generateRandomString(), { name: "Test User" })
    const channel = await chatPamServer.createChannel(generateRandomString(), { name: "Test Channel" })

    await testUser.setRestrictions(channel, { ban: true, mute: true, reason: "Violated community guidelines" })
    await sleep(350)

    const restrictions = await channel.getUserRestrictions(testUser)
    expect(restrictions.ban).toBe(true)
    expect(restrictions.mute).toBe(true)
    expect(restrictions.reason).toBe("Violated community guidelines")

    await Promise.all([testUser.delete(), channel.delete(), chatPamServer.currentUser.delete()])
  }, 20000)

  test("should get channel restrictions for user via user.getChannelRestrictions", async () => {
    const chatPamServer = await createChatInstance({ userId: generateRandomString(), clientType: 'PamServer' })
    const testUser = await chatPamServer.createUser(generateRandomString(), { name: "Test User" })
    const channel = await chatPamServer.createChannel(generateRandomString(), { name: "Test Channel" })

    await chatPamServer.setRestrictions(testUser.id, channel.id, { mute: true })
    await sleep(350)

    const restrictions = await testUser.getChannelRestrictions(channel.id)
    expect(restrictions.mute).toBe(true)

    await Promise.all([testUser.delete(), channel.delete(), chatPamServer.currentUser.delete()])
  }, 20000)

  test("should check if user is present on specific channel via user.isPresentOn", async () => {
    const channel = await chat.createChannel(`test-channel-${Date.now()}`, { name: "Test Channel" })
    const disconnect = channel.connect(() => {})
    await sleep(2000)

    const isPresent = await chat.currentUser.isPresentOn(channel.id)
    expect(isPresent).toBe(true)

    disconnect()
    await sleep(2000)

    const isPresentAfterDisconnect = await chat.currentUser.isPresentOn(channel.id)
    expect(isPresentAfterDisconnect).toBe(false)

    await channel.delete()
  })

  test("should create user with all fields", async () => {
    const userId = `user_${Date.now()}`
    const userData = {
      name: "Test User Full",
      externalId: "ext-123",
      profileUrl: "https://example.com/avatar.jpg",
      email: "test@example.com",
      custom: {
        role: "admin",
        department: "engineering"
      },
      status: "active",
      type: "premium"
    }

    let createdUser: User | undefined

    try {
      const createdUser = await chat.createUser(userId, userData)
      expect(createdUser.id).toBe(userId)
      expect(createdUser.name).toBe(userData.name)
      expect(createdUser.externalId).toBe(userData.externalId)
      expect(createdUser.profileUrl).toBe(userData.profileUrl)
      expect(createdUser.email).toBe(userData.email)
      expect(createdUser.custom).toEqual(userData.custom)
      expect(createdUser.status).toBe(userData.status)
      expect(createdUser.type).toBe(userData.type)
      expect(createdUser.updated).toBeDefined()
    } finally {
        if (createdUser) {
          await createdUser.delete().catch(() => {})
        }
    }
  })

  test("should update user with all optional fields", async () => {
    const updateData = {
      name: "Updated Full User",
      externalId: "ext-456",
      profileUrl: "https://example.com/new-avatar.jpg",
      email: "updated@example.com",
      custom: {
        role: "moderator",
        level: 5
      },
      status: "busy",
      type: "standard"
    }

    const updatedUser = await user.update(updateData)

    expect(updatedUser.id).toBe(user.id)
    expect(updatedUser.name).toBe(updateData.name)
    expect(updatedUser.externalId).toBe(updateData.externalId)
    expect(updatedUser.profileUrl).toBe(updateData.profileUrl)
    expect(updatedUser.email).toBe(updateData.email)
    expect(updatedUser.custom).toEqual(updateData.custom)
    expect(updatedUser.status).toBe(updateData.status)
    expect(updatedUser.type).toBe(updateData.type)
    expect(updatedUser.updated).toBeDefined()

    await updatedUser.delete()
  })

  test("should soft delete user", async () => {
    const testUser = await createRandomUser(chat)
    const softDeleteResult = await testUser.delete({ soft: true })
    expect(softDeleteResult).toBeDefined()
    expect((softDeleteResult as User).status).toBe("deleted")
  })

  test("should check if user is a member of channel via user.isMemberOf", async () => {
    const testChannel = await createRandomChannel(chat)

    await testChannel.invite(user)
    await sleep(200)

    const isMember = await user.isMemberOf(testChannel.id)
    expect(isMember).toBe(true)

    const otherChannel = await createRandomChannel(chat)
    const isNotMember = await user.isMemberOf(otherChannel.id)
    expect(isNotMember).toBe(false)

    await Promise.all([testChannel.delete(), otherChannel.delete()])
  }, 20000)

  test("should get membership for channel via user.getMembership", async () => {
    const testChannel = await createRandomChannel(chat)

    await testChannel.invite(user)
    await sleep(200)

    const membership = await user.getMembership(testChannel.id)
    expect(membership).toBeDefined()
    expect(membership?.user.id).toBe(user.id)
    expect(membership?.channel.id).toBe(testChannel.id)

    const otherChannel = await createRandomChannel(chat)
    const noMembership = await user.getMembership(otherChannel.id)
    expect(noMembership).toBeNull()

    await Promise.all([testChannel.delete(), otherChannel.delete()])
  }, 20000)
})
