import {
  Channel,
  ChannelGroup,
  Chat,
} from "../dist-test"
import {
  createRandomChannel,
  createChatInstance,
  generateRandomString,
  waitForAllMessagesToBeDelivered,
  sleep,
} from "./utils"

import { jest } from "@jest/globals"

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
    await firstChannel.delete()
    await secondChannel.delete()

    chat.sdk.channelGroups.deleteGroup({
      channelGroup: channelGroup.id,
    })
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

//   test("should receive a message", async () => {
//     const messages: string[] = []
//     const unicodeMessages = ["Some message"]
//
//     const disconnect = channelGroup.connect((message) => {
//       if (message.content.text !== undefined) {
//         messages.push(message.content.text)
//       }
//     })
//     await sleep(200)
//
//     for (const unicodeMessage of unicodeMessages) {
//       await channel.sendText(unicodeMessage)
//       const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))
//       await sleep(2000)
//     }
//
//     await waitForAllMessagesToBeDelivered(messages, unicodeMessages)
//
//     for (const unicodeMessage of unicodeMessages) {
//       expect(messages).toContain(unicodeMessage)
//     }
//
//     disconnect()
//   }, 30000)
})