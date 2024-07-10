package com.pubnub.kmp.utils

import com.pubnub.api.models.consumer.access_manager.v3.PNToken
import com.pubnub.kmp.Chat

internal class AccessManager(private val chat: Chat) {
    internal enum class ResourceType { UUIDS, CHANNELS }

    internal enum class Permission { READ, WRITE, MANAGE, DELETE, GET, JOIN, UPDATE }

    fun canI(permission: Permission, resourceType: ResourceType, resourceName: String): Boolean {
        val authKey = chat.config.pubnubConfig.authKey
        if (authKey.isEmpty()) {
            return true
        }
        val parsedToken = chat.pubNub.parseToken(authKey)
        return Companion.canI(resourceType, parsedToken, resourceName, permission)
    }

    companion object {
        internal fun canI(
            resourceType: ResourceType,
            parsedToken: PNToken,
            resourceName: String,
            permission: Permission,
        ): Boolean {
            val resource =
                if (resourceType == ResourceType.UUIDS) parsedToken.resources.uuids else parsedToken.resources.channels
            val resourcePermission = resource[resourceName]?.let {
                when (permission) {
                    Permission.READ -> it.read
                    Permission.WRITE -> it.write
                    Permission.MANAGE -> it.manage
                    Permission.DELETE -> it.delete
                    Permission.GET -> it.get
                    Permission.JOIN -> it.join
                    Permission.UPDATE -> it.update
                }
            }
            if (resourcePermission != null) {
                return resourcePermission
            }

            val resourcePatterns =
                if (resourceType == ResourceType.UUIDS) parsedToken.patterns.uuids else parsedToken.patterns.channels
            resourcePatterns.keys.forEach { pattern ->
                val regex = Regex(pattern)
                if (regex.matches(resourceName)) {
                    val resPermission = resourcePatterns[pattern] ?: return false
                    return when (permission) {
                        Permission.READ -> resPermission.read
                        Permission.WRITE -> resPermission.write
                        Permission.MANAGE -> resPermission.manage
                        Permission.DELETE -> resPermission.delete
                        Permission.GET -> resPermission.get
                        Permission.JOIN -> resPermission.join
                        Permission.UPDATE -> resPermission.update
                    }
                }
            }
            return false
        }
    }
}
