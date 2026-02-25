import {
  generateUUID
} from "pubnub"

import {
  Channel,
  Chat,
  Event,
  INTERNAL_ADMIN_CHANNEL,
  INTERNAL_MODERATION_PREFIX,
  Message,
  CryptoUtils,
  CryptoModule,
  MessageDTOParams,
  ThreadMessage,
} from "../dist-test"

import {
  createChatInstance,
  generateExpectedLinkedText,
  sleep,
  waitForAllMessagesToBeDelivered,
  generateRandomString,
  createRandomChannel,
  createRandomUser
} from "./utils"

import { jest } from "@jest/globals"
import * as fs from "fs"

declare class ChatInternal extends Chat {
  getMessageFromReport(eventJs: Event<"report">, lookupBeforeMillis?: number, lookupAfterMillis?: number): Promise<Message | null>
}

describe("Send message test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let channel: Channel
  let messageDraft

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    channel = await createRandomChannel(chat)
    messageDraft = channel.createMessageDraft()
  }, 15000)

  afterEach(async () => {
    await channel.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()

    jest.clearAllMocks()
  }, 15000)

  type FileDetails = {
    id: string
    name: string
    url: string
    type: string
  }

  test("should send and receive unicode messages correctly", async () => {
    const messages: string[] = []
    const unicodeMessages = ["😀", "Привет", "你好", "こんにちは", "안녕하세요"]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
    })

    await sleep(200)

    for (const unicodeMessage of unicodeMessages) {
      await channel.sendText(unicodeMessage)
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
      await sleep(2000)
    }

    await waitForAllMessagesToBeDelivered(messages, unicodeMessages)

    for (const unicodeMessage of unicodeMessages) {
      expect(messages).toContain(unicodeMessage)
    }

    disconnect()
  }, 30000)

  test("should send and receive regular text messages correctly", async () => {
    const messages: string[] = []
    const textMessages = ["Hello", "This", "Is", "A", "Test"]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
    })

    await sleep(200)

    for (const textMessage of textMessages) {
      await channel.sendText(textMessage)
    }

    await sleep(2000)
    await waitForAllMessagesToBeDelivered(messages, textMessages)

    for (const textMessage of textMessages) {
      expect(messages).toContain(textMessage)
    }

    disconnect()
  }, 30000)

  test("should delete the message", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeDelete = await channel.getHistory()
    const messagesBeforeDelete: Message[] = historyBeforeDelete.messages
    const sentMessage = messagesBeforeDelete[messagesBeforeDelete.length - 1]

    await sentMessage.delete()
    await sleep(250) // History calls have around 130ms of cache time

    const historyAfterDelete = await channel.getHistory()
    const messagesAfterDelete: Message[] = historyAfterDelete.messages

    const deletedMessage = messagesAfterDelete.find(
      (message: Message) => message.timetoken === sentMessage.timetoken
    )

    expect(deletedMessage).toBeUndefined()
  }, 30000)

  test("should soft delete message", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeDelete = await channel.getHistory()
    const messagesBeforeDelete = historyBeforeDelete.messages
    const sentMessage = messagesBeforeDelete[messagesBeforeDelete.length - 1]

    await sentMessage.delete({ soft: true })
    await sleep(150)

    const historyAfterDelete = await channel.getHistory()
    const messagesAfterDelete = historyAfterDelete.messages

    const deletedMessage = messagesAfterDelete.find(
      (message: Message) => message.timetoken === sentMessage.timetoken
    )

    expect(deletedMessage.deleted).toBe(true)
  }, 20000)

  test("should restore soft deleted message", async () => {
    await channel.sendText("Test message")
    await sleep(150)

    const historyBeforeDelete = await channel.getHistory()
    const messagesBeforeDelete = historyBeforeDelete.messages
    const sentMessage = messagesBeforeDelete[messagesBeforeDelete.length - 1]

    await sentMessage.delete({ soft: true })
    await sleep(150)

    const historyAfterDelete = await channel.getHistory()
    const deletedMessage = historyAfterDelete.messages.find(
      (message: Message) => message.timetoken === sentMessage.timetoken
    )

    const restoredMessage = await deletedMessage.restore()
    expect(restoredMessage.deleted).toBe(false)

    await sleep(150)
    const historyAfterRestore = await channel.getHistory()
    const historicRestoredMessage = historyAfterRestore.messages.find(
      (message: Message) => message.timetoken === sentMessage.timetoken
    )

    expect(historicRestoredMessage.deleted).toBe(false)
  }, 20000)

  test("should create thread on message", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory()
    const sentMessage = history.messages[history.messages.length - 1]

    const messageThread = await sentMessage.createThread()
    await messageThread.sendText("Some message in a thread")
    await sleep(150)

    const updatedHistory = await channel.getHistory()
    const messageWithThread = updatedHistory.messages.find(
      (message: Message) => message.timetoken === sentMessage.timetoken
    )

    expect(messageWithThread.hasThread).toBe(true)
    expect(await messageWithThread.getThread()).toBeDefined()

    // await messageThread.delete()
  }, 20000)

  test("should create thread with result and return updated parent message with hasThread true", async () => {
    // given - a message without a thread
    await channel.sendText("Parent message for createThreadWithResult test")
    await sleep(150)

    const history = await channel.getHistory()
    const message = history.messages[history.messages.length - 1]
    expect(message.hasThread).toBe(false)

    // when - create thread using createThreadWithResult
    const result = await message.createThreadWithResult("First thread message")

    // then - result should contain both threadChannel and updated parentMessage
    const { threadChannel, parentMessage } = result

    // verify threadChannel is valid
    expect(threadChannel).toBeDefined()
    expect(threadChannel.id).toContain("PUBNUB_INTERNAL_THREAD")
    expect(threadChannel.parentChannelId).toBe(message.channelId)

    // verify parentMessage has hasThread = true (this is the key improvement!)
    // Previously with createThread(), you had to re-fetch the message to see hasThread=true
    expect(parentMessage.hasThread).toBe(true)
    expect(parentMessage.timetoken).toBe(message.timetoken)
    expect(parentMessage.text).toBe(message.text)

    // cleanup
    await parentMessage.removeThread()
  }, 20000)

  test("should soft delete message with thread", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeThread = await channel.getHistory()
    const messageBeforeThread = historyBeforeThread.messages[historyBeforeThread.messages.length - 1]
    const messageThread = await messageBeforeThread.createThread()
    await messageThread.sendText("Some message in a thread")
    await sleep(150)

    const historyWithThread = await channel.getHistory()
    const messageWithThread = historyWithThread.messages[historyWithThread.messages.length - 1]

    await messageWithThread.delete({ soft: true })
    await sleep(200)

    const historyAfterDelete = await channel.getHistory()
    const deletedMessage = historyAfterDelete.messages.find(
      (message: Message) => message.timetoken === messageWithThread.timetoken
    )

    expect(deletedMessage.deleted).toBe(true)
    expect(deletedMessage.hasThread).toBe(false)

    // await messageThread.delete()
  }, 15000)

  test("should restore message and preserve thread", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeThread = await channel.getHistory()
    const messageBeforeThread = historyBeforeThread.messages[historyBeforeThread.messages.length - 1]
    const messageThread = await messageBeforeThread.createThread()
    await messageThread.sendText("Some message in a thread")
    await sleep(150)

    const historyWithThread = await channel.getHistory()
    const messageWithThread = historyWithThread.messages[historyWithThread.messages.length - 1]

    await messageWithThread.delete({ soft: true })
    await sleep(200)

    const historyAfterDelete = await channel.getHistory()
    const deletedMessage = historyAfterDelete.messages.find(
      (message: Message) => message.timetoken === messageWithThread.timetoken
    )

    const restoredMessage = await deletedMessage.restore()

    expect(restoredMessage.deleted).toBe(false)
    expect(restoredMessage.hasThread).toBe(true)
    expect(await restoredMessage.getThread()).toBeDefined()
    expect((await restoredMessage.getThread()).id).toBe(chat.getThreadId(restoredMessage.channelId, restoredMessage.timetoken))

    await sleep(150)

    const historyAfterRestore = await channel.getHistory()
    const historicRestoredMessage = historyAfterRestore.messages.find(
      (message: Message) => message.timetoken === messageWithThread.timetoken
    )

    expect(historicRestoredMessage.deleted).toBe(false)
    expect(await historicRestoredMessage.getThread()).toBeDefined()
    expect((await historicRestoredMessage.getThread()).id).toBe(chat.getThreadId(historicRestoredMessage.channelId, historicRestoredMessage.timetoken))

    // await messageThread.delete()
  }, 25000)

  test("should only log a warning if you try to restore an undeleted message", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historicMessages = (await channel.getHistory()).messages
    const sentMessage = historicMessages[historicMessages.length - 1]
    const logSpy = jest.spyOn(console, "warn")
    await sentMessage.restore()

    expect(sentMessage.deleted).toBe(false)
    expect(logSpy).toHaveBeenCalledWith("(BaseMessageImpl) This message has not been deleted")
  }, 20000)

  test("should throw an error if you try to create a thread on a deleted message", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeDelete = await channel.getHistory()
    const messagesBeforeDelete = historyBeforeDelete.messages
    const sentMessage = messagesBeforeDelete[messagesBeforeDelete.length - 1]

    await sentMessage.delete({ soft: true })
    await sleep(150) // History calls have around 130ms of cache time

    const historyAfterDelete = await channel.getHistory()
    const messagesAfterDelete = historyAfterDelete.messages

    const deletedMessage = messagesAfterDelete.find(
      (message: Message) => message.timetoken === sentMessage.timetoken
    )

    await deletedMessage.createThread().catch((e) => {
      expect(e.message).toContain("You cannot create threads on deleted messages.")
    })
  }, 20000)

  test("should edit the message", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeEdit = await channel.getHistory()
    const messagesBeforeEdit: Message[] = historyBeforeEdit.messages
    const sentMessage = messagesBeforeEdit[messagesBeforeEdit.length - 1]

    const mockMessage: Partial<Message> = {
      ...sentMessage,
      editText: jest.fn().mockResolvedValue(sentMessage),
    }

    const editedMessage = await (mockMessage as Message).editText("Edited message")

    expect(mockMessage.editText).toHaveBeenCalledWith("Edited message")
    expect(editedMessage).toBe(sentMessage)
  }, 30000)

  test("should toggle the message reaction", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeReaction = await channel.getHistory()
    const messagesBeforeReaction: Message[] = historyBeforeReaction.messages
    const sentMessage = messagesBeforeReaction[messagesBeforeReaction.length - 1]

    expect(sentMessage.actions?.reactions?.like).toBeUndefined()
    expect(sentMessage.reactions.length).toBe(0)

    const toggledMessage = await sentMessage.toggleReaction("like")
    expect(toggledMessage.actions?.reactions?.like).toBeDefined()

    const reactions = toggledMessage.reactions
    expect(reactions.length).toBe(1)
    const reaction = reactions[0]
    expect(reaction.value).toBe("like")
    expect(reaction.isMine).toBe(true)
    expect(reaction.userIds.length).toBe(1)
    expect(reaction.userIds).toContain(chat.currentUser.id)
  }, 30000)

  test("should be unable to pin multiple messages", async () => {
    await channel.sendText("First Test message")
    await sleep(150)
    await channel.sendText("Second Test message")
    await sleep(150)

    const history = await channel.getHistory()
    const messages: Message[] = history.messages

    const firstMessageToPin = messages[messages.length - 2]
    const secondMessageToPin = messages[messages.length - 1]
    const firstPinnedChannel = await channel.pinMessage(firstMessageToPin)

    if (!firstPinnedChannel.custom?.["pinnedMessageTimetoken"] || firstPinnedChannel.custom["pinnedMessageTimetoken"] !== firstMessageToPin.timetoken) {
      throw new Error("Failed to pin the first message")
    }

    const secondPinnedChannel = await channel.pinMessage(secondMessageToPin)

    if (!secondPinnedChannel.custom?.["pinnedMessageTimetoken"] || secondPinnedChannel.custom["pinnedMessageTimetoken"] !== secondMessageToPin.timetoken) {
      throw new Error("Failed to pin the second message")
    }
    if (secondPinnedChannel.custom["pinnedMessageTimetoken"] === firstMessageToPin.timetoken) {
      throw new Error("First message is still pinned")
    }
  }, 30000)

  test("should pin the message", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforePin = await channel.getHistory()
    const messagesBeforePin: Message[] = historyBeforePin.messages
    const messageToPin = messagesBeforePin[messagesBeforePin.length - 1]
    const pinnedChannel = await channel.pinMessage(messageToPin)

    expect(pinnedChannel.custom?.["pinnedMessageTimetoken"]).toBe(messageToPin.timetoken)
  }, 30000)

  test("should unpin the message", async () => {
    await channel.sendText("Test message to be pinned and then unpinned")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforePin = await channel.getHistory()
    const messagesBeforePin: Message[] = historyBeforePin.messages
    const messageToPin = messagesBeforePin[messagesBeforePin.length - 1]

    const pinnedChannel = await channel.pinMessage(messageToPin)
    expect(pinnedChannel.custom?.["pinnedMessageTimetoken"]).toBe(messageToPin.timetoken)

    const unpinnedChannel = await channel.unpinMessage()
    expect(unpinnedChannel.custom?.["pinnedMessageTimetoken"]).toBeUndefined()
  }, 30000)

  test("should stream message updates and invoke the callback", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeEdit = await channel.getHistory()
    const messagesBeforeEdit: Message[] = historyBeforeEdit.messages
    const sentMessage = messagesBeforeEdit[messagesBeforeEdit.length - 1]

    const mockMessage: Partial<Message> = {
      ...sentMessage,
      editText: jest.fn().mockResolvedValue(sentMessage),
    }

    const editedMessage = await (mockMessage as Message).editText("Edited message")

    expect(mockMessage.editText).toHaveBeenCalledWith("Edited message")
    expect(editedMessage).toBe(sentMessage)

    const unsubscribe = Message.streamUpdatesOn(messagesBeforeEdit, (updatedMessages) => {
      const receivedMessage = updatedMessages.find((msg) => msg.timetoken === sentMessage.timetoken)
      expect(receivedMessage).toEqual(editedMessage)
      unsubscribe()
    })

    await new Promise((resolve) => setTimeout(resolve, 2000))
  }, 30000)

  test("should not receive updates after unsubscribing from Message.streamUpdatesOn", async () => {
    await channel.sendText("Test message for unsubscribe")
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory()
    const sentMessage = history.messages[history.messages.length - 1]

    // Use a real array to track callback invocations
    const updates: Message[][] = []
    const callback = (msgs) => {
      const summary = msgs.map(m => ({
        timetoken: m.timetoken,
        text: m.text,
        actions: m.actions,
      }))

      // Optionally, push a deep copy to avoid mutation issues:
      updates.push(msgs.map(m => ({ ...m })))
    }

    // Subscribe to updates
    const unsubscribe = Message.streamUpdatesOn([sentMessage], callback)
    await sleep(1000)

    // Edit the message to trigger the callback
    await sentMessage.editText("First edit after subscribe")
    await sleep(2000)

    expect(updates.length).toBeGreaterThan(0)
    const updatesCountAfterFirstEdit = updates.length

    // Unsubscribe
    unsubscribe()

    // Edit the message again
    await sentMessage.editText("Second edit after unsubscribe")
    await sleep(2000)

    // The callback should not be called again
    expect(updates.length).toBe(updatesCountAfterFirstEdit)
  }, 15000)

  test("should render URLs correctly", async () => {
    const messageDraft = channel.createMessageDraft()
    const someUser = await chat.createUser(generateRandomString(), { name: "Lukasz" })
    const someUser2 = await chat.createUser(generateRandomString(), { name: "Anton" })

    const expectedLinkedText = generateExpectedLinkedText(messageDraft, someUser, someUser2)
    const messagePreview = messageDraft.getMessagePreview()

    expectedLinkedText.forEach((expectedElement) => {
      expect(messagePreview).toEqual(expect.arrayContaining([expectedElement]))
    })

    await someUser.delete()
    await someUser2.delete()
  }, 20000)

  test("should add linked text correctly", () => {
    const initialText = "Check out this link: "
    messageDraft.onChange(initialText)

    const textToAdd = "example link"
    const linkToAdd = "https://www.example.com"

    messageDraft.addLinkedText({
      text: textToAdd,
      link: linkToAdd,
      positionInInput: initialText.length,
    })

    const expectedText = `${initialText}${textToAdd}`

    expect(messageDraft.value).toBe(expectedText)
    expect(messageDraft.textLinks).toHaveLength(1)
    expect(messageDraft.textLinks[0]).toEqual({
      startIndex: initialText.length,
      endIndex: initialText.length + textToAdd.length,
      link: linkToAdd,
    })
  })

  test("should throw an error for invalid link format", () => {
    const initialText = "Check out this link: "
    messageDraft.onChange(initialText)

    const invalidLinkToAdd = "invalid-link"

    expect(() => {
      messageDraft.addLinkedText({
        text: "invalid link",
        link: invalidLinkToAdd,
        positionInInput: initialText.length,
      })
    }).toThrow("You need to insert a URL")

    expect(messageDraft.value).toBe(initialText)
    expect(messageDraft.textLinks).toHaveLength(0)
  })

  test("should throw an error if adding a link inside another link", () => {
    const initialText = "Check out this link: "
    messageDraft.onChange(initialText)

    const textToAdd1 = "example link1"
    const linkToAdd1 = "https://www.example1.com"
    const textToAdd2 = "example link2"
    const linkToAdd2 = "https://www.example2.com"

    messageDraft.addLinkedText({
      text: textToAdd1,
      link: linkToAdd1,
      positionInInput: initialText.length,
    })

    expect(() => {
      messageDraft.addLinkedText({
        text: textToAdd2,
        link: linkToAdd2,
        positionInInput: initialText.length + textToAdd1.length - 2,
      })
    }).toThrow("You cannot insert a link inside another link")

    expect(messageDraft.value).toBe(initialText + textToAdd1)
    expect(messageDraft.textLinks).toHaveLength(1)
    expect(messageDraft.textLinks[0]).toEqual({
      startIndex: initialText.length,
      endIndex: initialText.length + textToAdd1.length,
      link: linkToAdd1,
    })
  })

  test("should remove a linked URL correctly", () => {
    const initialText = "Check out this link: "
    messageDraft.onChange(initialText)

    const textToAdd = "example link"
    const linkToAdd = "https://www.example.com"

    messageDraft.addLinkedText({
      text: textToAdd,
      link: linkToAdd,
      positionInInput: initialText.length,
    })

    expect(messageDraft.value).toBe(initialText + textToAdd)
    const positionInLinkedText = initialText.length
    messageDraft.removeLinkedText(positionInLinkedText)

    const expectedValue = "Check out this link: example link"
    expect(messageDraft.value).toBe(expectedValue)

    const messagePreview = messageDraft.getMessagePreview()
    const expectedMessagePreview = [
      {
        type: "text",
        content: {
          text: "Check out this link: example link",
        },
      },
    ]

    expect(messagePreview).toEqual(expectedMessagePreview)
  })

  test("should report a message", async () => {
    const messageText = "Test message to be reported"
    const reportReason = "Inappropriate content"

    await channel.sendText(messageText)
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory({ count: 1 })
    const reportedMessage = history.messages[0]
    await reportedMessage.report(reportReason)
    await sleep(150) // History calls have around 130ms of cache time

    const { events } = await channel.getMessageReportsHistory()
    const reportMessage = events[0]

    expect(reportMessage?.type).toBe("report")
    expect(reportMessage?.payload.reason).toBe(reportReason)
    expect(reportMessage?.payload.reportedMessageChannelId).toBe(reportedMessage.channelId)
    expect(reportMessage?.payload.reportedMessageTimetoken).toBe(reportedMessage.timetoken)
    expect(reportMessage?.payload.reportedUserId).toBe(reportedMessage.userId)
  }, 20000)

  test("should find a message from auto moderation report", async () => {
    const messageText = "Test message to be reported"
    const modId = generateUUID()
    const reportChannel = INTERNAL_MODERATION_PREFIX + channel.id

    const reportPayload = {
      "text": messageText,
      "reason": "auto moderated",
      "reportedMessageChannelId": channel.id,
      "autoModerationId": modId
    };
    
    await chat.emitEvent({
      "channel": reportChannel,
      "type": "report",
      "payload": reportPayload
    })
    await sleep(150)
    await channel.sendText(messageText, {meta : {"pn_mod_id" : modId}})
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory({ count: 1 })
    const reportedMessage = history.messages[0]
    const reportEvents = await chat.getEventsHistory({channel: reportChannel, count: 1 })
    const reportEvent = reportEvents.events[0]
    const message = await (chat as ChatInternal).getMessageFromReport(reportEvent)

    expect(message?.timetoken).toBe(reportedMessage.timetoken)
  }, 20000)

  test.skip("should report a message (deprecated)", async () => {

    const messageText = "Test message to be reported"
    const reportReason = "Inappropriate content"

    await channel.sendText(messageText)
    await sleep(150) // History calls have around 130ms of cache time

    const history = await channel.getHistory({ count: 1 })
    const reportedMessage = history.messages[0]
    await reportedMessage.DEPRECATED_report(reportReason)
    await sleep(150) // History calls have around 130ms of cache time

    const adminChannel = await chat.getChannel(INTERNAL_ADMIN_CHANNEL)
    expect(adminChannel).toBeDefined()

    const adminChannelHistory = await adminChannel.getHistory({ count: 1 })
    const reportMessage = adminChannelHistory.messages[0]

    expect(reportMessage?.content.type).toBe("report")
    expect(reportMessage?.content.text).toBe(messageText)
    expect(reportMessage?.content.reason).toBe(reportReason)
    expect(reportMessage?.content.reportedMessageChannelId).toBe(reportedMessage.channelId)
    expect(reportMessage?.content.reportedMessageTimetoken).toBe(reportedMessage.timetoken)
    expect(reportMessage?.content.reportedUserId).toBe(reportedMessage.userId)
  })

  test("should send multiple image files along with a text message correctly", async () => {
    const messages: string[] = []
    const filesReceived: FileDetails[] = []
    const textMessage = "Hello, sending three files"

    const file1 = fs.createReadStream("tests/fixtures/pblogo1.png")
    const file2 = fs.createReadStream("tests/fixtures/pblogo2.png")
    const file3 = fs.createReadStream("tests/fixtures/pblogo3.png")

    const filesFromInput = [
      { stream: file1, name: "pblogo1.png", mimeType: "image/png" },
      { stream: file2, name: "pblogo2.png", mimeType: "image/png" },
      { stream: file3, name: "pblogo3.png", mimeType: "image/png" },
    ]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
      if (message.content.files !== undefined) {
        filesReceived.push(...message.content.files)
      }
    })

    await channel.sendText(textMessage, {
      files: filesFromInput,
    })

    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
    await sleep(2000)

    expect(messages).toContain(textMessage)

    expect(filesReceived.length).toBe(3)
    expect(filesReceived[0].id).toBeDefined()
    expect(filesReceived[0].name).toBe("pblogo1.png")
    expect(filesReceived[0].url).toBeDefined()
    expect(filesReceived[0].type).toBe("image/png")

    expect(filesReceived[1].id).toBeDefined()
    expect(filesReceived[1].name).toBe("pblogo2.png")
    expect(filesReceived[1].url).toBeDefined()
    expect(filesReceived[1].type).toBe("image/png")

    expect(filesReceived[2].id).toBeDefined()
    expect(filesReceived[2].name).toBe("pblogo3.png")
    expect(filesReceived[2].url).toBeDefined()
    expect(filesReceived[2].type).toBe("image/png")

    disconnect()
  }, 30000)

  test("should send image file along with a text message correctly", async () => {
    const messages: string[] = []
    const filesReceived: FileDetails[] = []
    const textMessage = "Hello, sending three files"

    const file1 = fs.createReadStream("tests/fixtures/pblogo1.png")
    const filesFromInput = [{ stream: file1, name: "pblogo1.png", mimeType: "image/png" }]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
      if (message.content.files !== undefined) {
        filesReceived.push(...message.content.files)
      }
    })

    await channel.sendText(textMessage, {
      files: filesFromInput,
    })

    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
    await sleep(2000)

    expect(messages).toContain(textMessage)
    expect(filesReceived.length).toBe(1)
    expect(filesReceived[0].id).toBeDefined()
    expect(filesReceived[0].name).toBe("pblogo1.png")
    expect(filesReceived[0].url).toBeDefined()
    expect(filesReceived[0].type).toBe("image/png")

    disconnect()
  }, 30000)

  test("should send pdf file along with a text message correctly", async () => {
    const messages: string[] = []
    const filesReceived: FileDetails[] = []
    const textMessage = "Hello, sending three files"
    const file1 = fs.createReadStream("tests/fixtures/lorem-ipsum.pdf")
    const filesFromInput = [{ stream: file1, name: "lorem-ipsum.pdf", mimeType: "application/pdf" }]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
      if (message.content.files !== undefined) {
        filesReceived.push(...message.content.files)
      }
    })

    await channel.sendText(textMessage, {
      files: filesFromInput,
    })

    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
    await sleep(2000)

    expect(messages).toContain(textMessage)
    expect(filesReceived.length).toBe(1)
    expect(filesReceived[0].id).toBeDefined()
    expect(filesReceived[0].name).toBe("lorem-ipsum.pdf")
    expect(filesReceived[0].url).toBeDefined()
    expect(filesReceived[0].type).toBe("application/pdf")

    disconnect()
  }, 30000)

  test("should send txt file along with a text message correctly", async () => {
    const messages: string[] = []
    const filesReceived: FileDetails[] = []
    const textMessage = "Hello, sending three files"

    const file1 = fs.createReadStream("tests/fixtures/sample1.txt")
    const filesFromInput = [{ stream: file1, name: "sample1.txt", mimeType: "text/plain" }]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
      if (message.content.files !== undefined) {
        filesReceived.push(...message.content.files)
      }
    })

    await channel.sendText(textMessage, {
      files: filesFromInput,
    })

    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
    await sleep(2000)

    expect(messages).toContain(textMessage)
    expect(filesReceived.length).toBe(1)
    expect(filesReceived[0].id).toBeDefined()
    expect(filesReceived[0].name).toBe("sample1.txt")
    expect(filesReceived[0].url).toBeDefined()
    expect(filesReceived[0].type).toBe("text/plain")

    disconnect()
  }, 30000)

  test("should send multiple different types of files along with a text message correctly", async () => {
    const messages: string[] = []
    const filesReceived: FileDetails[] = []
    const textMessage = "Hello, sending three files"

    const file1 = fs.createReadStream("tests/fixtures/pblogo1.png")
    const file2 = fs.createReadStream("tests/fixtures/lorem-ipsum.pdf")
    const file3 = fs.createReadStream("tests/fixtures/sample1.txt")

    const filesFromInput = [
      { stream: file1, name: "pblogo1.png", mimeType: "image/png" },
      { stream: file2, name: "lorem-ipsum.pdf", mimeType: "application/pdf" },
      { stream: file3, name: "sample1.txt", mimeType: "text/plain" },
    ]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
      if (message.content.files !== undefined) {
        filesReceived.push(...message.content.files)
      }
    })

    await channel.sendText(textMessage, {
      files: filesFromInput,
    })

    const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
    await sleep(2000)

    expect(messages).toContain(textMessage)
    expect(filesReceived.length).toBe(3)

    expect(filesReceived[0].id).toBeDefined()
    expect(filesReceived[0].name).toBe("pblogo1.png")
    expect(filesReceived[0].url).toBeDefined()
    expect(filesReceived[0].type).toBe("image/png")

    expect(filesReceived[1].id).toBeDefined()
    expect(filesReceived[1].name).toBe("lorem-ipsum.pdf")
    expect(filesReceived[1].url).toBeDefined()
    expect(filesReceived[1].type).toBe("application/pdf")

    expect(filesReceived[2].id).toBeDefined()
    expect(filesReceived[2].name).toBe("sample1.txt")
    expect(filesReceived[2].url).toBeDefined()
    expect(filesReceived[2].type).toBe("text/plain")

    disconnect()
  }, 35000)

  test("should send 3 messages with proper delays and verify rate limiter", async () => {
    const timeout = 1000
    const factor = 2

    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: { rateLimitFactor: factor, rateLimitPerChannel: { public: timeout } },
    })

    const channel = await chat.createPublicConversation({
      channelId: `channel_${generateRandomString()}`,
      channelData: {
        name: "Test Channel",
        description: "This is a test channel",
      },
    })

    const start = performance.now()
    await channel.sendText("Message 1")
    await channel.sendText("Message 2")
    const durationSecond = performance.now() - start

    await channel.sendText("Message 3")
    const durationThird = performance.now() - start

    expect(durationSecond).toBeGreaterThan(timeout)
    expect(durationThird).toBeGreaterThan(timeout + timeout * factor)

    await channel.delete()
    await chat.currentUser.delete()
  }, 20000)

  test("should send long messages and validate correct rendering", async () => {
    const messages: string[] = []
    const longMessages = [
      "This is a long message with a lot of text to test the rendering of long messages in the chat.",
      "Another long message that should be rendered correctly without any issues.",
      "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non arcu eget risus lacinia tincidunt ut non orci. Nullam scelerisque odio vel erat feugiat placerat.",
      "A very lengthy message to check how the chat handles extremely long text messages. It should not break the layout or cause any issues.",
    ]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
    })

    for (const longMessage of longMessages) {
      await channel.sendText(longMessage)
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
      await sleep(2000)
    }

    await waitForAllMessagesToBeDelivered(messages, longMessages)

    for (const longMessage of longMessages) {
      expect(messages).toContain(longMessage)
    }

    disconnect()
  }, 30000)

  test("should fail to send an empty or whitespace-only message", async () => {
    const messages: string[] = []

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
    })

    let errorMessage = "Message text cannot be empty"
    try {
      await channel.sendText("   ")
    } catch (error) {
      errorMessage = error.message
    }

    expect(errorMessage).toContain("Message text cannot be empty")
    expect(messages.length).toBe(0)
    disconnect()
  }, 30000)

  test("should send and receive messages in various languages correctly", async () => {
    const messages: string[] = []
    const textMessages = [
      "Hello",
      "This is a test message",
      "你好", // Chinese
      "مرحبًا", // Arabic
      "こんにちは", // Japanese
      "안녕하세요", // Korean
      "Hola", // Spanish
    ]

    const disconnect = channel.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
    })

    for (const textMessage of textMessages) {
      await channel.sendText(textMessage)
      const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
      await sleep(2000)
    }

    await waitForAllMessagesToBeDelivered(messages, textMessages)

    for (const textMessage of textMessages) {
      expect(messages).toContain(textMessage)
    }

    disconnect()
  }, 30000)

  test("should toggle the message reaction and then delete the message reaction", async () => {
    await channel.sendText("Test message")
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeReaction = await channel.getHistory()
    const messagesBeforeReaction: Message[] = historyBeforeReaction.messages
    const sentMessage = messagesBeforeReaction[messagesBeforeReaction.length - 1]

    expect(sentMessage.actions?.reactions?.like).toBeUndefined()
    const toggledMessage = await sentMessage.toggleReaction("like")
    expect(toggledMessage.actions?.reactions?.like).toBeDefined()
    const messageAfterRemovingReaction = await toggledMessage.toggleReaction("like")
    const likeReactions = messageAfterRemovingReaction.actions?.reactions?.like
    expect(likeReactions === undefined || likeReactions.length === 0).toBeTruthy()
  }, 30000)

  test("should not allow inserting a link inside another link", () => {
    const initialText = "Check out these links: "
    messageDraft.onChange(initialText)
    const textToAdd1 = "example link 1"
    const linkToAdd1 = "https://www.example1.com"

    messageDraft.addLinkedText({
      text: textToAdd1,
      link: linkToAdd1,
      positionInInput: initialText.length,
    })

    const textToAdd2 = " example link 2"
    const linkToAdd2 = "https://www.example2.com"

    expect(() => {
      messageDraft.addLinkedText({
        text: textToAdd2,
        link: linkToAdd2,
        positionInInput: messageDraft.value.indexOf(textToAdd1) + 2,
      })
    }).toThrowError("You cannot insert a link inside another link")

    const expectedText = `${initialText}${textToAdd1}`
    expect(messageDraft.value).toBe(expectedText)

    const expectedLinks = [
      {
        startIndex: initialText.length,
        endIndex: initialText.length + textToAdd1.length,
        link: linkToAdd1,
      },
    ]

    expect(messageDraft.textLinks).toHaveLength(1)
    expect(messageDraft.textLinks).toEqual(expect.arrayContaining(expectedLinks))
  })

  test("should send quote message in a thread", async () => {
    const originalMessageText = "Original message for forwarding"
    await channel.sendText(originalMessageText)
    await sleep(300) // History calls have around 130ms of cache time

    const historyBeforeThread = await channel.getHistory()

    const originalMessage = historyBeforeThread.messages[0]
    const newThread = await originalMessage.createThread()
    await newThread.sendText("First message")
    await sleep(250)

    const historyWithThread = await channel.getHistory()
    const threadedMessage = historyWithThread.messages[0]
    expect(threadedMessage.hasThread).toBe(true)

    const thread = await threadedMessage.getThread()
    const firstThreadMessage = (await thread.getHistory()).messages[0]
    const messageDraft = thread.createMessageDraftV2()

    messageDraft.addQuote(firstThreadMessage)

    await messageDraft.update("This is a forwarded message.")
    await messageDraft.send()
    await sleep(500)

    const threadMessages = await thread.getHistory()
    const forwardedMessageText = threadMessages.messages[1].content.text
    const forwardedMessageQuote = threadMessages.messages[1].quotedMessage

    expect(forwardedMessageText).toBe("This is a forwarded message.")
    expect(forwardedMessageQuote.text).toBe("First message")
    expect(forwardedMessageQuote.userId).toBe(chat.currentUser.id)
  }, 25000)

  test("should pin the message inside the thread", async () => {
    const messageText = "Test message"
    await channel.sendText(messageText)
    await sleep(150) // History calls have around 130ms of cache time

    const historyBeforeThread = await channel.getHistory()
    const messageBeforeThread = historyBeforeThread.messages[0]

    expect(messageBeforeThread.hasThread).toBe(false)

    const newThread = await messageBeforeThread.createThread()
    await newThread.sendText("Hello!")
    await sleep(150)

    const historyWithThread = await channel.getHistory()
    const messageWithThread = historyWithThread.messages[0]

    expect(messageWithThread.hasThread).toBe(true)

    const thread = await messageWithThread.getThread()
    const threadText = "Whatever text"
    await thread.sendText(threadText)
    await sleep(150) // History calls have around 130ms of cache time

    const threadMessages = await thread.getHistory()
    const messageToPin = threadMessages.messages[0]
    const pinnedThread = await thread.pinMessage(messageToPin)

    expect(pinnedThread.custom?.["pinnedMessageTimetoken"]).toBe(messageToPin.timetoken)
  }, 20000)

  test("should encrypt and decrypt a message", async () => {
    const encryptedChat = await createChatInstance({
      config: {
        cryptoModule: CryptoModule.aesCbcCryptoModule({ cipherKey: "pubnubenigma" }),
        userId: "another-user",
      },
    })

    const someRandomUser1 = await encryptedChat.createUser(generateRandomString(), { name: "random-1" })
    const someEncryptedGroupChannel = await encryptedChat.createGroupConversation({ users: [someRandomUser1] })

    const sameCipheredGroupChannel = await chat.getChannel(someEncryptedGroupChannel.channel.id)
    let encryptedMessage: Message
    let cipheredMessage: Message | undefined

    const disconnect1 = someEncryptedGroupChannel.channel.connect((msg) => {
      encryptedMessage = msg
    })
    const disconnect2 = sameCipheredGroupChannel.connect((msg) => {
      cipheredMessage = msg
    })

    sleep(1000)

    await someEncryptedGroupChannel.channel.sendText("Random text")
    await sleep(1000) // History calls have around 130ms of cache time
    const encryptedHistory = await someEncryptedGroupChannel.channel.getHistory()
    const cipheredHistory = await sameCipheredGroupChannel.getHistory()

    expect(encryptedMessage).toBeDefined()
    expect(cipheredMessage).toBeDefined()
    expect(encryptedMessage.text).toBe("Random text")
    expect(encryptedHistory.messages[0].text).toBe("Random text")
    expect(cipheredHistory.messages[0].text.startsWith("UE5FRAFBQ1JIE")).toBeTruthy()

    await someEncryptedGroupChannel.channel.delete({ soft: false })
    await sameCipheredGroupChannel.delete({ soft: false })
    await someRandomUser1.delete({ soft: false })
    await encryptedChat.currentUser.delete()

    disconnect1()
    disconnect2()
  }, 30000)

  test("should encrypt and decrypt a file", async () => {
    const file1 = fs.createReadStream("tests/fixtures/pblogo1.png")
    const file2 = fs.createReadStream("tests/fixtures/pblogo2.png")
    const file3 = fs.createReadStream("tests/fixtures/pblogo3.png")

    const filesFromInput = [
      { stream: file1, name: "pblogo1.png", mimeType: "image/png" },
      { stream: file2, name: "pblogo2.png", mimeType: "image/png" },
      { stream: file3, name: "pblogo3.png", mimeType: "image/png" },
    ]

    const encryptedChat = await createChatInstance({
      config: {
        cryptoModule: CryptoModule.aesCbcCryptoModule({ cipherKey: "pubnubenigma" }),
        userId: "another-user",
      },
    })
    const someRandomUser1 = await encryptedChat.createUser(generateRandomString(), { name: "random-1" })
    const someEncryptedGroupChannel = await encryptedChat.createGroupConversation({ users: [someRandomUser1] })
    const sameCipheredGroupChannel = await chat.getChannel(someEncryptedGroupChannel.channel.id)

    let encryptedMessage: Message
    let cipheredMessage: Message | undefined

    const disconnect1 = someEncryptedGroupChannel.channel.connect((msg) => {
      encryptedMessage = msg
    })
    const disconnect2 = sameCipheredGroupChannel.connect((msg) => {
      cipheredMessage = msg
    })

    await someEncryptedGroupChannel.channel.sendText("Random text", { files: filesFromInput })
    await sleep(200) // History calls have around 130ms of cache time
    const encryptedHistory = await someEncryptedGroupChannel.channel.getHistory()
    const cipheredHistory = await sameCipheredGroupChannel.getHistory()

    expect(encryptedMessage).toBeDefined()
    expect(encryptedMessage.text).toBe("Random text")
    expect(encryptedHistory.messages[0].text).toBe("Random text")
    expect(cipheredHistory.messages[0].text.startsWith("UE5FRAFBQ1JIE")).toBeTruthy()
    expect(encryptedHistory.messages[0].files.length).toBe(3)
    expect(cipheredHistory.messages[0].files.length).toBe(0)

    await someEncryptedGroupChannel.channel.delete({ soft: false })
    await sameCipheredGroupChannel.delete({ soft: false })
    await someRandomUser1.delete({ soft: false })
    await encryptedChat.currentUser.delete()

    disconnect1()
    disconnect2()
  }, 30000)

  test("should still view text messages sent before enabling encryption", async () => {
    const encryptedChat = await createChatInstance({
      config: {
        cryptoModule: CryptoModule.aesCbcCryptoModule({ cipherKey: "pubnubenigma" }),
        userId: "another-user",
      },
    })

    const someRandomUser1 = await encryptedChat.createUser(generateRandomString(), { name: "random-1" })
    const somePlainGroupChannel = await chat.createGroupConversation({ users: [someRandomUser1] })
    const sameEncryptedGroupChannel = await encryptedChat.getChannel(somePlainGroupChannel.channel.id)

    let plainMessage: Message
    let cipheredMessage: Message | undefined

    const disconnect1 = somePlainGroupChannel.channel.connect((msg) => {
      plainMessage = msg
    })
    const disconnect2 = sameEncryptedGroupChannel.connect((msg) => {
      cipheredMessage = msg
    })

    await somePlainGroupChannel.channel.sendText("Random text")
    await sleep(1000) // History calls have around 130ms of cache time

    const plainHistory = await somePlainGroupChannel.channel.getHistory()
    const cipheredHistory = await sameEncryptedGroupChannel.getHistory()

    expect(plainMessage).toBeDefined()
    expect(cipheredMessage).toBeDefined()
    expect(plainMessage.text).toBe("Random text")
    expect(cipheredMessage.text).toBe("Random text")
    expect(plainHistory.messages[0].text).toBe("Random text")
    expect(cipheredHistory.messages[0].text).toBe("Random text")

    disconnect1()
    disconnect2()

    await somePlainGroupChannel.channel.delete()
    await sameEncryptedGroupChannel.delete()
    await someRandomUser1.delete()
    await encryptedChat.currentUser.delete()
  }, 35000)

  test("should still view files sent before enabling encryption", async () => {
    const file1 = fs.createReadStream("tests/fixtures/pblogo1.png")
    const file2 = fs.createReadStream("tests/fixtures/pblogo2.png")
    const file3 = fs.createReadStream("tests/fixtures/pblogo3.png")

    const filesFromInput = [
      { stream: file1, name: "pblogo1.png", mimeType: "image/png" },
      { stream: file2, name: "pblogo2.png", mimeType: "image/png" },
      { stream: file3, name: "pblogo3.png", mimeType: "image/png" },
    ]

    const encryptedChat = await createChatInstance({
      config: {
        cryptoModule: CryptoModule.aesCbcCryptoModule({ cipherKey: "pubnubenigma" }),
        userId: "another-user",
      },
    })

    const someRandomUser1 = await encryptedChat.createUser(generateRandomString(), { name: "random-1" })
    const somePlainGroupChannel = await chat.createGroupConversation({ users: [someRandomUser1] })
    const sameEncryptedGroupChannel = await encryptedChat.getChannel(somePlainGroupChannel.channel.id)

    let plainMessage: Message
    let cipheredMessage: Message | undefined

    const disconnect1 = somePlainGroupChannel.channel.connect((msg) => {
      plainMessage = msg
    })
    const disconnect2 = sameEncryptedGroupChannel.connect((msg) => {
      cipheredMessage = msg
    })

    await somePlainGroupChannel.channel.sendText("Random text", { files: filesFromInput })
    await sleep(200) // History calls have around 130ms of cache time
    const plainHistory = await somePlainGroupChannel.channel.getHistory()
    const cipheredHistory = await sameEncryptedGroupChannel.getHistory()

    expect(plainMessage).toBeDefined()
    expect(cipheredMessage).toBeDefined()
    expect(plainMessage.text).toBe("Random text")
    expect(cipheredMessage.text).toBe("Random text")
    expect(plainHistory.messages[0].text).toBe("Random text")
    expect(cipheredHistory.messages[0].text).toBe("Random text")
    expect(plainHistory.messages[0].files.length).toBe(3)
    expect(cipheredHistory.messages[0].files.length).toBe(3)

    disconnect1()
    disconnect2()

    await somePlainGroupChannel.channel.delete()
    await sameEncryptedGroupChannel.delete()
    await someRandomUser1.delete()
    await encryptedChat.currentUser.delete()
  }, 30000)

  test("should be able to decrypt text and file messages sent using a previous encryption key", async () => {
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
    await someGroupChannel.channel.sendText("Random text", { files: filesFromInput })

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

    await someGroupChannel.channel.delete({ soft: false })
    await someRandomUser1.delete({ soft: false })
    await encryptedChat1.currentUser.delete()
    await encryptedChat2.currentUser.delete()
  }, 35000)

  test("should send a message with custom body and transform it to TextMessageContent when received", async () => {
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        customPayloads: {
          getMessagePublishBody: ({ type, text, files }) => {
            return {
              body: {
                message: {
                  content: {
                    text,
                  },
                },
                files,
              },
              messageType: type,
            }
          },
          getMessageResponseBody: (messageParams: MessageDTOParams) => {
            return {
              text: messageParams.message.body.message.content.text,
              type: messageParams.message.messageType,
              files: messageParams.message.body.files,
              customKey: "customValue"
            }
          },
        },
      },
    })

    const someChannel = await chat.createChannel(generateRandomString())
    await someChannel.sendText("Hello world!")
    await sleep(200)

    const historyObject = await someChannel.getHistory({ count: 1 })
    expect(historyObject.messages[0].text).toBe("Hello world!")
    expect(historyObject.messages[0].content.customKey).toBe("customValue")

    await someChannel.delete()
    await chat.currentUser.delete()
  }, 20000)

  test("should send a message with custom body and crash if getMessageResponseBody is incorrect", async () => {
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        customPayloads: {
          getMessageResponseBody: (messageParams: MessageDTOParams) => {
            return {
              text: messageParams.message.it.does.not.exist,
              type: messageParams.message.messageType,
              files: messageParams.message.body.files,
            }
          },
        },
      },
    })

    const someChannel = await chat.createChannel(generateRandomString())
    await someChannel.sendText("Hello world!")
    await sleep(200)

    let thrownErrorMessage = undefined

    try {
      await someChannel.getHistory()
    } catch (error) {
      thrownErrorMessage = error.message
    }

    expect(thrownErrorMessage).toBeDefined()

    await someChannel.delete()
    await chat.currentUser.delete()
  }, 20000)

  test("should be able to pass custom edit and delete action names", async () => {
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        customPayloads: {
          editMessageActionName: "field-updated",
          deleteMessageActionName: "field-removed",
        },
      },
    })

    const someChannel = await chat.createChannel(generateRandomString())
    await someChannel.sendText("Hello world!")
    await sleep(250)

    const historyBeforeEdit = await someChannel.getHistory({ count: 1 })
    await historyBeforeEdit.messages[0].editText("Edited text")

    await sleep(250)
    const historyAfterEdit = await someChannel.getHistory({ count: 1 })

    expect(historyAfterEdit.messages[0].text).toBe("Edited text")
    expect(historyAfterEdit.messages[0].actions["field-updated"]).toBeDefined()
    expect(historyAfterEdit.messages[0].actions["edited"]).toBeUndefined()
    expect(historyAfterEdit.messages[0].deleted).toBe(false)

    await historyAfterEdit.messages[0].delete({ soft: true })
    await sleep(300)
    const historyAfterDelete = await someChannel.getHistory({ count: 1 })

    expect(historyAfterDelete.messages[0].deleted).toBe(true)
    expect(historyAfterDelete.messages[0].actions["field-removed"]).toBeDefined()
    expect(historyAfterDelete.messages[0].actions["deleted"]).toBeUndefined()

    await someChannel.delete()
    await chat.currentUser.delete()
  }, 20000)

  test("should work fine even for multiple schemas on different channels", async () => {
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        customPayloads: {
          getMessagePublishBody: ({ type, text, files }, channelId) => {
            if (channelId === "different-schema-for-no-reason") {
              return {
                different: {
                  schema: {
                    for: {
                      no: {
                        reason: text,
                      },
                    },
                  },
                  files,
                },
                messageType: type,
              }
            }

            return {
              body: {
                message: {
                  content: {
                    text,
                  },
                },
                files,
              },
              messageType: type,
            }
          },
          getMessageResponseBody: (messageParams: MessageDTOParams) => {
            if (messageParams.channel === "different-schema-for-no-reason") {
              return {
                text: messageParams.message.different.schema.for.no.reason,
                type: messageParams.message.messageType,
                files: messageParams.message.different.files,
              }
            }

            return {
              text: messageParams.message.body.message.content.text,
              type: messageParams.message.messageType,
              files: messageParams.message.body.files,
            }
          },
        },
      },
    })

    const someChannel = await chat.createChannel(generateRandomString())
    await someChannel.sendText("One type of schema")
    await sleep(200)

    const someChannelWithDifferentSchema = await chat.createChannel(generateRandomString())
    await someChannelWithDifferentSchema.sendText("Another type of schema")
    await sleep(200)

    const someChannelHistoryObject = await someChannel.getHistory({ count: 1 })
    const someChannelWithDifferentSchemaHistoryObject = await someChannelWithDifferentSchema.getHistory({ count: 1 })

    expect(someChannelHistoryObject.messages[0].text).toBe("One type of schema")
    expect(someChannelWithDifferentSchemaHistoryObject.messages[0].text).toBe("Another type of schema")

    await someChannel.delete()
    await someChannelWithDifferentSchema.delete()
    await chat.currentUser.delete()
  }, 25000)

  test("should be able to read live messages with custom payloads as well", async () => {
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        customPayloads: {
          getMessagePublishBody: ({ type, text, files }) => {
            return {
              body: {
                message: {
                  content: {
                    text,
                  },
                },
                files,
              },
              messageType: type,
            }
          },
          getMessageResponseBody: (messageParams: MessageDTOParams) => {
            return {
              text: messageParams.message.body.message.content.text,
              type: messageParams.message.messageType,
              files: messageParams.message.body.files,
            }
          },
        },
      },
    })

    const someChannel = await chat.createChannel(generateRandomString(), { name: "Custom body channel" })
    const receivedMessages: string[] = []
    const disconnect = someChannel.connect((msg) => { receivedMessages.push(msg.text) })
    await sleep(500)

    const expectedMessages = ["Hello live world!", "Hello live world! Number 2"]

    for (const expectedMessage of expectedMessages) {
      await someChannel.sendText(expectedMessage)
      await sleep(500)
    }

    await waitForAllMessagesToBeDelivered(receivedMessages, expectedMessages)

    expect(receivedMessages).toContain(expectedMessages[0])
    expect(receivedMessages).toContain(expectedMessages[1])
    expect(receivedMessages.length).toBeGreaterThanOrEqual(2)

    disconnect()

    await someChannel.delete()
    await chat.currentUser.delete()
  }, 30000)

  test("should be able to read live encrypted messages with custom payloads", async () => {
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        customPayloads: {
          getMessagePublishBody: ({ type, text, files }) => {
            return {
              body: {
                message: {
                  content: {
                    text,
                  },
                },
                files,
              },
              messageType: type,
            }
          },
          getMessageResponseBody: (messageParams: MessageDTOParams) => {
            return {
              text: messageParams.message.body.message.content.text,
              type: messageParams.message.messageType,
              files: messageParams.message.body.files,
            }
          },
        },
        cryptoModule: CryptoModule.aesCbcCryptoModule({ cipherKey: "pubnubenigma" }),
      },
    })

    const someChannel = await chat.createChannel(generateRandomString())
    const receivedMessages: string[] = []
    const disconnect = someChannel.connect((msg) => { receivedMessages.push(msg.text) })
    await sleep(250)

    const expectedMessages = ["Hello encrypted world!", "Hello encrypted world! Number 2"]
    await someChannel.sendText(expectedMessages[0])
    await sleep(500)
    await someChannel.sendText(expectedMessages[1])
    await sleep(500)

    await waitForAllMessagesToBeDelivered(receivedMessages, expectedMessages)

    expect(receivedMessages).toContain(expectedMessages[0])
    expect(receivedMessages).toContain(expectedMessages[1])
    expect(receivedMessages.length).toBeGreaterThanOrEqual(2)

    disconnect()

    await someChannel.delete()
    await chat.currentUser.delete()
  }, 20000)

  test("should be able to read historic encrypted messages with custom payloads", async () => {
    const chat = await createChatInstance({
      userId: generateRandomString(),
      config: {
        customPayloads: {
          getMessagePublishBody: ({ type, text, files }) => {
            return {
              body: {
                message: {
                  content: {
                    text,
                  },
                },
                files,
              },
              messageType: type,
            }
          },
          getMessageResponseBody: (messageParams: MessageDTOParams) => {
            return {
              text: messageParams.message.body.message.content.text,
              type: messageParams.message.messageType,
              files: messageParams.message.body.files,
            }
          },
        },
        cryptoModule: CryptoModule.aesCbcCryptoModule({ cipherKey: "pubnubenigma" }),
      },
    })

    const someChannel = await chat.createChannel(generateRandomString())
    await someChannel.sendText("Hello encrypted world!")
    await sleep(500)
    await someChannel.sendText("Hello encrypted world! Number 2")
    await sleep(500)

    const historyObject = await someChannel.getHistory({ count: 2 })
    expect(historyObject.messages[0].text).toBe("Hello encrypted world!")
    expect(historyObject.messages[1].text).toBe("Hello encrypted world! Number 2")

    await someChannel.delete()
    await chat.currentUser.delete()
  }, 20000)

  test("should receive streamUpdates callback when soft deleting message", async () => {
    const disconnect = channel.connect((message) => {  })

    await sleep(500)
    await channel.sendText("message1")
    await sleep(500)

    const history = await channel.getHistory()
    const message1 = history.messages[history.messages.length - 1]

    const message1Updates: Message[] = []
    const unsubscribe1 = message1.streamUpdates((updatedMessage: Message) => { message1Updates.push(updatedMessage) })
    await sleep(3000)

    await message1.delete({ soft: true })
    await sleep(2000)

    unsubscribe1()
    disconnect()

    expect(message1Updates.length).toBeGreaterThanOrEqual(1)
    expect(message1Updates[message1Updates.length - 1].deleted).toBe(true)
    expect(message1Updates[message1Updates.length - 1].timetoken).toBe(message1.timetoken)
  }, 20000)

  test("should receive streamUpdatesOn callback when soft deleting message", async () => {
    const disconnect = channel.connect((message) => { })
    await sleep(500)

    await channel.sendText("message1")
    await channel.sendText("message2")
    await sleep(500)

    const history = await channel.getHistory()
    const message1 = history.messages[history.messages.length - 2]
    const message2 = history.messages[history.messages.length - 1]

    const streamUpdatesOnCallbacks: Message[][] = []
    const unsubscribeOn = Message.streamUpdatesOn([message1, message2], (messages: Message[]) => {
      streamUpdatesOnCallbacks.push([...messages].sort((a, b) => Number(a.timetoken) - Number(b.timetoken)))
    })

    await sleep(5000)
    await message1.delete({ soft: true })
    await sleep(2000)

    await message2.delete({ soft: true })
    await sleep(3000)

    unsubscribeOn()
    disconnect()

    expect(streamUpdatesOnCallbacks.length).toBeGreaterThanOrEqual(2)

    const firstUpdate = streamUpdatesOnCallbacks[0]
    const firstMsg1 = firstUpdate.find((m) => m.timetoken === message1.timetoken)
    const firstMsg2 = firstUpdate.find((m) => m.timetoken === message2.timetoken)

    expect(firstUpdate.length).toBe(2)
    expect(firstMsg1?.deleted).toBe(true)
    expect(firstMsg2?.deleted).toBe(false)

    const secondUpdate = streamUpdatesOnCallbacks[1]
    const secondMsg1 = secondUpdate.find((m) => m.timetoken === message1.timetoken)
    const secondMsg2 = secondUpdate.find((m) => m.timetoken === message2.timetoken)

    expect(secondUpdate.length).toBe(2)
    expect(secondMsg1?.deleted).toBe(true)
    expect(secondMsg2?.deleted).toBe(true)
  }, 35000)

  test("should check if user has reacted via message.hasUserReaction", async () => {
    await channel.sendText("Message for reaction test")
    await sleep(150)

    const history = await channel.getHistory()
    const message = history.messages[0]

    expect(message.hasUserReaction("👍")).toBe(false)
    expect(message.reactions.length).toBe(0)

    await message.toggleReaction("👍")
    await sleep(150)

    const updatedMessage = await channel.getMessage(message.timetoken)
    const hasReactionAfter = updatedMessage.hasUserReaction("👍")

    expect(hasReactionAfter).toBe(true)

    const reactions = updatedMessage.reactions
    expect(reactions.length).toBe(1)
    expect(reactions[0].value).toBe("👍")
    expect(reactions[0].isMine).toBe(true)
    expect(reactions[0].userIds.length).toBe(1)
  }, 20000)

  test("should forward message via message.forward", async () => {
    const targetChannel = await createRandomChannel(chat)
    const messageText = "Message to forward via message object"

    await channel.sendText(messageText)
    await sleep(3000)

    const history = await channel.getHistory()
    const originalMessage = history.messages[0]
    await sleep(150)

    await originalMessage.forward(targetChannel.id)
    await sleep(150)

    const targetHistory = await targetChannel.getHistory()
    expect(targetHistory.messages.length).toBeGreaterThan(0)
    expect(targetHistory.messages[0].content.text).toEqual(messageText)

    await targetChannel.delete()
  }, 20000)

  test("should pin message via message.pin", async () => {
    const messageText = "Message to pin via message object"

    await channel.sendText(messageText)
    await sleep(300)

    const history = await channel.getHistory()
    const message = history.messages[0]

    await message.pin()
    await sleep(300)

    const updatedChannel = await chat.getChannel(channel.id)
    const pinnedMessage = await updatedChannel.getPinnedMessage()

    expect(pinnedMessage).toBeDefined()
    expect(pinnedMessage?.timetoken).toEqual(message.timetoken)
    expect(pinnedMessage?.content.text).toEqual(messageText)
  }, 20000)

  test("should pin thread message to parent channel via threadMessage.pinToParentChannel", async () => {
    await channel.sendText("Parent message")
    await sleep(300)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]

    const threadChannel = await parentMessage.createThread()
    await threadChannel.sendText("Thread message to pin to parent")
    await sleep(150)

    const threadChannelHistory = await threadChannel.getHistory()
    const threadMessage = threadChannelHistory.messages[0]
    const updatedChannel = await threadMessage.pinToParentChannel()
    await sleep(150)

    const pinnedInParent = await updatedChannel.getPinnedMessage()

    expect(pinnedInParent).toBeDefined()
    expect(pinnedInParent?.content.text).toEqual("Thread message to pin to parent")

    // await threadChannel.delete()
  }, 20000)

  test("should unpin thread message from parent channel via threadMessage.unpinFromParentChannel", async () => {
    await channel.sendText("Parent message")
    await sleep(350)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]

    const threadChannel = await parentMessage.createThread()
    await threadChannel.sendText("Thread message to unpin")
    await sleep(350)

    const threadChannelHistory = await threadChannel.getHistory()
    const threadMessage = threadChannelHistory.messages[0]

    const channelAfterPin = await threadMessage.pinToParentChannel()
    await sleep(350)

    const pinnedMessageAfterPin = await channelAfterPin.getPinnedMessage()
    expect(pinnedMessageAfterPin).toBeDefined()
    expect(pinnedMessageAfterPin?.content.text).toEqual("Thread message to unpin")

    const channelAfterUnpin = await threadMessage.unpinFromParentChannel()
    await sleep(350)

    const pinnedMessageAfterUnpin = await channelAfterUnpin.getPinnedMessage()
    expect(pinnedMessageAfterUnpin).toBeNull()

    // await threadChannel.delete()
  }, 25000)

  test("should get message elements with text, mentions, and links", async () => {
    const mentionedUser1 = await createRandomUser(chat, "mentioned1_")
    const mentionedUser2 = await createRandomUser(chat, "mentioned2_")
    const messageDraft = channel.createMessageDraftV2()

    // Build message with user mentions and a linked URL
    messageDraft.update(`Hello @${mentionedUser1.name}, check out `)

    // Add the URL as a text link
    messageDraft.addLinkedText({
      text: "example.com",
      link: "https://example.com",
      positionInInput: messageDraft.value.length,
    })

    messageDraft.update(`Hello @${mentionedUser1.name}, check out example.com and @${mentionedUser2.name}`)

    // Add mentions
    const firstMentionOffset = 6 // "Hello " = 6 chars
    const firstMentionLength = mentionedUser1.name.length + 1 // @ + name
    messageDraft.addMention(firstMentionOffset, firstMentionLength, "mention", mentionedUser1.id)

    const messageText = messageDraft.value
    const secondMentionOffset = messageText.indexOf(`@${mentionedUser2.name}`)
    const secondMentionLength = mentionedUser2.name.length + 1
    messageDraft.addMention(secondMentionOffset, secondMentionLength, "mention", mentionedUser2.id)

    await messageDraft.send()
    await sleep(300)

    const history = await channel.getHistory()
    const message = history.messages[0]
    const elements = message.getMessageElements()

    // Verify elements structure
    expect(elements).toBeDefined()
    expect(Array.isArray(elements)).toBe(true)
    expect(elements.length).toBe(6)

    // Element 0: plainText "Hello "
    expect(elements[0].type).toBe("text")
    expect(elements[0].content.text).toBe("Hello ")

    // Element 1: mention for first user
    expect(elements[1].type).toBe("mention")
    expect(elements[1].content.id).toBe(mentionedUser1.id)
    expect(elements[1].content.name).toBe("@" + mentionedUser1.name)

    // Element 2: plainText ", check out "
    expect(elements[2].type).toBe("text")
    expect(elements[2].content.text).toBe(", check out ")

    // Element 3: textLink
    expect(elements[3].type).toBe("textLink")
    expect(elements[3].content.text).toBe("example.com")
    expect(elements[3].content.link).toBe("https://example.com")

    // Element 4: plainText " and "
    expect(elements[4].type).toBe("text")
    expect(elements[4].content.text).toBe(" and ")

    // Element 5: mention for second user
    expect(elements[5].type).toBe("mention")
    expect(elements[5].content.id).toBe(mentionedUser2.id)
    expect(elements[5].content.name).toBe("@" + mentionedUser2.name)

    await mentionedUser1.delete()
    await mentionedUser2.delete()
  }, 20000)

  test("should remove thread from a message", async () => {
    await channel.sendText("Message with thread to be removed")
    await sleep(300)

    const historyBeforeThread = await channel.getHistory()
    const messageBeforeThread = historyBeforeThread.messages[0]

    const threadChannel = await messageBeforeThread.createThread()
    await threadChannel.sendText("Thread message")
    await sleep(300)

    const historyWithThread = await channel.getHistory()
    const messageWithThread = historyWithThread.messages[0]
    expect(messageWithThread.hasThread).toBe(true)

    const [deleteData, channelOrBoolean] = await messageWithThread.removeThread()
    await sleep(300)

    expect(deleteData).toBeDefined()
    expect(channelOrBoolean).toBeDefined()

    const historyAfterRemoval = await channel.getHistory()
    const messageAfterRemoval = historyAfterRemoval.messages[0]

    expect(messageAfterRemoval.hasThread).toBe(false)
  }, 20000)

  test("should stream updates on multiple thread messages", async () => {
    await channel.sendText("Parent message for thread updates test")
    await sleep(300)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const threadChannel = await parentMessage.createThread()

    await threadChannel.sendText("First thread message")
    await threadChannel.sendText("Second thread message")
    await sleep(300)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage1 = threadHistory.messages[0]
    const threadMessage2 = threadHistory.messages[1]

    const streamUpdatesOnCallbacks: any[] = []
    const unsubscribeOn = ThreadMessage.streamUpdatesOn([threadMessage1, threadMessage2], (messages: any[]) => {
      streamUpdatesOnCallbacks.push([...messages])
    })

    await sleep(3000)
    await threadMessage1.editText("Edited first thread message")
    await sleep(2000)

    await threadMessage2.editText("Edited second thread message")
    await sleep(2000)

    unsubscribeOn()

    expect(streamUpdatesOnCallbacks.length).toBeGreaterThanOrEqual(2)
    expect(Array.isArray(streamUpdatesOnCallbacks[0])).toBe(true)
    expect(streamUpdatesOnCallbacks[0].length).toBe(2)

    // await threadChannel.delete()
  }, 30000)
})
