package com.pubnub.integration

import com.pubnub.kmp.Chat
import com.pubnub.kmp.ChatConfigImpl
import com.pubnub.kmp.ChatImpl
import com.pubnub.kmp.User
import com.pubnub.test.BaseIntegrationTest
import com.pubnub.test.await
import com.pubnub.test.randomString
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseChatIntegrationTest: BaseIntegrationTest() {

    lateinit var chat: Chat
    lateinit var someUser: User
    var cleanup: MutableList<suspend ()->Unit> = mutableListOf()

    @BeforeTest
    override fun before(){
        super.before()
        chat = ChatImpl(ChatConfigImpl(config), pubnub)
        someUser = User(chat, randomString(), randomString(), randomString(), randomString(), randomString(), mapOf(randomString() to randomString()), randomString(), randomString(), updated = null, lastActiveTimestamp = null)
    }

    @AfterTest
    fun afterTest() = runTest {
        pubnub.removeUUIDMetadata(someUser.id).await()
        cleanup.forEach { it.invoke() }
    }
}