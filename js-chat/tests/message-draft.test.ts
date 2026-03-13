import { Channel, Chat, Message, MessageDraft, MixedTextTypedElement } from "../dist-test"
import {
  createChatInstance,
  renderMessagePart,
  generateRandomString,
  createRandomChannel,
  createRandomUser,
  sleep
} from "./utils"
import { jest } from "@jest/globals"

describe("MessageDraft", function () {
  jest.retryTimes(2)

  let chat: Chat
  let channel: Channel
  let messageDraft: MessageDraft

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    channel = await createRandomChannel(chat)
    messageDraft = channel.createMessageDraft({ userSuggestionSource: "global" })
  })

  afterEach(async () => {
    await channel.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()
  })

  test("should mention 2 users", async () => {
    const [user1, user2] = await Promise.all([
      chat.createUser(generateRandomString(10), { name: "Alice" }),
      chat.createUser(generateRandomString(10), { name: "Bob" }),
    ])

    const text = `Hello @${user1.name} and @${user2.name}`
    messageDraft.update(text)

    const mention1Offset = text.indexOf(`@${user1.name}`)
    messageDraft.addMention(mention1Offset, user1.name.length + 1, "mention", user1.id)

    const mention2Offset = text.indexOf(`@${user2.name}`)
    messageDraft.addMention(mention2Offset, user2.name.length + 1, "mention", user2.id)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(4)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[0].content.text).toBe("Hello ")
    expect(messagePreview[1].type).toBe("mention")
    expect(messagePreview[1].content.id).toBe(user1.id)
    expect(messagePreview[1].content.name).toBe(`@${user1.name}`)
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[2].content.text).toBe(" and ")
    expect(messagePreview[3].type).toBe("mention")
    expect(messagePreview[3].content.id).toBe(user2.id)
    expect(messagePreview[3].content.name).toBe(`@${user2.name}`)
    expect(messageDraft.value).toBe(text)
    expect(messagePreview.map(renderMessagePart).join("")).toBe(text)

    await Promise.all([user1.delete({ soft: false }), user2.delete({ soft: false })])
  })

  test("should mention 2 - 3 users next to each other", async () => {
    const [user1, user2, user3] = await Promise.all([
      createRandomUser(chat),
      createRandomUser(chat),
      createRandomUser(chat),
    ])

    let elements: MixedTextTypedElement[][] = []
    let resolve, reject;

    const promise = new Promise((res, rej) => {
      resolve = res;
      reject = rej;
    });

    messageDraft.addChangeListener(async function(state) {
      elements.push(state.messageElements)
      if (elements.length == 3) {
        resolve()
        return
      }
      let mentions = await state.suggestedMentions
      messageDraft.insertSuggestedMention(mentions[0], mentions[0].replaceWith)
    })

    messageDraft.update("Hello @Te @Tes @Test")
    await promise

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(4)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("mention")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messagePreview.map(renderMessagePart).join("")).toBe(elements[2].map(renderMessagePart).join(""))

    await Promise.all([
      user1.delete({ soft: false }),
      user2.delete({ soft: false }),
      user3.delete({ soft: false }),
    ])
  }, 20000)

  test("should reference 2 channels", async () => {
    const [channel1, channel2] = await Promise.all([
      createRandomChannel(chat, "general"),
      createRandomChannel(chat, "random"),
    ])

    const text = `Hello #${channel1.name} and #${channel2.name}`
    messageDraft.update(text)

    const ref1Offset = text.indexOf(`#${channel1.name}`)
    messageDraft.addMention(ref1Offset, channel1.name.length + 1, "channelReference", channel1.id)

    const ref2Offset = text.indexOf(`#${channel2.name}`)
    messageDraft.addMention(ref2Offset, channel2.name.length + 1, "channelReference", channel2.id)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(4)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[0].content.text).toBe("Hello ")
    expect(messagePreview[1].type).toBe("channelReference")
    expect(messagePreview[1].content.id).toBe(channel1.id)
    expect(messagePreview[1].content.name).toBe(`#${channel1.name}`)
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[2].content.text).toBe(" and ")
    expect(messagePreview[3].type).toBe("channelReference")
    expect(messagePreview[3].content.id).toBe(channel2.id)
    expect(messagePreview[3].content.name).toBe(`#${channel2.name}`)

    await Promise.all([channel1.delete({ soft: false }), channel2.delete({ soft: false })])
  })

  test("should reference 2 channels and 2 mentions", async () => {
    const [channel1, channel2] = await Promise.all([
      createRandomChannel(chat, "general"),
      createRandomChannel(chat, "random"),
    ])
    const [user1, user2] = await Promise.all([
      chat.createUser(generateRandomString(10), { name: "Alice" }),
      chat.createUser(generateRandomString(10), { name: "Bob" }),
    ])

    const text = `Hello #${channel1.name} and @${user1.name} and #${channel2.name} or @${user2.name}.`
    messageDraft.update(text)

    const chan1Offset = text.indexOf(`#${channel1.name}`)
    messageDraft.addMention(chan1Offset, channel1.name.length + 1, "channelReference", channel1.id)

    const user1Offset = text.indexOf(`@${user1.name}`)
    messageDraft.addMention(user1Offset, user1.name.length + 1, "mention", user1.id)

    const chan2Offset = text.indexOf(`#${channel2.name}`)
    messageDraft.addMention(chan2Offset, channel2.name.length + 1, "channelReference", channel2.id)

    const user2Offset = text.indexOf(`@${user2.name}`)
    messageDraft.addMention(user2Offset, user2.name.length + 1, "mention", user2.id)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("channelReference")
    expect(messagePreview[1].content.id).toBe(channel1.id)
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messagePreview[3].content.id).toBe(user1.id)
    expect(messagePreview[4].type).toBe("text")
    expect(messagePreview[5].type).toBe("channelReference")
    expect(messagePreview[5].content.id).toBe(channel2.id)
    expect(messagePreview[6].type).toBe("text")
    expect(messagePreview[7].type).toBe("mention")
    expect(messagePreview[7].content.id).toBe(user2.id)

    expect(messagePreview.map(renderMessagePart).join("")).toBe(text)

    await Promise.all([channel1.delete({ soft: false }), channel2.delete({ soft: false })])
    await Promise.all([user1.delete({ soft: false }), user2.delete({ soft: false })])
  })

  test("should add 2 text links and 2 plain links", async () => {
    messageDraft.update("Hello https://pubnub.com, https://google.com and ")
    messageDraft.addLinkedText({
      text: "pubnub",
      link: "https://pubnub.com",
      positionInInput: messageDraft.value.length,
    })
    messageDraft.update("Hello https://pubnub.com, https://google.com and pubnub, ")
    messageDraft.addLinkedText({
      text: "google",
      link: "https://google.com",
      positionInInput: messageDraft.value.length,
    })

    const messagePreview = messageDraft.getMessagePreview()

    // Verify we have text links in the preview
    const textLinkElements = messagePreview.filter(e => e.type === "textLink")
    expect(textLinkElements.length).toBe(2)
  })

  test("should mix every type of message part", async () => {
      const [channel1, channel2] = await Promise.all([
          createRandomChannel(chat),
          createRandomChannel(chat)
      ])
      const [user1, user2, user4, user5] = await Promise.all([
          createRandomUser(chat),
          createRandomUser(chat),
          createRandomUser(chat),
          createRandomUser(chat),
      ])

      messageDraft.update("Hello ")
      messageDraft.addLinkedText({
          text: "pubnub",
          link: "https://pubnub.com",
          positionInInput: messageDraft.value.length,
      })

      messageDraft.update("Hello pubnub at https://pubnub.com! Hello to ")
      messageDraft.addLinkedText({
          text: "google",
          link: "https://google.com",
          positionInInput: messageDraft.value.length,
      })

      let elements: MixedTextTypedElement[][] = []
      let resolve, reject;

      const promise = new Promise((res, rej) => {
          resolve = res;
          reject = rej;
      });

      messageDraft.addChangeListener(async function(state) {
          elements.push(state.messageElements)
          let mentions = await state.suggestedMentions
          if (mentions.length == 0) {
              resolve()
              return
          }
          messageDraft.insertSuggestedMention(mentions[0], mentions[0].replaceWith)
      })

      messageDraft.update(
          `Hello pubnub at https://pubnub.com! Hello to google at https://google.com. Referencing #${channel1.name.substring(0,8)}, #${channel2.name.substring(0,8)}, #blankchannel, @${user1.name.substring(0,8)}, @${user2.name.substring(0,8)}, and mentioning @blankuser3 @${user4.name.substring(0,8)} @${user5.name.substring(0,8)}`
      )

      await promise
      const messagePreview = messageDraft.getMessagePreview()

      expect(messagePreview.length).toBe(16)
      expect(messagePreview[0].type).toBe("text")
      expect(messagePreview[1].type).toBe("textLink")
      expect(messagePreview[2].type).toBe("text")
      expect(messagePreview[3].type).toBe("textLink")
      expect(messagePreview[4].type).toBe("text")
      expect(messagePreview[5].type).toBe("channelReference")
      expect(messagePreview[6].type).toBe("text")
      expect(messagePreview[7].type).toBe("channelReference")
      expect(messagePreview[8].type).toBe("text")
      expect(messagePreview[9].type).toBe("mention")
      expect(messagePreview[10].type).toBe("text")
      expect(messagePreview[11].type).toBe("mention")
      expect(messagePreview[12].type).toBe("text")
      expect(messagePreview[13].type).toBe("mention")
      expect(messagePreview[14].type).toBe("text")
      expect(messagePreview[15].type).toBe("mention")
      expect(messagePreview.map(renderMessagePart).join("")).toBe(
          `Hello pubnub at https://pubnub.com! Hello to google at https://google.com. Referencing #${channel1.name}, #${channel2.name}, #blankchannel, @${user1.name}, @${user2.name}, and mentioning @blankuser3 @${user4.name} @${user5.name}`
      )
      expect(messageDraft.value).toBe(
          `Hello pubnub at https://pubnub.com! Hello to google at https://google.com. Referencing ${channel1.name}, ${channel2.name}, #blankchannel, ${user1.name}, ${user2.name}, and mentioning @blankuser3 ${user4.name} ${user5.name}`
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
    }, 25000)

  test("should send and receive a message with quotedMessage", async () => {
    // Send a preliminary message to quote
    const quoteText = "Message to quote"
    await channel.sendText(quoteText)
    await sleep(150)

    // Retrieve it from history to get a full Message object
    const history = await channel.getHistory()
    const quotedMsg = history.messages[0]

    // Set up onMessageReceived listener
    let receivedMessage: Message | null = null
    let resolveReceived: () => void
    const messageReceived = new Promise<void>((resolve) => {
      resolveReceived = resolve
    })

    const disconnect = channel.onMessageReceived((message) => {
      receivedMessage = message
      resolveReceived()
    })
    await sleep(2000)

    // Create draft with quote, update text, and send
    messageDraft.addQuote(quotedMsg)
    messageDraft.update("Some text with a quote")
    await messageDraft.send()

    // Wait for message and verify
    await messageReceived

    expect(receivedMessage).not.toBeNull()
    expect(receivedMessage!.quotedMessage).toBeDefined()
    expect(receivedMessage!.quotedMessage!.text).toBe(quoteText)
    expect(receivedMessage!.quotedMessage!.userId).toBe(chat.currentUser.id)

    disconnect()
  }, 30000)
})
