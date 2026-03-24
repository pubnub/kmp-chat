import {Chat, Channel, User, Membership} from "../dist-test"
import {createChatInstance, generateRandomString, sleep, createRandomChannel, createRandomUser} from "./utils"

describe("Membership test", () => {
  jest.retryTimes(3)

  let chat: Chat
  let channel: Channel
  let user: User

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    channel = await createRandomChannel(chat)
    user = await createRandomUser(chat)
  })

  afterEach(async () => {
    await channel.delete()
    await user.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()

    jest.clearAllMocks()
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

  test("should update membership status, type, and custom data via membership.update", async () => {
    const membership = await channel.invite(user)
    await sleep(150)

    const status = "moderatorStatus"
    const type = "moderatorType"
    const customData = {
      role: "moderator"
    }

    const updatedMembership = await membership.update({ status, type, custom: customData })
    await sleep(150)

    expect(updatedMembership).toBeDefined()
    expect(updatedMembership.custom).toEqual(customData)
    expect(updatedMembership.status).toEqual(status)
    expect(updatedMembership.type).toEqual(type)
    expect(updatedMembership.user.id).toEqual(user.id)
    expect(updatedMembership.channel.id).toEqual(channel.id)
    expect(updatedMembership.updated).toBeDefined()
    expect(typeof updatedMembership.updated).toBe("string")
    expect(updatedMembership.eTag).toBeDefined()
    expect(typeof updatedMembership.eTag).toBe("string")
  }, 20000)

  test("should set last read message timetoken directly via membership.setLastReadMessageTimetoken", async () => {
    const membership = await channel.invite(user)
    await sleep(150)

    await channel.sendText("Test message 1")
    await sleep(150)
    await channel.sendText("Test message 2")
    await sleep(350)

    const history = await channel.getHistory()
    const message = history.messages[0]

    const updatedMembership = await membership.setLastReadMessageTimetoken(message.timetoken)
    const lastReadTimetoken = updatedMembership.lastReadMessageTimetoken

    expect(lastReadTimetoken).toBeDefined()
    expect(lastReadTimetoken).toEqual(message.timetoken)
  }, 20000)

  test("should get unread messages count via membership.getUnreadMessagesCount", async () => {
    const membership = await channel.invite(user)
    await sleep(150)

    await channel.sendText("Test message 1")
    await sleep(150)
    await channel.sendText("Test message 2")
    await sleep(150)
    await channel.sendText("Test message 3")
    await sleep(350)

    const history = await channel.getHistory()
    const firstMessage = history.messages[2]

    await membership.setLastReadMessage(firstMessage)
    await sleep(150)

    const unreadCount = await membership.getUnreadMessagesCount()

    expect(unreadCount).toBeDefined()
    expect(typeof unreadCount === "number").toBe(true)
    expect(unreadCount).not.toBe(false)
    expect(unreadCount).toBeGreaterThanOrEqual(2)
  }, 20000)

  test("should preserve populated fields in streamUpdates during partial membership.update", async () => {
    const membership = await channel.join({
      status: "memberStatus",
      type: "memberType",
      custom: { role: "member" }
    })
    await sleep(150)

    const updates: Array<Membership | null> = []
    const stopStreaming = membership.streamUpdates((updatedMembership) => { updates.push(updatedMembership) })
    await sleep(150)

    const directUpdatedMembership = await membership.update({ status: "moderatorStatus" })
    await sleep(500)

    stopStreaming()

    const streamedMembership = updates[updates.length - 1]

    expect(directUpdatedMembership.status).toEqual("moderatorStatus")
    expect(directUpdatedMembership.type).toEqual("memberType")
    expect(directUpdatedMembership.custom).toEqual({ role: "member" })
    expect(streamedMembership).toBeDefined()
    expect(streamedMembership).not.toBeNull()
    expect(streamedMembership!.status).toEqual("moderatorStatus")
    expect(streamedMembership!.type).toEqual("memberType")
    expect(streamedMembership!.custom).toEqual({ role: "member" })
  }, 20000)

  test("should stream updates for multiple memberships via Membership.streamUpdatesOn", async () => {
    const user2 = await createRandomUser(chat)
    const membership1 = await channel.invite(user)
    const membership2 = await channel.invite(user2)
    await sleep(150)

    const updates: Membership[] = []
    const stopStreaming = Membership.streamUpdatesOn([membership1, membership2],
      (updatedMemberships: Membership[]) => {
        for (const membership of updatedMemberships) {
          updates.push(membership)
        }
      }
    )

    await sleep(1000)
    await membership1.update({ status: "moderatorStatus", type: "moderatorType", custom: { role: "moderator" } })
    await sleep(1000)

    const foundMembership = updates.find((value) => {
      return value.channel.id === membership1.channel.id && value.user.id === membership1.user.id
    })

    expect(foundMembership).toBeDefined()
    expect(foundMembership?.custom).toEqual({ role: "moderator" })
    expect(foundMembership?.status).toEqual("moderatorStatus")
    expect(foundMembership?.type).toEqual("moderatorType")

    stopStreaming()

    await channel.leave()
    await user2.delete()
  }, 35000)

  test("should return 0 when all messages are read", async () => {
    const membership = await channel.invite(chat.currentUser)
    await sleep(150)

    await channel.sendText("Test message")
    await sleep(350)

    const history = await channel.getHistory()
    const message = history.messages[0]

    const updatedMembership = await membership.setLastReadMessage(message)
    await sleep(150)
    const unreadCount = await updatedMembership.getUnreadMessagesCount()

    expect(unreadCount).toBeDefined()
    expect(typeof unreadCount === "number").toBe(true)
    expect(unreadCount === 0).toBe(true)
  }, 20000)

  test("should match direct update result and onUpdated callback for partial membership.update", async () => {
    const membership = await channel.join({
      status: "memberStatus",
      type: "memberType",
      custom: { role: "member" }
    })
    await sleep(150)

    let callbackMembership: Membership | undefined
    const stop = membership.onUpdated((m) => { callbackMembership = m })
    await sleep(1000)

    const directUpdatedMembership = await membership.update({ status: "moderatorStatus" })
    await sleep(500)

    expect(directUpdatedMembership.status).toEqual("moderatorStatus")
    expect(directUpdatedMembership.type).toEqual("memberType")
    expect(directUpdatedMembership.custom).toEqual({ role: "member" })
    expect(callbackMembership).toBeDefined()
    expect(callbackMembership!.status).toEqual("moderatorStatus")
    expect(callbackMembership!.type).toEqual("memberType")
    expect(callbackMembership!.custom).toEqual({ role: "member" })
    stop()
  }, 20000)

  test("should preserve lastReadMessageTimetoken when updating custom data via membership.update", async () => {
    const membership = await channel.invite(user)
    await sleep(150)

    await channel.sendText("Test message")
    await sleep(350)

    const history = await channel.getHistory()
    const message = history.messages[0]

    const membershipWithTimetoken = await membership.setLastReadMessage(message)
    const timetoken = membershipWithTimetoken.lastReadMessageTimetoken
    expect(timetoken).toBeDefined()

    const updatedMembership = await membershipWithTimetoken.update({ custom: { role: "moderator" } })

    expect(updatedMembership.lastReadMessageTimetoken).toEqual(timetoken)
    expect(updatedMembership.custom.role).toEqual("moderator")
  }, 20000)

  test("should delete membership via membership.delete", async () => {
    const membership = await channel.invite(user)
    const result = await membership.delete()
    expect(result).toBe(true)

    const isMember = await user.isMemberOf(channel.id)
    expect(isMember).toBe(false)
  }, 20000)

  test("should fire callback when membership is deleted via membership.onDeleted", async () => {
    const membership = await channel.join()
    await sleep(150)

    let deletedCalled = false
    const stop = membership.onDeleted(() => { deletedCalled = true })
    await sleep(1000)

    await channel.leave()
    await sleep(500)

    expect(deletedCalled).toBe(true)
    stop()
  }, 20000)
})
