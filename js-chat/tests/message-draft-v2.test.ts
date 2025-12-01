import { Channel, Chat, MessageDraftV2, MixedTextTypedElement } from "../dist-test"
import {
  createChatInstance,
  renderMessagePart,
  generateRandomString,
  createRandomChannel,
  createRandomUser
} from "./utils"
import { jest } from "@jest/globals"

describe("MessageDraft2", function () {
  jest.retryTimes(2)

  let chat: Chat
  let channel: Channel
  let messageDraft: MessageDraftV2

  beforeEach(async () => {
    chat = await createChatInstance({ userId: generateRandomString() })
    channel = await createRandomChannel(chat)
    messageDraft = channel.createMessageDraftV2({ userSuggestionSource: "global" })
  })

  afterEach(async () => {
    await channel.delete()
    await chat.currentUser.delete()
    await chat.sdk.disconnect()
  })

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

    await Promise.all([user1.delete({ soft: false }), user2.delete({ soft: false })])
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
      user1.delete({ soft: false }),
      user2.delete({ soft: false }),
      user3.delete({ soft: false }),
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
          channel1.delete({ soft: false }),
          channel2.delete({ soft: false })
      ])
      await Promise.all([
          user1.delete({ soft: false }),
          user2.delete({ soft: false }),
          user4.delete({ soft: false }),
      ])
    }, 25000)
})
