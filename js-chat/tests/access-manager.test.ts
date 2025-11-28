import {
    Chat
} from "../dist-test"
import {
    createChatInstance,
    generateRandomString,
    sleep
} from "./utils";

import { jest } from "@jest/globals"

describe("Access Manager test test", () => {
    jest.retryTimes(2)

    let chatPamClient: Chat
    let chatPamServer: Chat

    beforeAll(async () => {
        let chatClientUserId = generateRandomString()
        chatPamServer = await createChatInstance( { clientType: 'PamServer' })
        const token = await _grantTokenForUserId(chatPamServer, chatClientUserId);
        chatPamClient = await createChatInstance( {
            userId: chatClientUserId ,
            clientType: 'PamClient',
            config: {
                authKey: token
            },
        })
    })

    afterEach(async () => {
        await chatPamClient.currentUser.delete()
        await chatPamServer.currentUser.delete()
        jest.clearAllMocks()
    })

    // Test is skipped because it has 65sec sleep to wait for token expiration
    test.skip("when token is updated then client can use API", async () => {
        const user1Id = generateRandomString()
        const userToChatWith = await chatPamServer.createUser(user1Id, { name: "User1" })
        const createDirectConversationResult = await chatPamServer.createDirectConversation(
            {
                user: userToChatWith,
                channelData: {
                    name: "Quick sync on customer XYZ"
                },
                membershipData: {
                    custom: {
                        purpose: "premium-support"
                    }
                }
            }
        )
        let channelId = createDirectConversationResult.channel.id
        let token = await _grantTokenForChannel(1, chatPamServer, channelId);
        await chatPamClient.sdk.setToken(token)

        const channelRetrievedByClient = await chatPamClient.getChannel(createDirectConversationResult.channel.id);
        expect(channelRetrievedByClient).toBeDefined();

        // Verify that the fetched channel ID matches the expected channel ID
        expect(channelRetrievedByClient?.id).toEqual(channelId);

        let publishResult  = await channelRetrievedByClient.sendText("my first message");
        let message = await channelRetrievedByClient.getMessage(publishResult.timetoken);
        await message.toggleReaction("one")

        // Sleep so that token expires
        await sleep(65000)
        token = await _grantTokenForChannel(1, chatPamServer, channelId);
        await chatPamClient.sdk.setToken(token)

        await message.toggleReaction("two");
        await chatPamClient.getChannel(channelRetrievedByClient?.id);

        await chatPamServer.deleteChannel(channelId)
    }, 100000)

    async function _grantTokenForUserId(chatPamServer, chatClientUserId) {
        return chatPamServer.sdk.grantToken({
            ttl: 10,
            resources: {
                uuids: {
                    [chatClientUserId]: {
                        get: true,
                        update: true
                    }
                }
            }
        });
    }

    async function _grantTokenForChannel(ttl, chatPamServer, channelId) {
        return chatPamServer.sdk.grantToken({
            ttl: ttl,
            resources: {
                channels: {
                    [channelId]: {
                        read: true,
                        write: true,
                        get: true
                    }
                }
            }
        });
    }
})
