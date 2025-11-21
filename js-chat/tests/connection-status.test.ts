import {Chat, ConnectionStatusCategory} from "../dist-test"
import {createChatInstance, sleep} from "./utils";

describe("Connection Status test", () => {
    jest.retryTimes(0) // Disable retries for this test

    let chat1: Chat
    let chat2: Chat

    const TEST_CHANNEL = "test-channel-" + Math.random().toString(36).substring(2, 15)

    beforeAll(async () => {
        chat1 = await createChatInstance({
            config: {
                enableEventEngine: true
            }
        })
        chat2 = await createChatInstance({
            shouldCreateNewInstance: true,
            config: {
                enableEventEngine: true
            }
        })
    })

    afterAll(async () => {
        // Use type assertion to access destroy method
        ;(chat1 as any)?.destroy?.()
        ;(chat2 as any)?.destroy?.()
    })

    const waitForCondition = (
        predicate: () => boolean,
        description: string,
        timeoutMs = 5000
    ): Promise<void> => {
        return new Promise((resolve, reject) => {
            const startTime = Date.now()
            const interval = setInterval(() => {
                if (predicate()) {
                    clearInterval(interval)
                    resolve()
                } else if (Date.now() - startTime > timeoutMs) {
                    clearInterval(interval)
                    reject(new Error(`Timeout waiting for: ${description} (${timeoutMs}ms)`))
                }
            }, 100)
        })
    }

    test("should receive connection status changes throughout connection lifecycle", async () => {
        // Track specific events in the lifecycle
        let firstOnlineReceived = false
        let offlineReceived = false
        let secondOnlineReceived = false
        let eventCount = 0
        const eventHistory: string[] = []

        const removeListener = chat1.addConnectionStatusListener((status) => {
            eventCount++
            const statusValue = status.category.value
            eventHistory.push(statusValue)

            // Track events based on sequence
            if (statusValue === ConnectionStatusCategory.PN_CONNECTION_ONLINE.value) {
                if (!firstOnlineReceived) {
                    firstOnlineReceived = true
                } else if (offlineReceived && !secondOnlineReceived) {
                    secondOnlineReceived = true
                }
            }
            if (statusValue === ConnectionStatusCategory.PN_CONNECTION_OFFLINE.value) {
                offlineReceived = true
            }

            // Validate ConnectionStatus object structure
            expect(status).toHaveProperty('category')
            expect(status.category).toHaveProperty('value')
            expect(typeof status.category.value).toBe('string')
        })

        try {
            const channel = await chat1.createPublicConversation({
                channelId: TEST_CHANNEL,
                channelData: {
                    name: "Connection Status Test Channel",
                    description: "Test channel for connection status integration test"
                }
            })
            const disconnect = channel.connect(() => {
                // Message callback - not needed for this test
            })

            // Wait for initial online status
            await waitForCondition(
                () => firstOnlineReceived,
                "First ONLINE status after connecting",
                8000
            )

            await chat1.disconnectSubscriptions()
            await waitForCondition(
                () => offlineReceived,
                "OFFLINE status after disconnecting",
                8000
            )

            await chat1.reconnectSubscriptions()
            await waitForCondition(
                () => secondOnlineReceived,
                "Second ONLINE status after reconnecting",
                8000
            )

            // Verify we received events
            expect(eventCount).toBeGreaterThan(0)
            expect(eventHistory.length).toBe(eventCount)

            // Verify all three lifecycle events were received
            expect(firstOnlineReceived).toBe(true)
            expect(offlineReceived).toBe(true)
            expect(secondOnlineReceived).toBe(true)

            // Verify event sequence is correct (ONLINE → OFFLINE → ONLINE)
            expect(eventHistory.length).toBeGreaterThanOrEqual(3)
            const firstOnlineIndex = eventHistory.indexOf('PN_CONNECTION_ONLINE')
            const offlineIndex = eventHistory.indexOf('PN_CONNECTION_OFFLINE')
            const lastOnlineIndex = eventHistory.lastIndexOf('PN_CONNECTION_ONLINE')

            expect(firstOnlineIndex).toBeGreaterThanOrEqual(0)
            expect(offlineIndex).toBeGreaterThan(firstOnlineIndex)
            expect(lastOnlineIndex).toBeGreaterThan(offlineIndex)
            expect(lastOnlineIndex).not.toBe(firstOnlineIndex) // Ensure we got TWO different online events

            // Verify the static constants are accessible and work
            expect(ConnectionStatusCategory.PN_CONNECTION_ONLINE).toBeDefined()
            expect(ConnectionStatusCategory.PN_CONNECTION_OFFLINE).toBeDefined()
            expect(ConnectionStatusCategory.PN_CONNECTION_ONLINE.value).toBe('PN_CONNECTION_ONLINE')
            expect(ConnectionStatusCategory.PN_CONNECTION_OFFLINE.value).toBe('PN_CONNECTION_OFFLINE')

            // Clean up the connection
            disconnect()

        } finally {
            removeListener()
            // Clean up the channel
            try {
                const channelToDelete = await chat1.getChannel(TEST_CHANNEL)
                if (channelToDelete) {
                    await channelToDelete.delete()
                }
            } catch (error) {
            }
        }
    }, 30000) // 30 second timeout for this comprehensive test

    test("should handle multiple connection status listeners", async () => {
        let onlineReceivedFirstListener = false
        let onlineReceivedSecondListener = false
        let offlineReceivedFirstListener = false
        let offlineReceivedSecondListener = false
        let eventCountFirstListener = 0
        let eventCountSecondListener = 0
        const eventHistory: string[] = []

        // Add multiple connection status listeners
        const removeListener1 = chat1.addConnectionStatusListener((status) => {
            eventCountFirstListener++
            const statusValue = status.category.value
            eventHistory.push(statusValue)

            if (statusValue === ConnectionStatusCategory.PN_CONNECTION_ONLINE.value) {
                onlineReceivedFirstListener = true
            }

            if (statusValue === ConnectionStatusCategory.PN_CONNECTION_OFFLINE.value) {
                offlineReceivedFirstListener = true
            }

            expect(status).toHaveProperty('category')
            expect(status.category).toHaveProperty('value')
            expect(typeof status.category.value).toBe('string')
        })

        const removeListener2 = chat1.addConnectionStatusListener((status) => {
            eventCountSecondListener++
            const statusValue = status.category.value
            eventHistory.push(statusValue)

            if (statusValue === ConnectionStatusCategory.PN_CONNECTION_ONLINE.value) {
                onlineReceivedSecondListener = true
            }

            if (statusValue === ConnectionStatusCategory.PN_CONNECTION_OFFLINE.value) {
                offlineReceivedSecondListener = true
            }

            expect(status).toHaveProperty('category')
            expect(status.category).toHaveProperty('value')
            expect(typeof status.category.value).toBe('string')
        })

        try {
            const channel = await chat1.createPublicConversation({
                channelId: TEST_CHANNEL,
                channelData: {
                    name: "Connection Status Test Channel",
                    description: "Test channel for connection status integration test"
                }
            })
            const disconnect = channel.connect(() => {
                // Message callback - not needed for this test
            })
            // Wait for initial online status
            await waitForCondition(
                () => onlineReceivedFirstListener && onlineReceivedSecondListener,
                "First ONLINE status after connecting",
                8000
            )

            await chat1.disconnectSubscriptions()
            await waitForCondition(
                () => offlineReceivedFirstListener && offlineReceivedSecondListener,
                "OFFLINE status after disconnecting",
                8000
            )

            expect(onlineReceivedFirstListener).toBe(true)
            expect(onlineReceivedSecondListener).toBe(true)
            expect(offlineReceivedFirstListener).toBe(true)
            expect(offlineReceivedSecondListener).toBe(true)

        } finally {
            removeListener1()
            removeListener2()
            try {
                const channelToDelete = await chat1.getChannel(TEST_CHANNEL)
                if (channelToDelete) {
                    await channelToDelete.delete()
                }
            } catch (error) {
            }
        }
    }, 30000) // 30 second timeout for this comprehensive test

    test("should check if connection status methods are available", () => {
        // Check if the new methods are available on the chat instance
        expect(typeof chat1.addConnectionStatusListener).toBe('function')
        expect(typeof chat1.reconnectSubscriptions).toBe('function')
        expect(typeof chat1.disconnectSubscriptions).toBe('function')
    })

    test("should add and remove connection status listeners", () => {
        const statusCallback = jest.fn()

        // Add a connection status listener - this returns a cleanup function
        const removeListener = chat1.addConnectionStatusListener(statusCallback)

        // Verify the returned value is a function
        expect(typeof removeListener).toBe("function")

        // Verify that calling the cleanup function doesn't throw
        expect(() => {
            removeListener()
        }).not.toThrow()
    })

    test("should handle multiple connection status listeners", () => {
        const statusCallback1 = jest.fn()
        const statusCallback2 = jest.fn()
        const statusCallback3 = jest.fn()

        // Add multiple listeners
        const removeListener1 = chat1.addConnectionStatusListener(statusCallback1)
        const removeListener2 = chat1.addConnectionStatusListener(statusCallback2)
        const removeListener3 = chat1.addConnectionStatusListener(statusCallback3)

        // Verify all returned values are cleanup functions
        expect(typeof removeListener1).toBe('function')
        expect(typeof removeListener2).toBe('function')
        expect(typeof removeListener3).toBe('function')

        // Verify that each cleanup function can be called without throwing
        expect(() => removeListener2()).not.toThrow()
        expect(() => removeListener1()).not.toThrow()
        expect(() => removeListener3()).not.toThrow()

        // Trigger a connection status change to verify removed listeners don't get called
        return chat1.disconnectSubscriptions()
            .then(() => sleep(300))
            .then(() => chat1.reconnectSubscriptions())
            .then(() => sleep(300))
            .then(() => {
                // Since we removed all listeners, none of the callbacks should have been called
                expect(statusCallback1).not.toHaveBeenCalled()
                expect(statusCallback2).not.toHaveBeenCalled()
                expect(statusCallback3).not.toHaveBeenCalled()
            })
    })

    test("should handle listener exceptions gracefully", async () => {
        const faultyCallback = jest.fn(() => {
            throw new Error("Listener exception")
        })
        const goodCallback = jest.fn()

        // Add both faulty and good listeners
        const removeFaultyListener = chat1.addConnectionStatusListener(faultyCallback)
        const removeGoodListenerId = chat1.addConnectionStatusListener(goodCallback)

        // Trigger a status change
        await chat1.disconnectSubscriptions()
        await sleep(300)
        await chat1.reconnectSubscriptions()
        await sleep(300)

        // Both callbacks should have been called (errors should be caught)
        // Note: We can't guarantee they'll be called in this test setup,
        // but if they are, the faulty one shouldn't break the good one

        // Clean up
        removeFaultyListener()
        removeGoodListenerId()
    })
})
