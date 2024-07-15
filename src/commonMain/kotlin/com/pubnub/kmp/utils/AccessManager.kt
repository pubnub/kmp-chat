package com.pubnub.kmp.utils

import com.pubnub.api.models.consumer.access_manager.v3.PNToken
import com.pubnub.kmp.Chat

internal class AccessManager(private val chat: Chat) {
    internal enum class ResourceType { UUIDS, CHANNELS }

    internal enum class Permission { READ, WRITE, MANAGE, DELETE, GET, JOIN, UPDATE }

    fun canI(permission: Permission, resourceType: ResourceType, resourceName: String): Boolean {
        val authKey = chat.pubNub.configuration.authKey
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
            permission: Permission
        ): Boolean {
            val resource = getResourceMap(resourceType, parsedToken)
            val resourcePermission = resource[resourceName]?.let {
                getPermission(it, permission)
            }

            if (resourcePermission != null) {
                return resourcePermission
            }

            val resourcePatterns = getPatternMap(resourceType, parsedToken)
            return checkPatterns(resourcePatterns, resourceName, permission)
        }

        private fun getResourceMap(resourceType: ResourceType, parsedToken: PNToken): Map<String, PNToken.PNResourcePermissions> {
            return if (resourceType == ResourceType.UUIDS) {
                parsedToken.resources.uuids
            } else {
                parsedToken.resources.channels
            }
        }

        private fun getPatternMap(resourceType: ResourceType, parsedToken: PNToken): Map<String, PNToken.PNResourcePermissions> {
            return if (resourceType == ResourceType.UUIDS) {
                parsedToken.patterns.uuids
            } else {
                parsedToken.patterns.channels
            }
        }

        private fun getPermission(resource: PNToken.PNResourcePermissions, permission: Permission): Boolean {
            return when (permission) {
                Permission.READ -> resource.read
                Permission.WRITE -> resource.write
                Permission.MANAGE -> resource.manage
                Permission.DELETE -> resource.delete
                Permission.GET -> resource.get
                Permission.JOIN -> resource.join
                Permission.UPDATE -> resource.update
            }
        }

        private fun checkPatterns(
            resourcePatterns: Map<String, PNToken.PNResourcePermissions>,
            resourceName: String,
            permission: Permission
        ): Boolean {
            resourcePatterns.keys.forEach { pattern ->
                val regex = Regex(pattern)
                if (regex.matches(resourceName)) {
                    val resPermission = resourcePatterns[pattern] ?: return false
                    return getPermission(resPermission, permission)
                }
            }
            return false
        }
    }
}
