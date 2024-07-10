package com.pubnub.kmp.utils

import com.pubnub.api.models.consumer.access_manager.v3.PNToken
import com.pubnub.kmp.utils.AccessManager.Companion.canI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccessManagerTest {

    val token =
        PNToken(
            version = 2,
            timestamp = 1632335843,
            ttl = 1440,
            authorizedUUID = "myauthuuid1",
            resources =
            PNToken.PNTokenResources(
                channels =
                mapOf(
                    "ch1" to
                            PNToken.PNResourcePermissions(
                                read = true, write = false, manage = true, delete = true, get = true, update = true, join = true,
                            ),
                ),
                uuids =
                mapOf(
                    "uuid1" to
                            PNToken.PNResourcePermissions(
                                read = true, write = false, manage = true, delete = true, get = true, update = true, join = true,
                            ),
                ),
            ),
            patterns =
            PNToken.PNTokenResources(
                uuids =
                mapOf(
                    "^user-.*" to
                            PNToken.PNResourcePermissions(
                                read = true,
                                write = false,
                                manage = false,
                                delete = false,
                                get = false,
                                update = false,
                                join = false,
                            ),
                ),
                channels =
                mapOf(
                    "^channel-.*" to
                            PNToken.PNResourcePermissions(
                                read = true,
                                write = false,
                                manage = false,
                                delete = false,
                                get = false,
                                update = false,
                                join = false,
                            ),
                ),
            ),
        )

    @Test
    fun canI() {
        assertTrue { canI(AccessManager.ResourceType.CHANNELS, token, "ch1", AccessManager.Permission.READ) }
        assertFalse { canI(AccessManager.ResourceType.CHANNELS, token, "nonexistent", AccessManager.Permission.READ) }
        assertFalse{ canI(AccessManager.ResourceType.CHANNELS, token, "ch1", AccessManager.Permission.WRITE) }
        assertFalse { canI(AccessManager.ResourceType.CHANNELS, token, "nonexistent", AccessManager.Permission.WRITE) }

        assertTrue { canI(AccessManager.ResourceType.UUIDS, token, "uuid1", AccessManager.Permission.READ) }
        assertFalse { canI(AccessManager.ResourceType.UUIDS, token, "nonexistent", AccessManager.Permission.READ) }
        assertFalse{ canI(AccessManager.ResourceType.UUIDS, token, "uuid1", AccessManager.Permission.WRITE) }
        assertFalse { canI(AccessManager.ResourceType.UUIDS, token, "nonexistent", AccessManager.Permission.WRITE) }

        // pattern matching
        assertTrue { canI(AccessManager.ResourceType.CHANNELS, token, "channel-abc", AccessManager.Permission.READ) }
        assertFalse { canI(AccessManager.ResourceType.CHANNELS, token, "channel-abc", AccessManager.Permission.WRITE) }

        assertTrue { canI(AccessManager.ResourceType.UUIDS, token, "user-abc", AccessManager.Permission.READ) }
        assertFalse { canI(AccessManager.ResourceType.UUIDS, token, "user-abc", AccessManager.Permission.WRITE) }

    }
}