import { Channel, Chat, MessageDraftV1 } from "../dist-test"
import {
  createChatInstance,
  generateRandomString,
  renderMessagePart,
  createRandomChannel,
  createRandomUser
} from "./utils"
import { jest } from "@jest/globals"

describe("MessageDraftV1 (deprecated)", function () {
  jest.retryTimes(2)

  let chat: Chat
  let channel: Channel
  let messageDraft: MessageDraftV1

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    channel = await createRandomChannel(chat)
    messageDraft = channel.createMessageDraftV1({ userSuggestionSource: "global" })
  })

  afterEach(async () => {
    await channel.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()

    jest.clearAllMocks()
  })

  test("should mention 2 users", async () => {
    const [user1, user2] = await Promise.all([createRandomUser(chat), createRandomUser(chat)])

    messageDraft.onChange("Hello @user1 and @user2")
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addMentionedUser(user2, 1)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(4)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("mention")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messageDraft.value).toBe(`Hello @${user1.name} and @${user2.name}`)
    expect(messagePreview.map(renderMessagePart).join("")).toBe(`Hello @${user1.name} and @${user2.name}`)

    await Promise.all([user1.delete({ soft: false }), user2.delete({ soft: false })])
  })

  test("should reference 2 channels", async () => {
    const [channel1, channel2] = await Promise.all([createRandomChannel(chat), createRandomChannel(chat)])

    messageDraft.onChange("Hello #channel1 and #channl2")
    messageDraft.addReferencedChannel(channel1, 0)
    messageDraft.addReferencedChannel(channel2, 1)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(4)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("channelReference")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("channelReference")
    expect(messagePreview.map(renderMessagePart).join("")).toBe(`Hello #${channel1.name} and #${channel2.name}`)
    expect(messageDraft.value).toBe(`Hello #${channel1.name} and #${channel2.name}`)

    await Promise.all([channel1.delete({ soft: false }), channel2.delete({ soft: false })])
  })

  test("should reference 2 channels and 2 mentions", async () => {
    const [channel1, channel2] = await Promise.all([createRandomChannel(chat), createRandomChannel(chat)])
    const [user1, user2] = await Promise.all([createRandomUser(chat), createRandomUser(chat)])

    messageDraft.onChange("Hello #channel1 and @brad and #channel2 or @jasmine.")
    messageDraft.addReferencedChannel(channel1, 0)
    messageDraft.addReferencedChannel(channel2, 1)
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addMentionedUser(user2, 1)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(9)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("channelReference")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[5].type).toBe("channelReference")
    expect(messagePreview[6].type).toBe("text")
    expect(messagePreview[7].type).toBe("mention")

    expect(messagePreview.map(renderMessagePart).join("")).toBe(
      `Hello #${channel1.name} and @${user1.name} and #${channel2.name} or @${user2.name}.`
    )
    expect(messageDraft.value).toBe(
      `Hello #${channel1.name} and @${user1.name} and #${channel2.name} or @${user2.name}.`
    )

    await Promise.all([channel1.delete({ soft: false }), channel2.delete({ soft: false })])
    await Promise.all([user1.delete({ soft: false }), user2.delete({ soft: false })])
  })

  test("should mix every type of message part", async () => {
    const testId = `js-chat-${Date.now()}`
    const channel1Id = `${testId}-channel-1`
    const channel2Id = `${testId}-channel-2`
    const user1Id = `${testId}-user-1`
    const user2Id = `${testId}-user-2`
    const user4Id = `${testId}-user-4`
    const user5Id = `${testId}-user-5`

    const [channel1, channel2] = await Promise.all([
      chat.createChannel(channel1Id, { name: "Test Channel 1" }),
      chat.createChannel(channel2Id, { name: "Test Channel 2" })
    ])
    const [user1, user2, user4, user5] = await Promise.all([
      chat.createUser(user1Id, { name: "Test User 1" }),
      chat.createUser(user2Id, { name: "Test User 2" }),
      chat.createUser(user4Id, { name: "Test User 3" }),
      chat.createUser(user5Id, { name: "Test User 4" }),
    ])

    messageDraft.onChange("Hello ")
    messageDraft.addLinkedText({
      text: "pubnub",
      link: "https://pubnub.com",
      positionInInput: messageDraft.value.length,
    })
    messageDraft.onChange("Hello pubnub at https://pubnub.com! Hello to ")
    messageDraft.addLinkedText({
      text: "google",
      link: "https://google.com",
      positionInInput: messageDraft.value.length,
    })
    messageDraft.onChange(
      "Hello pubnub at https://pubnub.com! Hello to google at https://google.com. Referencing #channel1, #channel2, #blankchannel, @user1, @user2, and mentioning @blankuser3 @user4 @user5"
    )

    messageDraft.addReferencedChannel(channel1, 0)
    messageDraft.addReferencedChannel(channel2, 1)
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addMentionedUser(user2, 1)
    messageDraft.addMentionedUser(user4, 3)
    messageDraft.addMentionedUser(user5, 4)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(20)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("textLink")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("plainLink")
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[5].type).toBe("textLink")
    expect(messagePreview[6].type).toBe("text")
    expect(messagePreview[7].type).toBe("plainLink")
    expect(messagePreview[8].type).toBe("text")
    expect(messagePreview[9].type).toBe("channelReference")
    expect(messagePreview[10].type).toBe("text")
    expect(messagePreview[11].type).toBe("channelReference")
    expect(messagePreview[12].type).toBe("text")
    expect(messagePreview[13].type).toBe("mention")
    expect(messagePreview[14].type).toBe("text")
    expect(messagePreview[15].type).toBe("mention")
    expect(messagePreview[16].type).toBe("text")
    expect(messagePreview[17].type).toBe("mention")
    expect(messagePreview[18].type).toBe("text")
    expect(messagePreview[19].type).toBe("mention")
    expect(messagePreview.map(renderMessagePart).join("")).toBe(
      `Hello pubnub at https://pubnub.com! Hello to google at https://google.com. Referencing #${channel1.name}, #${channel2.name}, #blankchannel, @${user1.name}, @${user2.name}, and mentioning @blankuser3 @${user4.name} @${user5.name}`
    )
    expect(messageDraft.value).toBe(
      `Hello pubnub at https://pubnub.com! Hello to google at https://google.com. Referencing #${channel1.name}, #${channel2.name}, #blankchannel, @${user1.name}, @${user2.name}, and mentioning @blankuser3 @${user4.name} @${user5.name}`
    )

    await Promise.all([
      channel1.delete({ soft: false }),
      channel2.delete({ soft: false })
    ])
    await Promise.all([
      user1.delete({ soft: false }),
      user2.delete({ soft: false }),
      user4.delete({ soft: false }),
      user5.delete({ soft: false }),
    ])
  }, 20000)
})
