import { Chat, Channel, User, Membership } from "../dist-test"
import { createChatInstance, createRandomChannel, createRandomUser, sleep } from "./utils"

describe("Membership test", () => {
  let chat: Chat
  let channel: Channel
  let user: User

  beforeAll(async () => {
    chat = await createChatInstance()
  })

  beforeEach(async () => {
    channel = await createRandomChannel()
    user = await createRandomUser()
  })

  afterEach(async () => {
    await Promise.all([
      channel?.delete({ soft: false }),
      user?.delete()
    ])
  })

  test("should get membership via user.getMembership", async () => {
    await channel.invite(user)
    await sleep(150)

    const membership = await user.getMembership(channel.id)

    expect(membership).toBeDefined()
    expect(membership.user.id).toEqual(user.id)
    expect(membership.channel.id).toEqual(channel.id)
  })

  test("should access lastReadMessageTimetoken property via membership.lastReadMessageTimetoken", async () => {
    await channel.invite(user)
    await sleep(150)

    const message = await channel.sendText("Test message")
    await sleep(150)

    const membership = await user.getMembership(channel.id)
    await membership.setLastReadMessage(message)
    await sleep(150)

    const updatedMembership = await user.getMembership(channel.id)
    const lastReadTimetoken = updatedMembership.lastReadMessageTimetoken

    expect(lastReadTimetoken).toBeDefined()
    expect(lastReadTimetoken).toEqual(message.timetoken)
  })
})
