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
} from "./utils"

import { jest } from "@jest/globals"

describe("Mute list test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let channel: Channel
  let messageDraft: MessageDraft

  beforeAll(async () => {
    chat = await createChatInstance()
  })

  beforeEach(async () => {
    channel = await createRandomChannel()
    messageDraft = channel.createMessageDraft()
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
