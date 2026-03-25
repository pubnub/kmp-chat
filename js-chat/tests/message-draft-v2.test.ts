import { Channel, Chat, Message, MessageDraft, MessageDraftV2, MixedTextTypedElement, CryptoUtils, CryptoModule } from "../dist-test"
import {
  createChatInstance,
  renderMessagePart,
  generateRandomString,
  createRandomChannel,
  createRandomUser,
  sleep
} from "./utils"
import { jest } from "@jest/globals"
import * as fs from "fs"

jest.setTimeout(60000)

describe("MessageDraft2", function () {
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

  test("deprecated createMessageDraftV2() should still work", async () => {
    const quoteText = "Message to quote"
    await channel.sendText(quoteText)
    await sleep(150)

    const history = await channel.getHistory()
    const quotedMsg = history.messages[0]

    const deprecatedDraft: MessageDraftV2 = channel.createMessageDraftV2()
    deprecatedDraft.addQuote(quotedMsg)
    const sentText = "Message sent via deprecated draft"
    deprecatedDraft.update(sentText)
    await deprecatedDraft.send()
    await sleep(300)

    const historyAfter = await channel.getHistory()
    const sent = historyAfter.messages.find((m) => m.content?.text === sentText)

    expect(sent.quotedMessage).toBeDefined()
    expect(sent.quotedMessage!.text).toBe(quoteText)
  }, 20000)

  test("should mention 2 users", async () => {
    const [user1, user2] = await Promise.all([createRandomUser(chat), createRandomUser(chat)])

    messageDraft.update("Hello @user1 and @user2")
    messageDraft.addMention(6, 6, "mention", user1.id)
    messageDraft.addMention(17, 6, "mention", user2.id)

    const messagePreview = messageDraft.getMessagePreview()

    expect(messagePreview.length).toBe(4)
    expect(messagePreview[0].type).toBe("text")
    expect(messagePreview[1].type).toBe("mention")
    expect(messagePreview[2].type).toBe("text")
    expect(messagePreview[3].type).toBe("mention")
    expect(messageDraft.value).toBe(`Hello @user1 and @user2`)
    expect(messagePreview.map(renderMessagePart).join("")).toBe(`Hello @@user1 and @@user2`)

    await Promise.all([user1.delete(), user2.delete()])
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
      user1.delete(),
      user2.delete(),
      user3.delete(),
    ])
  }, 20000)

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
          channel1.delete(),
          channel2.delete()
      ])
      await Promise.all([
          user1.delete(),
          user2.delete(),
          user4.delete(),
      ])
    }, 25000)

  test("should send and receive a message with quotedMessage and all SendTextParams", async () => {
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

    // Create draft with quote, update text, and send with all SendTextParams
    messageDraft.addQuote(quotedMsg)
    messageDraft.update("Some text with a mention")
    messageDraft.addMention(17, 7, "mention", "someUser")
    await messageDraft.send({
      meta: { customKey: "customValue" },
      storeInHistory: true,
      sendByPost: false,
      ttl: 10,
      customPushData: { title: "New message", body: "You have a new mention" },
    })

    // Wait for message and verify
    await messageReceived

    expect(receivedMessage).not.toBeNull()
    expect(receivedMessage!.quotedMessage).toBeDefined()
    expect(receivedMessage!.quotedMessage!.text).toBe(quoteText)
    expect(receivedMessage!.quotedMessage!.userId).toBe(chat.currentUser.id)
    expect(receivedMessage!.meta).toBeDefined()
    expect(receivedMessage!.meta.customKey).toBe("customValue")

    disconnect()
  }, 30000)

  test("should upload and retrieve files from channel via MessageDraftV2", async () => {
    const fileContent = "Test file content for PubNub"
    const buffer = Buffer.from(fileContent, 'utf-8')
    const file = chat.sdk.File.create({ name: "test.txt", mimeType: "text/plain", data: buffer })

    messageDraft.update("Message with file")
    messageDraft.files = [file]
    await messageDraft.send()
    await sleep(1000)

    const filesResult = await channel.getFiles({ limit: 10 })
    expect(filesResult).toBeDefined()
    expect(filesResult.files.length).toBeGreaterThan(0)

    const uploadedFile = filesResult.files[0]
    expect(uploadedFile.name).toBe("test.txt")

    const deleteResult = await channel.deleteFile({ id: uploadedFile.id, name: uploadedFile.name })
    expect(deleteResult).toBeDefined()
    expect(deleteResult.status).toBe(200)
  }, 20000)

  test("should be able to decrypt text and file messages sent using a previous encryption key via MessageDraftV2", async () => {
    const file1 = fs.createReadStream("tests/fixtures/pblogo1.png")
    const file2 = fs.createReadStream("tests/fixtures/pblogo2.png")
    const file3 = fs.createReadStream("tests/fixtures/pblogo3.png")

    const filesFromInput = [
      { stream: file1, name: "pblogo1.png", mimeType: "image/png" },
      { stream: file2, name: "pblogo2.png", mimeType: "image/png" },
      { stream: file3, name: "pblogo3.png", mimeType: "image/png" },
    ]

    const encryptedChat1 = await createChatInstance({
      config: {
        cryptoModule: CryptoModule.aesCbcCryptoModule({ cipherKey: "pubnubenigma" }),
        userId: "some-user-1",
      },
    })
    const encryptedChat2 = await createChatInstance({
      config: {
        cryptoModule: CryptoModule.aesCbcCryptoModule({ cipherKey: "another-pubnubenigma" }),
        userId: "some-user-2",
      },
    })

    const someRandomUser1 = await encryptedChat1.createUser(generateRandomString(), { name: "random-1" })
    const someGroupChannel = await encryptedChat1.createGroupConversation({ users: [someRandomUser1] })

    const draft = someGroupChannel.channel.createMessageDraft()
    draft.update("Random text")
    draft.files = filesFromInput
    await draft.send()

    await sleep(200) // History calls have around 130ms of cache time

    const firstCypherKeyHistory = await someGroupChannel.channel.getHistory()
    expect(firstCypherKeyHistory.messages[0].text).toBe("Random text")
    expect(firstCypherKeyHistory.messages[0].files.length).toBe(3)

    const sameChannelWithSecondCryptoKey = await encryptedChat2.getChannel(someGroupChannel.channel.id)
    const secondCypherKeyHistory = await sameChannelWithSecondCryptoKey.getHistory()
    expect(secondCypherKeyHistory.messages[0].text.startsWith("UE5FRAFBQ1JIE")).toBeTruthy()
    expect(secondCypherKeyHistory.messages[0].files.length).toBe(0)

    // Decryption with the original key
    const decryptedMessages = secondCypherKeyHistory.messages.map((msg) => {
      if (msg.error && msg.error.startsWith("Error while decrypting message content")) {
        return CryptoUtils.decrypt({
          chat: encryptedChat2,
          message: msg,
          decryptor: (encryptedContent) => {
            const cryptoModule = CryptoModule.aesCbcCryptoModule({ cipherKey: "pubnubenigma" })
            const enc = new TextDecoder("utf-8")
            const decryptedArrayBuffer = cryptoModule.decrypt(encryptedContent) as ArrayBuffer

            if (!decryptedArrayBuffer.byteLength) {
              return {
                type: "text",
                files: [],
                text: "(This message is corrupted)",
              }
            }
            return JSON.parse(enc.decode(decryptedArrayBuffer))
          },
        })
      }

      return msg
    })

    expect(decryptedMessages[0].text).toBe("Random text")
    expect(decryptedMessages[0].files.length).toBe(3)

    filesFromInput.forEach((fileFromInput, index) => {
      expect(decryptedMessages[0].files[index].name).toBe(fileFromInput.name)
      expect(decryptedMessages[0].files[index].type).toBe(fileFromInput.mimeType)
    })

    await someGroupChannel.channel.delete()
    await someRandomUser1.delete()
    await encryptedChat1.currentUser.delete()
    await encryptedChat2.currentUser.delete()
  }, 35000)
})
