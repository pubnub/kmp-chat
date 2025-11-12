import {Chat, Channel, User} from "../dist-test"
import {createChatInstance, generateRandomString, makeid, sleep} from "./utils"

describe("Membership test", () => {
  let chat: Chat
  let channel: Channel
  let user: User

  function createRandomChannel(prefix: string = "") {
    return chat.createChannel(`${prefix}channel_${makeid()}`, {
      name: `${prefix}Test Channel`,
      description: "This is a test channel",
    })
  }

  function createRandomUser(prefix: string = "") {
    return chat.createUser(`${prefix}user_${makeid()}`, {
      name: `${prefix}Test User`,
    })
  }

  beforeAll(async () => {
    chat = await createChatInstance({ shouldCreateNewInstance: true, userId: generateRandomString(8) })
  })

  beforeEach(async () => {
    channel = await createRandomChannel()
    user = await createRandomUser()
  })

  afterEach(async () => {
    await Promise.all([
      channel?.delete({ soft: false }),
      user?.delete(),
      chat.currentUser.delete({ soft: false })
    ])
  })

  test("should get membership via channel.invite", async () => {
    const membership = await channel.invite(user)
    await sleep(150)

    expect(membership).toBeDefined()
    expect(membership.user.id).toEqual(user.id)
    expect(membership.channel.id).toEqual(channel.id)
  }, 20000)

  test("should access lastReadMessageTimetoken property via membership.lastReadMessageTimetoken", async () => {
    const membership = await channel.invite(user)
    await sleep(150)

    await channel.sendText("Test message")
    await sleep(350)

    const history = await channel.getHistory()
    const message = history.messages[0]

    const updatedMembership = await membership.setLastReadMessage(message)
    const lastReadTimetoken = updatedMembership.lastReadMessageTimetoken

    expect(lastReadTimetoken).toBeDefined()
    expect(lastReadTimetoken).toEqual(message.timetoken)
  }, 20000)
})
