package com.pubnub.kmp

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.BeforeTest
import kotlin.test.Test

class UserTest {
    private lateinit var objectUnderTest: User
    private val chat: Chat = mock(MockMode.strict)
    private val id = "testId"
    private val name = "testName"
    private val externalId = "testExternalId"
    private val profileUrl = "testProfileUrl"
    private val email = "testEmail"
    private val custom = mapOf("testCustom" to "custom")
    private val status = "testStatus"
    private val type = "testType"
    private val updated = "testUpdated"
    private val callbackUser: (Result<User>) -> Unit = { }
    private val channelId = "channelId01"

    @BeforeTest
    fun setUp() {
        objectUnderTest = User(
            chat = chat,
            id = id,
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = type,
            updated = updated,
        )
    }

    @Test
    fun canSoftDeleteUser() {
        // given
        val softDeleteTrue = true
        every { chat.deleteUser(any(), any(), any())} returns Unit

        // when
        objectUnderTest.delete(softDeleteTrue, callbackUser)

        // then
        verify { chat.deleteUser(id, softDeleteTrue, callbackUser) }
    }

    @Test
    fun canHardDeleteUser() {
        // given
        val softDeleteFalse = false
        every { chat.deleteUser(any(), any(), any()) } returns Unit

        // when
        objectUnderTest.delete(softDelete = softDeleteFalse, callbackUser)

        // then
        verify { chat.deleteUser(id, softDeleteFalse, callbackUser) }
    }

    @Test
    fun canUpdateUser() {
        // given
        every {
            chat.updateUser(
                id = any(),
                name = any(),
                externalId = any(),
                profileUrl = any(),
                email = any(),
                custom = any(),
                status = any(),
                type = any(),
                updated = any(),
                callback = any()
            )
        } returns Unit

        // when
        objectUnderTest.update(
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = type,
            updated = updated,
            callback = callbackUser
        )

        // then
        verify { chat.updateUser(id, name, externalId, profileUrl, email, custom, status, type, updated, callbackUser) }
    }

    @Test
    fun canWherePresent() {
        // given
        val callback: (Result<List<String>>) -> Unit = {}
        every { chat.wherePresent(any(), any()) } returns Unit

        // when
        objectUnderTest.wherePresent(callback)

        // then
        verify { chat.wherePresent(id, callback) }
    }

    @Test
    fun canIsPresentOn() {
        // given
        val callback: (Result<Boolean>) -> Unit = {}
        every { chat.isPresent(any(), any(), any()) } returns Unit

        // when
        objectUnderTest.isPresentOn(channelId = channelId, callback = callback)

        // then
        verify { chat.isPresent(id, channelId, callback) }
    }
}