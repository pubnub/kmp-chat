@file:OptIn(ExperimentalJsExport::class)

package com.pubnub.kmp

import kotlin.js.Promise

fun <T> PNFuture<T>.asPromise(): Promise<T> = Promise { resolve, reject ->
    async {
        it.onSuccess {
            resolve(it)
        }.onFailure {
            reject(it)
        }
    }
}

@JsExport
@JsName("Chat")
class ChatJs constructor(private val chatConfig: ChatConfig) {
    private val chat: Chat = ChatImpl(chatConfig)

//    fun createUser(
//        id: String,
//        userInfo: dynamic
//    ): Promise<UserJs> {
//        return GlobalScope.promise {
//            UserJs(chat.createUser(id,
//                userInfo.name,
//                userInfo.externalId,
//                userInfo.profileUrl,
//                userInfo.email,
//                userInfo.custom,
//                userInfo.status,
//                userInfo.type))
//        }
//    }

    fun updateUser(
        id: String,
        userInfo: dynamic
    ): Promise<UserJs> {
        return chat.updateUser(
            id,
            userInfo.name,
            userInfo.externalId,
            userInfo.profileUrl,
            userInfo.email,
            userInfo.custom,
            userInfo.status,
            userInfo.type,
        ).then { UserJs(it) }.asPromise()
    }

    fun deleteUser(id: String, params: dynamic = null): Promise<UserJs> {
        return chat.deleteUser(
            id,
            params?.softDelete ?: false
        ).then { UserJs(it) }.asPromise()
    }
}

@JsExport
@JsName("User")
class UserJs internal constructor(private val user: User) {
    fun update(
        name: String? = null,
        externalId: String? = null,
        profileUrl: String? = null,
        email: String? = null,
        custom: CustomObject? = null,
        status: String? = null,
        type: String? = null,
        updated: String? = null, // todo do we need this?
    ): Promise<UserJs> {
        return user.update(name, externalId, profileUrl, email, custom, status, type)
            .then { UserJs(it) }.asPromise()
    }

    fun delete(softDelete: Boolean): Promise<UserJs> {
        return user.delete(softDelete).then { UserJs(it) }.asPromise()
    }
}
