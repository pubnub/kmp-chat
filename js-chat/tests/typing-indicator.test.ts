import { Channel, Chat } from "../dist-test"
import { createChatInstance, generateRandomString, sleep, createRandomChannel } from "./utils"

describe("Typing indicator test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let chat2: Chat

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    chat2 = await createChatInstance({ userId: generateRandomString() })
  })

  afterEach(async () => {
    await chat.currentUser.delete()
    await chat.sdk.disconnect()
    await chat2.currentUser.delete()
    await chat2.sdk.disconnect()

    jest.clearAllMocks()
  })

  test("should call the callback with the typing value when a typing signal is received", async () => {
    const channel = await createRandomChannel(chat)
    const membership = await channel.invite(chat2.currentUser)
    await sleep(500)

    const channel2 = await chat2.getChannel(channel.id)
    const callback = jest.fn()

    const unsubscribe = await channel.getTyping(callback)
    await sleep(500)

    await channel2.startTyping()
    await sleep(2000)

    expect(callback).toHaveBeenCalledWith([chat2.currentUser.id])

    unsubscribe()
    await channel.delete()
  }, 20000)

  test("should call the callback via onTypingChanged when a typing signal is received", async () => {
    const channel = await createRandomChannel(chat)
    const membership = await channel.invite(chat2.currentUser)
    await sleep(500)

    const channel2 = await chat2.getChannel(channel.id)
    const callback = jest.fn()

    const unsubscribe = await channel.onTypingChanged(callback)
    await sleep(500)

    await channel2.startTyping()
    await sleep(2000)

    expect(callback).toHaveBeenCalledWith([chat2.currentUser.id])

    unsubscribe()
    await channel.delete()
  }, 20000)

  // Needs to be clarified. Task created CSK-285
  test.skip("should not call the callback when the typing signal is from the same user as the recipient", async () => {
    const chat1 = await createChatInstance({
      userId: "testing",
    })

    const channelId = generateRandomString()
    const channelData = {
      name: "Typing Test Group Channel for Same User",
      description: "This is a test group channel for typing by the same user.",
    }

    const membershipData = {
      custom: {
        role: "member",
      },
    }

    const result = await chat.createGroupConversation({
      users: [chat1.currentUser],
      channelId,
      channelData,
      membershipData,
    })

    const { channel } = result
    const callback = jest.fn()

    const channelFromUser1 = await chat1.getChannel(channelId)
    const unsubscribe = channelFromUser1.getTyping(callback)

    await channelFromUser1.startTyping()
    await sleep(1000)

    expect(callback).not.toHaveBeenCalled()

    await channelFromUser1.stopTyping()
    await sleep(2000)

    unsubscribe()
    await channel.delete()
  })

  test("should handle multiple users starting and stopping typing", async () => {
    const chat3 = await createChatInstance({
      userId: generateRandomString(),
    })
    const chat4 = await createChatInstance({
      userId: generateRandomString(),
    })

    const channelId = `group_channel_typing_test_${generateRandomString()}`
    const channelData = {
      name: "Typing Test Group Channel",
      description: "This is a test group channel for typing.",
    }

    const membershipData = {
      custom: {
        role: "member",
      },
    }

    const result = await chat.createGroupConversation({
      users: [chat3.currentUser, chat4.currentUser],
      channelId,
      channelData,
      membershipData,
    })

    const { channel } = result

    const callback = jest.fn()
    const channelFromUser3 = await chat3.getChannel(channelId)
    const channelFromUser4 = await chat4.getChannel(channelId)

    const unsubscribe = channel.getTyping(callback)
    await sleep(500)

    await channel.startTyping()
    await channelFromUser3.startTyping()
    await channelFromUser4.startTyping()
    await sleep(2000)

    expect(callback).toHaveBeenCalledWith(
      expect.arrayContaining([chat.currentUser.id, chat3.currentUser.id, chat4.currentUser.id])
    )

    await channel.stopTyping()
    await channelFromUser3.stopTyping()
    await channelFromUser4.stopTyping()
    await sleep(2000)

    expect(callback).toHaveBeenCalledWith([])

    unsubscribe()
    await chat3.currentUser.delete()
    await chat4.currentUser.delete()
    await channel.delete()
  }, 35000)

  test("should properly handle typing and stopping typing", async () => {
    const channel = await createRandomChannel(chat)
    const membership = await channel.invite(chat2.currentUser)
    await sleep(500)

    const channel2 = await chat2.getChannel(channel.id)
    const callback = jest.fn()

    const unsubscribe = await channel.getTyping(callback)
    await sleep(500)

    await channel2.startTyping()
    await sleep(2000)

    expect(callback).toHaveBeenCalledWith([chat2.currentUser.id])

    await channel2.stopTyping()
    await sleep(2000)

    expect(callback).toHaveBeenCalledWith([])

    unsubscribe()
    await channel.delete()
  }, 25000)
})
