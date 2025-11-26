import {
  Channel,
  Chat
} from "../dist-test"
import {
  createChatInstance,
  makeid
} from "./utils"

import { jest } from "@jest/globals"

describe("Mute list test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let channel: Channel

  function createRandomChannel(prefix: string = "") {
    return chat.createChannel(`${prefix}channel_${makeid()}`, {
      name: `${prefix}Test Channel`,
      description: "This is a test channel",
    })
  }

  function createRandomUser(prefix: string = "") {
    return chat.createUser(`${prefix}user_${makeid()}`, {
      name: `${prefix}Test User`,
    })
  }

  beforeAll(async () => {
    chat = await createChatInstance({ shouldCreateNewInstance: true, userId: makeid() })
  })

  beforeEach(async () => {
    channel = await createRandomChannel()
  })

  afterEach(async () => {
    await channel.delete()
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
