@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.mutelist.MutedUsersManager
import kotlin.js.Promise

@JsExport
@JsName("MutedUsersManager")
class MutedUsersManagerJs internal constructor(private val mutedUsersManager: MutedUsersManager) {
    val mutedUsers: Array<String> get() {
        return mutedUsersManager.mutedUsers.toTypedArray()
    }

    fun muteUser(userId: String): Promise<Any> = mutedUsersManager.muteUser(userId).asPromise()

    fun unmuteUser(userId: String): Promise<Any> = mutedUsersManager.unmuteUser(userId).asPromise()
}
