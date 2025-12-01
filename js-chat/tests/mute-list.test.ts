import {
  Channel,
  Chat
} from "../dist-test"
import {
  createChatInstance,
  generateRandomString,
  createRandomChannel,
  createRandomUser
} from "./utils"

import { jest } from "@jest/globals"

describe("Mute list test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let channel: Channel

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    channel = await createRandomChannel(chat)
  })

  afterEach(async () => {
    await channel.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()

    jest.clearAllMocks()
  })

  test("should add user to mute set", async () => {
     await chat.mutedUsersManager.muteUser("abc")
     expect(chat.mutedUsersManager.mutedUsers[0]).toBe("abc")
  })

  test("should remove user from mute set", async () => {
    await chat.mutedUsersManager.muteUser("abc")
    await chat.mutedUsersManager.muteUser("def")
    await chat.mutedUsersManager.unmuteUser("abc")
    expect(chat.mutedUsersManager.mutedUsers[0]).toBe("def")
    expect(chat.mutedUsersManager.mutedUsers.length).toBe(1)
  })
})
