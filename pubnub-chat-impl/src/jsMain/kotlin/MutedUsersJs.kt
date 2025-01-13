@file:OptIn(ExperimentalJsExport::class)

import com.pubnub.chat.mutelist.MutedUsers
import kotlin.js.Promise

@JsExport
@JsName("MutedUsers")
class MutedUsersJs internal constructor(private val mutedUsers: MutedUsers) {
    val muteSet: Array<String> get() {
        return mutedUsers.muteSet.toTypedArray()
    }

    fun muteUser(userId: String): Promise<Any> = mutedUsers.muteUser(userId).asPromise()

    fun unmuteUser(userId: String): Promise<Any> = mutedUsers.unmuteUser(userId).asPromise()
}
