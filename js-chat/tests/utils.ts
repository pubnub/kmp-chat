// lib/tests/testUtils.ts
import { Chat, MessageDraft, Channel, Message, ChatConfig } from "../dist-test"
import * as dotenv from "dotenv"
import { User } from "../dist-test"
import { MixedTextTypedElement } from "../dist-test"
import PubNub from "pubnub"

dotenv.config()

const testsPrefix = "js-chat"

export function makeid(length = 10, prefix: string = testsPrefix): string {
  let result = ""
  const characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
  const charactersLength = characters.length
  let counter = 0
  while (counter < length) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength))
    counter += 1
  }
  return result
}

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

type ClientType = 'PamClient' | 'PamServer' | 'NoPam' | 'PamServerWithRefIntegrity';

const createChat = async (
    userId: string,
    config?: Partial<ChatConfig> & PubNub.PubnubConfig,
    clientType?: ClientType,

): Promise<Chat> => {

  const keysetError = `
    #######################################################
    # Could not read the PubNub keyset from the .env file #
    #######################################################
  `

  // Determine keys based on clientType
  let publishKey: string | undefined;
  let subscribeKey: string | undefined;
  let secretKey: string | undefined;

  switch (clientType) {
    case 'PamClient':
      publishKey = process.env.PAM_PUBLISH_KEY;
      subscribeKey = process.env.PAM_SUBSCRIBE_KEY;
      break;

    case 'PamServer':
      publishKey = process.env.PAM_PUBLISH_KEY;
      subscribeKey = process.env.PAM_SUBSCRIBE_KEY;
      secretKey = process.env.PAM_SECRET_KEY;
      break;

    case 'PamServerWithRefIntegrity':
      publishKey = process.env.PAM_WITH_INTEGRITY_PUBLISH_KEY;
      subscribeKey = process.env.PAM_WITH_INTEGRITY_SUBSCRIBE_KEY;
      secretKey = process.env.PAM_WITH_INTEGRITY_SECRET_KEY;
      break;

    case 'NoPam':
    default:
      publishKey = process.env.PUBLISH_KEY;
      subscribeKey = process.env.SUBSCRIBE_KEY;
      break;
  }

  // Validate required keys
  if (!publishKey || !subscribeKey || (clientType === 'PamServer' && !secretKey) || (clientType === 'PamServerWithRefIntegrity' && !secretKey)) {
    throw keysetError
  }

  // Build the chat configuration
  const chatConfig: Partial<ChatConfig> & PubNub.PubnubConfig = {
    publishKey,
    subscribeKey,
    userId,
    ...config,
  };

  // Include secretKey only if clientType is 'PamServer' or 'PamServerWithRefIntegrity'
  if ((clientType === 'PamServer' || clientType === 'PamServerWithRefIntegrity') && secretKey) {
    chatConfig.secretKey = secretKey;
  }

  return Chat.init(chatConfig);
};

export async function createChatInstance(
  options: {
    userId?: string
    config?: Partial<ChatConfig> & PubNub.PubnubConfig
    clientType?: ClientType
  } = {}
) {
  return await createChat(options.userId || process.env.USER_ID!, options.config, options.clientType);
}

export function createRandomChannel(chat: Chat, prefix: string = testsPrefix) {
  return chat.createChannel(generateRandomString(10, prefix), {
    name: `${prefix}Test Channel`,
    description: "This is a test channel",
  })
}

export function createRandomUser(chat: Chat, prefix: string = testsPrefix) {
  return chat.createUser(generateRandomString(10, prefix), {
    name: `${prefix}Test User`
  })
}

export function generateRandomString(length: number = 10, prefix: string = testsPrefix): string {
  const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  const charactersLength = characters.length;
  let result = prefix;
  for (let i = 0; i < length; i++) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength));
  }
  return result;
}

export const waitForAllMessagesToBeDelivered = async (
  textMessages: string[],
  messages: string[]
): Promise<void> => {
  await new Promise<void>(async (resolveMainFunction) => {
    for (let i = 0; i < 3; i++) {
      const allMessagesReceived = textMessages.every((textMessage) =>
        messages.includes(textMessage)
      )

      if (allMessagesReceived) {
        break
      } else {
        await new Promise((resolve) => setTimeout(resolve, 1000))
      }
    }

    resolveMainFunction()
  })
}

export const extractMentionedUserIds = (messageText: string): string[] => {
  const regex = /@(\w+)(?!\.[^\s@])\b/g
  const matches = messageText.match(regex)
  if (matches) {
    return matches.map((match) => match.slice(1))
  }
  return []
}

export function generateExpectedLinkedText(
  messageDraft: MessageDraft,
  someUser: User,
  someUser2: User
) {
  const expectedLinkedText = [
    {
      type: "text",
      content: {
        text: "Hello world!! This is a mention to ",
      },
    },
    {
      type: "mention",
      content: {
        name: someUser.name,
        id: someUser.id,
      },
    },
    {
      type: "text",
      content: {
        text: " and this is a ",
      },
    },
    {
      type: "textLink",
      content: {
        link: "https://pubnub.com",
        text: "text link",
      },
    },
    {
      type: "text",
      content: {
        text: " that leads to ",
      },
    },
    {
      type: "plainLink",
      content: {
        link: "https://pubnub.com",
      },
    },
    {
      type: "text",
      content: {
        text: ". Isn't it great? ",
      },
    },
    {
      type: "mention",
      content: {
        name: someUser2.name,
        id: someUser2.id,
      },
    },
  ]

  // Build the message text using V2 API
  let text = "Hello world!! This is a mention to "
  messageDraft.update(text)

  // Add first mention
  const mention1Text = `@${someUser.name}`
  text += mention1Text
  messageDraft.update(text)
  const mention1Offset = text.indexOf(mention1Text)
  messageDraft.addMention(mention1Offset, mention1Text.length, "mention", someUser.id)

  // Add text
  text += " and this is a "
  messageDraft.update(text)

  // Add text link
  messageDraft.addLinkedText({
    text: "text link",
    link: "https://pubnub.com",
    positionInInput: messageDraft.value.length,
  })

  // Add more text including plain link
  text = messageDraft.value + " that leads to https://pubnub.com. Isn't it great? "
  messageDraft.update(text)

  // Add second mention
  const mention2Text = `@${someUser2.name}`
  text += mention2Text
  messageDraft.update(text)
  const mention2Offset = text.lastIndexOf(mention2Text)
  messageDraft.addMention(mention2Offset, mention2Text.length, "mention", someUser2.id)

  return expectedLinkedText
}

export const sendMessageAndWaitForHistory = async (
  messageDraft: MessageDraft,
  channel: Channel,
  waitTimeMs = 150
): Promise<Message | undefined> => {
  await messageDraft.send()
  await sleep(waitTimeMs)

  const history = await channel.getHistory()
  return history.messages[0]
}

export const renderMessagePart = (messagePart: MixedTextTypedElement) => {
  if (messagePart.type === "text") {
    return messagePart.content.text
  }
  if (messagePart.type === "plainLink") {
    return messagePart.content.link
  }
  if (messagePart.type === "textLink") {
    return messagePart.content.text
  }
  if (messagePart.type === "mention") {
    return messagePart.content.name
  }
  if (messagePart.type === "channelReference") {
    return messagePart.content.name
  }

  return null
}
