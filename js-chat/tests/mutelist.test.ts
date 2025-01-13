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
     await chat.mutedUsers.muteUser("abc")
     expect(chat.mutedUsers.muteSet[0]).toBe("abc")
  })

    test("should remove user from mute set", async () => {
       await chat.mutedUsers.muteUser("abc")
       await chat.mutedUsers.muteUser("def")
       await chat.mutedUsers.unmuteUser("abc")
       expect(chat.mutedUsers.muteSet[0]).toBe("def")
       expect(chat.mutedUsers.muteSet.length).toBe(1)
    })

})
