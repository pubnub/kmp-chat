package com.pubnub.chat.mutelist

import com.pubnub.chat.config.ChatConfiguration
import com.pubnub.kmp.PNFuture

interface MutedUsersManager {
    /**
     * The current set of muted users.
     */
    val mutedUsers: Set<String>

    /**
     * Add a user to the list of muted users.
     *
     * @param userId the ID of the user to mute
     * @return a PNFuture to monitor syncing data to the server.
     *
     *   When [ChatConfiguration.syncMutedUsers] is enabled, it can fail e.g. because of network
     *   conditions or when number of muted users exceeds the limit.
     *
     *   When `syncMutedUsers` is false, it always succeeds (data is not synced in that case).
     */
    fun muteUser(userId: String): PNFuture<Unit>

    /**
     * Add a user to the list of muted users.
     *
     * @param userId the ID of the user to mute
     * @return a PNFuture to monitor syncing data to the server.
     *
     *   When [ChatConfiguration.syncMutedUsers] is enabled, it can fail e.g. because of network
     *   conditions or when number of muted users exceeds the limit.
     *
     *   When `syncMutedUsers` is false, it always succeeds (data is not synced in that case).
     */
    fun unmuteUser(userId: String): PNFuture<Unit>
}
