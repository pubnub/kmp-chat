package com.pubnub.kmp

import com.pubnub.com.pubnub.kmp.Chat
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.Test

class UserMokkeryTest {

    private val chat: Chat = mock(MockMode.strict)

    private val id = "testId"
    private val name = "testName"
    private val externalId = "testExternalId"
    private val profileUrl = "testProfileUrl"
    private val email = "testEmail"
    private val custom = "testCustom"
    private val status = "testStatus"
    private val type = "testType"
    private val updated = "testUpdated"
    private val callback: (Result<User>) -> Unit = { result: Result<User> -> }

    private val objectUnderTest = User(
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

    @Test
    fun canSoftDeleteUser() {
        // given
        val softDeleteTrue = true
        every { chat.deleteUser(any(), any(), any())} returns Unit

        // when
        objectUnderTest.delete(softDeleteTrue, callback)

        // then
        verify { chat.deleteUser(id, softDeleteTrue, callback) }
    }

    @Test
    fun canHardDeleteUser() {
        // given
        val softDeleteFalse = false
        every { chat.deleteUser(any(), any(), any()) } returns Unit

        // when
        objectUnderTest.delete(softDelete = softDeleteFalse, callback)

        // then
        verify { chat.deleteUser(id, softDeleteFalse, callback) }
    }

    @Test
    fun canUpdateUser() {
        // given

        // when
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

        objectUnderTest.update(
            name = name,
            externalId = externalId,
            profileUrl = profileUrl,
            email = email,
            custom = custom,
            status = status,
            type = type,
            updated = updated,
            callback = callback
        )

        // then
        verify { chat.updateUser(id, name, externalId, profileUrl, email, custom, status, type, updated, callback) }
    }
}