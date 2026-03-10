import {
  Channel,
  ThreadChannel,
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
    const threadChannel = await parentMessage.createThread("Initial thread message")

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
})
