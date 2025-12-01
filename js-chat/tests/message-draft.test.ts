import { Channel, Chat } from "../dist-test"
import {
  createChatInstance,
  generateRandomString,
  renderMessagePart,
  createRandomChannel,
  createRandomUser
} from "./utils"
import { jest } from "@jest/globals"

describe("MessageDraft", function () {
  jest.retryTimes(2)

  let chat: Chat
  let channel: Channel
  let messageDraft

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    channel = await createRandomChannel(chat)
    messageDraft = channel.createMessageDraft({ userSuggestionSource: "global" })
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

  test("should mention 2 - 3 users next to each other", async () => {
    const [user1, user2, user3] = await Promise.all([
      createRandomUser(chat),
      createRandomUser(chat),
      createRandomUser(chat),
    ])

    messageDraft.onChange("Hello @user1 @user2 @user3")
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addMentionedUser(user2, 1)
    messageDraft.addMentionedUser(user3, 2)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(6)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("mention")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[5].type).toBe("mention")
    expect(messagePreview.map(renderMessagePart).join("")).toBe(`Hello @${user1.name} @${user2.name} @${user3.name}`)
    expect(messageDraft.value).toBe(`Hello @${user1.name} @${user2.name} @${user3.name}`)

    await Promise.all([
      user1.delete({ soft: false }),
      user2.delete({ soft: false }),
      user3.delete({ soft: false }),
    ])
  })

  test("should mention 2 - 3 users with words between second and third", async () => {
    const [user1, user2, user3] = await Promise.all([
      createRandomUser(chat),
      createRandomUser(chat),
      createRandomUser(chat),
    ])

    messageDraft.onChange("Hello @user1 @user2 and @user3")
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addMentionedUser(user2, 1)
    messageDraft.addMentionedUser(user3, 2)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messageDraft.value).toBe(`Hello @${user1.name} @${user2.name} and @${user3.name}`)
    expect(messagePreview.length).toBe(6)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("mention")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[4].content.text).toBe(" and ")
    expect(messagePreview[5].type).toBe("mention")
    expect(messagePreview.map(renderMessagePart).join("")).toBe(`Hello @${user1.name} @${user2.name} and @${user3.name}`)
    expect(messageDraft.value).toBe(`Hello @${user1.name} @${user2.name} and @${user3.name}`)

    await Promise.all([
      user1.delete({ soft: false }),
      user2.delete({ soft: false }),
      user3.delete({ soft: false }),
    ])
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

  test("should reference 2 channels and 2 mentions with commas", async () => {
    const [channel1, channel2] = await Promise.all([createRandomChannel(chat), createRandomChannel(chat)])
    const [user1, user2] = await Promise.all([createRandomUser(chat), createRandomUser(chat)])

    messageDraft.onChange("Hello #channel1, @brad, #channel2 or @jasmine")
    messageDraft.addReferencedChannel(channel1, 0)
    messageDraft.addReferencedChannel(channel2, 1)
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addMentionedUser(user2, 1)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(8)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("channelReference")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[5].type).toBe("channelReference")
    expect(messagePreview[6].type).toBe("text")
    expect(messagePreview[7].type).toBe("mention")

    expect(messagePreview.map(renderMessagePart).join("")).toBe(
      `Hello #${channel1.name}, @${user1.name}, #${channel2.name} or @${user2.name}`
    )
    expect(messageDraft.value).toBe(
      `Hello #${channel1.name}, @${user1.name}, #${channel2.name} or @${user2.name}`
    )

    await Promise.all([channel1.delete({ soft: false }), channel2.delete({ soft: false })])
    await Promise.all([user1.delete({ soft: false }), user2.delete({ soft: false })])
  })

  test("should reference 2 channels and 2 mentions with commas - another variation", async () => {
    const [channel1, channel2, channel3] = await Promise.all([
      createRandomChannel(chat),
      createRandomChannel(chat),
      createRandomChannel(chat),
    ])
    const [user1, user2] = await Promise.all([createRandomUser(chat), createRandomUser(chat)])

    messageDraft.onChange("Hello #channel1, @brad, #channel2, #some-random-channel, @jasmine")
    messageDraft.addReferencedChannel(channel1, 0)
    messageDraft.addReferencedChannel(channel2, 1)
    messageDraft.addReferencedChannel(channel2, 2)
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addMentionedUser(user2, 1)
    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(10)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("channelReference")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[5].type).toBe("channelReference")
    expect(messagePreview[6].type).toBe("text")
    expect(messagePreview[7].type).toBe("channelReference")
    expect(messagePreview[8].type).toBe("text")
    expect(messagePreview[9].type).toBe("mention")

    expect(messagePreview.map(renderMessagePart).join("")).toBe(
      `Hello #${channel1.name}, @${user1.name}, #${channel2.name}, #${channel3.name}, @${user2.name}`
    )
    expect(messageDraft.value).toBe(
      `Hello #${channel1.name}, @${user1.name}, #${channel2.name}, #${channel3.name}, @${user2.name}`
    )

    await Promise.all([
      channel1.delete({ soft: false }),
      channel2.delete({ soft: false }),
      channel3.delete({ soft: false }),
    ])
    await Promise.all([
      user1.delete({ soft: false }),
      user2.delete({ soft: false })
    ])
  })

  test("should add 2 text links and 2 plain links", async () => {
    messageDraft.onChange("Hello https://pubnub.com, https://google.com and ")
    messageDraft.addLinkedText({
      text: "pubnub",
      link: "https://pubnub.com",
      positionInInput: messageDraft.value.length,
    })
    messageDraft.onChange("Hello https://pubnub.com, https://google.com and pubnub, ")
    messageDraft.addLinkedText({
      text: "google",
      link: "https://google.com",
      positionInInput: messageDraft.value.length,
    })
    messageDraft.onChange("Hello https://pubnub.com, https://google.com and pubnub, google.")

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(9)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("plainLink")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("plainLink")
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[5].type).toBe("textLink")
    expect(messagePreview[6].type).toBe("text")
    expect(messagePreview[7].type).toBe("textLink")
    expect(messagePreview[8].type).toBe("text")

    expect(messagePreview.map(renderMessagePart).join("")).toBe(
      "Hello https://pubnub.com, https://google.com and pubnub, google."
    )
    expect(messageDraft.value).toBe(
      "Hello https://pubnub.com, https://google.com and pubnub, google."
    )
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

  test("should mix every type of message part - variant 2", async () => {
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

    messageDraft.onChange("Hello @user1 #channel1 ")
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addReferencedChannel(channel1, 0)
    messageDraft.onChange(`${messageDraft.value} `)
    messageDraft.addLinkedText({
      text: "pubnub",
      link: "https://pubnub.com",
      positionInInput: messageDraft.value.length,
    })
    messageDraft.onChange(`${messageDraft.value} at https://pubnub.com. `)
    messageDraft.addLinkedText({
      text: "google",
      link: "https://google.com",
      positionInInput: messageDraft.value.length,
    })
    messageDraft.onChange(
      `${messageDraft.value} at https://google.com, @user2 @blankuser3 #channel2, random text @user4, @user5.`
    )
    messageDraft.addReferencedChannel(channel2, 1)
    messageDraft.addMentionedUser(user2, 1)
    messageDraft.addMentionedUser(user4, 3)
    messageDraft.addMentionedUser(user5, 4)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(21)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("mention")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("channelReference")
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[5].type).toBe("textLink")
    expect(messagePreview[6].type).toBe("text")
    expect(messagePreview[7].type).toBe("plainLink")
    expect(messagePreview[8].type).toBe("text")
    expect(messagePreview[9].type).toBe("textLink")
    expect(messagePreview[10].type).toBe("text")
    expect(messagePreview[11].type).toBe("plainLink")
    expect(messagePreview[12].type).toBe("text")
    expect(messagePreview[13].type).toBe("mention")
    expect(messagePreview[14].type).toBe("text")
    expect(messagePreview[15].type).toBe("channelReference")
    expect(messagePreview[16].type).toBe("text")
    expect(messagePreview[17].type).toBe("mention")
    expect(messagePreview[18].type).toBe("text")
    expect(messagePreview[19].type).toBe("mention")

    expect(messagePreview.map(renderMessagePart).join("")).toBe(
      `Hello @Test User 1 #Test Channel 1 pubnub at https://pubnub.com. google at https://google.com, @Test User 2 @blankuser3 #Test Channel 2, random text @Test User 3, @Test User 4.`
    )
    expect(messageDraft.value).toBe(
      `Hello @Test User 1 #Test Channel 1 pubnub at https://pubnub.com. google at https://google.com, @Test User 2 @blankuser3 #Test Channel 2, random text @Test User 3, @Test User 4.`
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

  test("should reference 3 channels and 3 mentions with no order", async () => {
    const testId = `js-chat-${Date.now()}`
    const channel1Id = `${testId}-channel-1`
    const channel2Id = `${testId}-channel-2`
    const channel3Id = `${testId}-channel-3`
    const user1Id = `${testId}-user-1`
    const user2Id = `${testId}-user-2`
    const user3Id = `${testId}-user-3`

    const [channel1, channel2, channel3] = await Promise.all([
      chat.createChannel(channel1Id, { name: "Test Channel" }),
      chat.createChannel(channel2Id, { name: "Test Channel" }),
      chat.createChannel(channel3Id, { name: "Test Channel" }),
    ])
    const [user1, user2, user3] = await Promise.all([
      chat.createUser(user1Id, { name: "Test User" }),
      chat.createUser(user2Id, { name: "Test User" }),
      chat.createUser(user3Id, { name: "Test User" }),
    ])

    messageDraft.onChange(`Hello @real #real #fake @fake @real #fake #fake #real @real #fake #real @@@ @@@@ @ #fake #fake`)
    messageDraft.addReferencedChannel(channel1, 0)
    messageDraft.addReferencedChannel(channel2, 4)
    messageDraft.addReferencedChannel(channel3, 6)
    messageDraft.addMentionedUser(user1, 0)
    messageDraft.addMentionedUser(user2, 2)
    messageDraft.addMentionedUser(user3, 3)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.map(renderMessagePart).join("")).toBe(
      "Hello @Test User #Test Channel #fake @fake @Test User #fake #fake #Test Channel @Test User #fake #Test Channel @@@ @@@@ @ #fake #fake"
    )

    await Promise.all([
      channel1.delete({ soft: false }),
      channel2.delete({ soft: false }),
      channel3.delete({ soft: false }),
    ])
    await Promise.all([
      user1.delete({ soft: false }),
      user2.delete({ soft: false }),
      user3.delete({ soft: false }),
    ])
  })
})
