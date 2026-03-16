import {
  Channel,
  ThreadChannel,
  ThreadMessage,
  Chat,
} from "../dist-test"
import {
  createChatInstance,
  generateRandomString,
  sleep,
  createRandomChannel,
} from "./utils"

describe("ThreadChannel test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let channel: Channel

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    channel = await createRandomChannel(chat)
  }, 15000)

  afterEach(async () => {
    await channel.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()

    jest.clearAllMocks()
  }, 15000)

  test("should receive thread messages via threadChannel.onThreadMessageReceived", async () => {
    await channel.sendText("Parent message")
    await sleep(150)
    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const threadChannel = await parentMessage.createThread()

    const receivedMessages: any[] = []
    const stop = threadChannel.onThreadMessageReceived((msg) => {
      receivedMessages.push(msg)
    })
    await sleep(2000)

    await threadChannel.sendText("Thread reply")
    await sleep(1000)

    expect(receivedMessages.length).toBe(1)
    expect(receivedMessages[0].content.text).toBe("Thread reply")
    expect(receivedMessages[0].parentChannelId).toBeDefined()

    stop()
  }, 30000)

  test("should receive thread channel updates via threadChannel.onThreadChannelUpdated", async () => {
    await channel.sendText("Parent message")
    await sleep(150)
    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Initial thread message")
    const threadChannel = result.threadChannel

    let updatedTC: any
    const stop = threadChannel.onThreadChannelUpdated((tc) => { updatedTC = tc })
    await sleep(1500)

    await threadChannel.update({ name: "Updated Thread" })
    await sleep(500)

    expect(updatedTC).toBeDefined()
    expect(updatedTC.name).toEqual("Updated Thread")
    stop()
  }, 30000)

  test("should return ThreadChannel from update", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel

    const updatedThreadChannel = await threadChannel.update({ description: "Updated description" })

    expect(updatedThreadChannel).toBeInstanceOf(ThreadChannel)
    expect(updatedThreadChannel.description).toEqual("Updated description")
    expect(updatedThreadChannel.parentChannelId).toBe(channel.id)
  }, 30000)

  test("should return ThreadMessages from getHistory", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()

    expect(threadHistory.messages.length).toBeGreaterThan(0)
    const threadMessage = threadHistory.messages[0]
    expect(threadMessage).toBeInstanceOf(ThreadMessage)
    expect(threadMessage.content.text).toBe("Thread reply")
    expect(threadMessage.parentChannelId).toBe(channel.id)
  }, 30000)

  test("should return ThreadMessage from getMessage", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const firstThreadMessage = threadHistory.messages[0]
    const retrievedMessage = await threadChannel.getMessage(firstThreadMessage.timetoken)

    expect(retrievedMessage).toBeInstanceOf(ThreadMessage)
    expect(retrievedMessage.content.text).toBe("Thread reply")
    expect(retrievedMessage.parentChannelId).toBe(channel.id)
  }, 30000)

  test("should return ThreadChannel from pinMessage and ThreadMessage from getPinnedMessage", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]
    const updatedThreadChannel = await threadChannel.pinMessage(threadMessage)

    expect(updatedThreadChannel).toBeInstanceOf(ThreadChannel)
    const pinnedMessage = await updatedThreadChannel.getPinnedMessage()
    expect(pinnedMessage).toBeDefined()
    expect(pinnedMessage).toBeInstanceOf(ThreadMessage)
    expect(pinnedMessage.timetoken).toBe(threadMessage.timetoken)
    expect(pinnedMessage.content.text).toBe("Thread reply")
  }, 30000)

  test("should return ThreadChannel from unpinMessage", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]

    const pinnedThreadChannel = await threadChannel.pinMessage(threadMessage)
    const unpinnedThreadChannel = await pinnedThreadChannel.unpinMessage()

    expect(unpinnedThreadChannel).toBeInstanceOf(ThreadChannel)
    expect(await unpinnedThreadChannel.getPinnedMessage()).toBeNull()
  }, 30000)

  test("should pin thread message to parent channel", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]
    const updatedParentChannel = await threadChannel.pinMessageToParentChannel(threadMessage)

    expect(updatedParentChannel).toBeInstanceOf(Channel)
    const pinnedMessage = await updatedParentChannel.getPinnedMessage()
    expect(pinnedMessage).toBeDefined()
    expect(pinnedMessage.content.text).toBe("Thread reply")
  }, 30000)

  test("should unpin message from parent channel", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]

    await threadChannel.pinMessageToParentChannel(threadMessage)
    await sleep(150)

    const updatedParentChannel = await threadChannel.unpinMessageFromParentChannel()
    expect(updatedParentChannel).toBeInstanceOf(Channel)
    expect(await updatedParentChannel.getPinnedMessage()).toBeNull()
  }, 30000)
})
