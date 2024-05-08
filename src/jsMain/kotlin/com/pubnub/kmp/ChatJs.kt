@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import kotlin.js.Promise

@JsExport
@JsName("Chat")
class ChatJs constructor(private val chatConfig: ChatConfig) {
    private val chat: Chat = ChatImpl(chatConfig)

    fun createUser(
        id: String,
        userInfo: dynamic
    ): Promise<UserJs> {
        return Promise { resolve, reject ->
            chat.createUser(
                id,
                userInfo.name,
                userInfo.externalId,
                userInfo.profileUrl,
                userInfo.email,
                userInfo.custom,
                userInfo.status,
                userInfo.type
            ) { result: Result<User> ->
                result.onSuccess { resolve(UserJs(it)) }
                    .onFailure { reject(it) }
            }
        }
    }

    fun updateUser(
        id: String,
        userInfo: dynamic
    ): Promise<UserJs> {
        return Promise { resolve, reject ->
            chat.updateUser(
                id,
                userInfo.name,
                userInfo.externalId,
                userInfo.profileUrl,
                userInfo.email,
                userInfo.custom,
                userInfo.status,
                userInfo.type,
                userInfo.updated,
            ) { result: Result<User> ->
                result.onSuccess { resolve(UserJs(it)) }
                    .onFailure { reject(it) }
            }
        }
    }

    fun deleteUser(id: String, params: dynamic = null): Promise<UserJs> {
        return Promise { resolve, reject ->
            chat.deleteUser(
                id,
                params?.softDelete ?: false
            ) { result: Result<User> ->
                result.onSuccess { resolve(UserJs(it)) }
                    .onFailure { reject(it) }
            }
        }
    }
}

@JsExport
@JsName("User")
class UserJs internal constructor(private val user: User) : UserInfo by user {
    fun update(
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: Map<String, Any?>? = null,
        status: String? = null,
        type: String? = null,
        updated: String? = null, // todo do we need this?
        ) : Promise<UserJs>{
        return Promise { resolve, reject ->
            user.update(name, externalId, profileUrl, email, custom, status, type, updated)
            { result: Result<User> ->
                result.onSuccess { resolve(UserJs(it)) }
                    .onFailure { reject(it) }
            }
        }
    }

    fun delete(softDelete: Boolean) : Promise<UserJs> {
        return Promise { resolve, reject ->
            user.delete(softDelete) { result: Result<User> ->
                result.onSuccess { resolve(UserJs(it)) }
                    .onFailure { reject(it) }
            }
        }
    }
}