import { Chat, INTERNAL_MODERATION_PREFIX, User } from "../dist-test"
import { createChatInstance, createRandomUser, sleep } from "./utils"
import { INTERNAL_ADMIN_CHANNEL } from "../dist-test"

describe("User test", () => {
  let chat: Chat
  let user: User

  beforeAll(async () => {
    chat = await createChatInstance()
  })

  beforeEach(async () => {
    user = await createRandomUser()
  })

  test("Should automatically create chat user while initializing", () => {
    expect(chat.currentUser).toBeDefined()
    expect(chat.currentUser.id).toBe(chat.sdk.getUUID())
  })

  test("Should be able to create and fetch user", async () => {
    expect(user).toBeDefined()
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
    let updatedUser
    const name = "Updated User"
    const callback = jest.fn((user) => {
      return (updatedUser = user)
    })

    const stopUpdates = user.streamUpdates(callback)
    await user.update({ name })
    await sleep(150)

    expect(callback).toHaveBeenCalledWith(updatedUser)
    expect(updatedUser.name).toEqual(name)

    stopUpdates()
  })

  test("should update the user even if they're a member of a particular channel", async () => {
    let someUser = await chat.getUser("test-user-chatsdk0")
    if (!someUser) {
      someUser = await chat.createUser("test-user-chatsdk0", { name: "Chat SDK user 0" })
    }
    let someChannel = await chat.getChannel("some-public-channel")
    if (!someChannel) {
      someChannel = await chat.createPublicConversation({
        channelId: "some-public-channel",
        channelData: { name: "Public channel test" },
      })
    }
    await chat.sdk.objects.setChannelMembers({
      channel: someChannel.id,
      uuids: [someUser.id],
    })

    const stopUpdates = User.streamUpdatesOn([someUser], (updatedUsers) => {
      someUser = updatedUsers[0]
    })
    await someUser.update({ name: "update number 1" })
    await sleep(1000)
    expect(someUser.name).toBe("update number 1")
    await someUser.update({ name: "update number 2" })
    await sleep(1000)
    expect(someUser.name).toBe("update number 2")

    stopUpdates()
  })

  test("should update the user even if they're not a member of a particular channel", async () => {
    let someUser = await chat.getUser("test-user-chatsdk1")
    if (!someUser) {
      someUser = await chat.createUser("test-user-chatsdk1", { name: "Chat SDK user 1" })
    }
    let someChannel = await chat.getChannel("some-public-channel-2")
    if (!someChannel) {
      someChannel = await chat.createPublicConversation({
        channelId: "some-public-channel-2",
        channelData: { name: "Public channel test 2" },
      })
    }
    const { members } = await someChannel.getMembers()

    expect(members.length).toBe(0)
    const stopUpdates = User.streamUpdatesOn([someUser], (updatedUsers) => {
      someUser = updatedUsers[0]
    })
    await someUser.update({ name: "update number 1" })
    await sleep(1000)
    expect(someUser.name).toBe("update number 1")
    await someUser.update({ name: "update number 2" })
    await sleep(1000)
    expect(someUser.name).toBe("update number 2")

    stopUpdates()
  })

//   test("should report a user", async () => {
//     const reportReason = "Inappropriate behavior"
//     await user.DEPRECATED_report(reportReason)
//     await sleep(150) // history calls have around 130ms of cache time
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
    const numUsers = 5

    for (let i = 0; i < numUsers; i++) {
      const newUser = await createRandomUser()
      usersToCreate.push(newUser)
    }

    for (const createdUser of usersToCreate) {
      const fetchedUser = await chat.getUser(createdUser.id)

      expect(fetchedUser).toBeDefined()
      expect(fetchedUser.id).toBe(createdUser.id)
      expect(fetchedUser.name).toEqual(createdUser.name)
    }
  }, 15000)

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

    await chat.currentUser.getMemberships({ filter: "channel.id like 'hello*'" })
    expect(chat.sdk.objects.getMemberships).toHaveBeenCalledWith({
      ...commonParams,
      filter: `!(channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*') && (channel.id like 'hello*')`,
    })

    await chat.currentUser.getMemberships({ filter: "channel.name like '*test-channel'" })
    expect(chat.sdk.objects.getMemberships).toHaveBeenCalledWith({
      ...commonParams,
      filter: `!(channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*') && (channel.name like '*test-channel')`,
    })

    await chat.currentUser.getMemberships()
    expect(chat.sdk.objects.getMemberships).toHaveBeenCalledWith({
      ...commonParams,
      filter: `!(channel.id LIKE '${INTERNAL_MODERATION_PREFIX}*')`,
    })

    const exampleResponse = {
      prev: undefined,
      status: 200,
      totalCount: 307,
      next: "MTAw",
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
        {
          channel: { id: "019b58bd-3592-4184-8bc9-ce4a3ea87b37" },
          status: null,
          custom: null,
          updated: "2024-02-29T09:06:21.629495Z",
          eTag: "AZO/t53al7m8fw",
        },
        {
          channel: { id: "0336a32b-3568-42ec-8664-48f05f479928" },
          status: null,
          custom: null,
          updated: "2024-05-21T12:12:51.439348Z",
          eTag: "AZO/t53al7m8fw",
        },
      ],
    }

    jest.spyOn(chat.sdk.objects, "getMemberships").mockImplementation(() => exampleResponse)

    const response = await chat.currentUser.getMemberships()
    expect(response).toEqual(
      expect.objectContaining({
        page: expect.objectContaining({
          prev: exampleResponse.prev,
          next: exampleResponse.next,
        }),
        total: exampleResponse.totalCount,
        status: exampleResponse.status,
      })
    )
    expect(response.memberships.map((m) => m.channel.id)).toEqual(
      exampleResponse.data.map((m) => m.channel.id)
    )
  })

  test("should get multiple users via chat.getUsers", async () => {
    const user1 = await createRandomUser()
    const user2 = await createRandomUser()
    const user3 = await createRandomUser()

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
    const channel1 = await chat.createChannel(`test-channel-${Date.now()}-1`, {
      name: "Test Channel 1"
    })
    const channel2 = await chat.createChannel(`test-channel-${Date.now()}-2`, {
      name: "Test Channel 2"
    })

    const disconnect1 = channel1.connect(() => {})
    const disconnect2 = channel2.connect(() => {})

    await sleep(2000)

    const presentChannels = await chat.currentUser.wherePresent()

    expect(presentChannels.length).toBeGreaterThan(0)
    const channelIds = presentChannels.map(c => c.id)
    expect(channelIds).toContain(channel1.id)
    expect(channelIds).toContain(channel2.id)

    disconnect1()
    disconnect2()
    await Promise.all([channel1.delete(), channel2.delete()])
  })

  test("should get channels where user is present via chat.wherePresent", async () => {
    const channel = await chat.createChannel(`test-channel-${Date.now()}`, {
      name: "Test Channel"
    })

    const disconnect = channel.connect(() => {})
    await sleep(2000)
    const presentChannels = await chat.wherePresent(chat.currentUser.id)

    expect(presentChannels.length).toBeGreaterThan(0)
    const channelIds = presentChannels.map(c => c.id)
    expect(channelIds).toContain(channel.id)

    disconnect()
    await channel.delete()
  })

  test("should check if user is active via user.active property", async () => {
    const channel = await chat.createChannel(`test-channel-${Date.now()}`, {
      name: "Test Channel"
    })

    const disconnect = channel.connect(() => {})
    await sleep(2000)
    const isActive = await chat.currentUser.active()

    expect(typeof isActive).toBe("boolean")
    expect(isActive).toBe(true)
    disconnect()

    await channel.delete()
  })

  test("should get last active timestamp via user.lastActiveTimestamp property", async () => {
    const channel = await chat.createChannel(`test-channel-${Date.now()}`, {
      name: "Test Channel"
    })

    const disconnect = channel.connect(() => {})
    await sleep(2000)
    const timestamp = await chat.currentUser.lastActiveTimestamp()

    expect(timestamp).toBeDefined()
    expect(typeof timestamp).toBe("string")
    disconnect()

    await channel.delete()
  })

  test("should set restrictions on user via user.setRestrictions", async () => {
    const channel = await chat.createChannel(`test-channel-${Date.now()}`, {
      name: "Test Channel"
    })

    const testUser = await createRandomUser()
    await testUser.setRestrictions(channel.id, { ban: true })
    await sleep(150)

    const restrictions = await channel.getUserRestrictions(testUser)
    expect(restrictions.ban).toBe(true)

    await Promise.all([testUser.delete(), channel.delete()])
  })

  test("should get channel restrictions for user via user.getChannelRestrictions", async () => {
    const channel = await chat.createChannel(`test-channel-${Date.now()}`, {
      name: "Test Channel"
    })

    const testUser = await createRandomUser()
    await chat.setRestrictions(testUser.id, channel.id, { mute: true })
    await sleep(150)

    const restrictions = await testUser.getChannelRestrictions(channel.id)
    expect(restrictions.mute).toBe(true)

    await Promise.all([testUser.delete(), channel.delete()])
  })
})
