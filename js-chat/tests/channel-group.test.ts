import {
  Channel,
  ChannelGroup,
  Chat,
} from "../dist-test"
import {
  createRandomChannel,
  createChatInstance,
  generateRandomString,
  sleep,
} from "./utils"

describe("Channel group test", () => {
  let chat: Chat
  let firstChannel: Channel
  let secondChannel: Channel
  let channelGroup: ChannelGroup

  beforeAll(async () => {
    chat = await createChatInstance({ userId: generateRandomString(10) })
  })

  beforeEach(async () => {
    firstChannel = await createRandomChannel()
    secondChannel = await createRandomChannel()
    channelGroup = chat.getChannelGroup(generateRandomString(10))
  })

  afterEach(async () => {
    await firstChannel.delete({ soft: false })
    await secondChannel.delete( { soft: false })
    await chat.sdk.channelGroups.deleteGroup({ channelGroup: channelGroup.id })
    await chat.currentUser.delete({ soft: false })
  })

  test("successfully adds channels to a channel group", async () => {
    await channelGroup.addChannels([firstChannel, secondChannel])
    const listChannelsResponse = await channelGroup.listChannels()
    const expectedChannelIds = [firstChannel, secondChannel].map(c => c.id).sort()
    const actualChannelIds = listChannelsResponse.channels.map(c => c.id).sort()

    expect(expectedChannelIds).toEqual(actualChannelIds)
  })

  test("successfully adds channel identifiers to a channel group", async () => {
    await channelGroup.addChannelIdentifiers([firstChannel.id, secondChannel.id])
    const listChannelsResponse = await channelGroup.listChannels()
    const expectedChannelIds = [firstChannel, secondChannel].map(c => c.id).sort()
    const actualChannelIds = listChannelsResponse.channels.map(c => c.id).sort()

    expect(expectedChannelIds).toEqual(actualChannelIds)
  })

  test("successfully removes channels from a channel group", async () => {
    await channelGroup.addChannels([firstChannel, secondChannel])
    const channelListResponse = await channelGroup.listChannels()
    expect(channelListResponse.channels).toHaveLength(2)

    await channelGroup.removeChannels([firstChannel, secondChannel])
    const channelListResponseAfterRemoval = await channelGroup.listChannels()
    expect(channelListResponseAfterRemoval.channels).toHaveLength(0)
  })

  test("successfully removes channel identifiers from a channel group", async () => {
    await channelGroup.addChannels([firstChannel, secondChannel])
    const channelListResponse = await channelGroup.listChannels()
    expect(channelListResponse.channels).toHaveLength(2)

    await channelGroup.removeChannelIdentifiers([firstChannel.id, secondChannel.id])
    const channelListResponseAfterRemoval = await channelGroup.listChannels()
    expect(channelListResponseAfterRemoval.channels).toHaveLength(0)
  })

  test("should receive a message", async () => {
    const messages: string[] = []
    const messagesToSend = ["Some text"]
    await channelGroup.addChannels([firstChannel, secondChannel])

    const disconnect = channelGroup.connect((message) => {
      if (message.content.text !== undefined) {
        messages.push(message.content.text)
      }
    })

    await sleep(1000)

    for (const message of messagesToSend) {
      await firstChannel.sendText(message)
      await sleep(500)
    }

    expect(messages).toStrictEqual(messagesToSend)
    disconnect()
  }, 30000)

  test("get present users", async () => {
    await channelGroup.addChannels([firstChannel, secondChannel])
    const disconnect = firstChannel.connect(() => null)
    const secondDisconnect = secondChannel.connect(() => null)

    await sleep(3000)

    const presenceByChannels = await channelGroup.whoIsPresent()
    expect(presenceByChannels[firstChannel.id]).toEqual([chat.currentUser.id])
    expect(presenceByChannels[secondChannel.id]).toEqual([chat.currentUser.id])

    disconnect()
    secondDisconnect()
  })

  test("presence stream", async () => {
    await channelGroup.addChannels([firstChannel, secondChannel])
    const disconnect = firstChannel.connect(() => null)
    const secondDisconnect = secondChannel.connect(() => null)

    await sleep(3000)

    const presencePromise = new Promise<{ [key: string]: string[] }>((resolve) => {
      channelGroup.streamPresence((presenceByChannels) => {
        const hasFirstChannel = presenceByChannels[firstChannel.id] != null
        const hasSecondChannel = presenceByChannels[secondChannel.id] != null

        if (hasFirstChannel && hasSecondChannel) {
          resolve(presenceByChannels)
        }
      })
    })

    const presenceByChannels = await presencePromise
    expect(presenceByChannels[firstChannel.id]).toEqual([chat.currentUser.id])
    expect(presenceByChannels[secondChannel.id]).toEqual([chat.currentUser.id])

    disconnect()
    secondDisconnect()
  }, 30000)
})