import {
  Channel,
  ThreadMessage,
  Chat,
} from "../dist-test"
import {
  createChatInstance,
  generateRandomString,
  sleep,
  createRandomChannel,
} from "./utils"

describe("ThreadMessage test", () => {
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
    chat.destroy()

    jest.clearAllMocks()
  }, 15000)

  test("should return ThreadMessage from editText", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]

    const newText = "Edited thread reply"
    const editedMessage = await threadMessage.editText(newText)

    expect(editedMessage).toBeInstanceOf(ThreadMessage)
    expect(editedMessage.text).toBe(newText)
    expect(editedMessage.parentChannelId).toBe(channel.id)
    expect(editedMessage.timetoken).toBe(threadMessage.timetoken)
  }, 30000)

  test("should return ThreadMessage from toggleReaction", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const reaction = ":+1"
    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]
    const messageWithReaction = await threadMessage.toggleReaction(reaction)

    expect(messageWithReaction).toBeInstanceOf(ThreadMessage)
    expect(messageWithReaction.hasUserReaction(reaction)).toBe(true)
    expect(messageWithReaction.parentChannelId).toBe(channel.id)
    expect(messageWithReaction.timetoken).toBe(threadMessage.timetoken)

    const messageWithoutReaction = await messageWithReaction.toggleReaction(reaction)

    expect(messageWithoutReaction).toBeInstanceOf(ThreadMessage)
    expect(messageWithoutReaction.hasUserReaction(reaction)).toBe(false)
    expect(messageWithoutReaction.parentChannelId).toBe(channel.id)
  }, 30000)

  test("should return ThreadMessage from restore", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]

    await threadMessage.delete({ soft: true })
    await sleep(150)

    const historyAfterDelete = await threadChannel.getHistory()
    const deletedMessage = historyAfterDelete.messages.find((m) => m.timetoken === threadMessage.timetoken)
    const restoredMessage = await deletedMessage.restore()

    expect(restoredMessage).toBeInstanceOf(ThreadMessage)
    expect(restoredMessage.deleted).toBe(false)
    expect(restoredMessage.content.text).toBe("Thread reply")
    expect(restoredMessage.parentChannelId).toBe(channel.id)
    expect(restoredMessage.timetoken).toBe(threadMessage.timetoken)
  }, 30000)

  test("should pin thread message to parent channel via pinToParentChannel", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]
    const updatedParentChannel = await threadMessage.pinToParentChannel()

    expect(updatedParentChannel).toBeInstanceOf(Channel)
    const pinnedMessage = await updatedParentChannel.getPinnedMessage()
    expect(pinnedMessage).toBeDefined()
    expect(pinnedMessage.content.text).toBe("Thread reply")
  }, 30000)

  test("should unpin thread message from parent channel via unpinFromParentChannel", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]

    await threadMessage.pinToParentChannel()
    await sleep(150)
    const updatedParentChannel = await threadMessage.unpinFromParentChannel()

    expect(updatedParentChannel).toBeInstanceOf(Channel)
    expect(await updatedParentChannel.getPinnedMessage()).toBeNull()
  }, 30000)

  test("should receive updates via threadMessage.onThreadMessageUpdated", async () => {
    await channel.sendText("Parent message")
    await sleep(150)

    const history = await channel.getHistory()
    const parentMessage = history.messages[0]
    const result = await parentMessage.createThreadWithResult("Thread reply")
    const threadChannel = result.threadChannel
    await sleep(150)

    const threadHistory = await threadChannel.getHistory()
    const threadMessage = threadHistory.messages[0]

    let updatedMsg: any

    const stop = threadMessage.onThreadMessageUpdated((msg) => { updatedMsg = msg })
    await sleep(2000)

    const newText = "Edited thread reply"
    await threadMessage.editText(newText)
    await sleep(1000)

    expect(updatedMsg).toBeDefined()
    expect(updatedMsg.text).toBe(newText)
    expect(updatedMsg.parentChannelId).toBe(channel.id)
    expect(updatedMsg.timetoken).toBe(threadMessage.timetoken)

    stop()
  }, 30000)
})
