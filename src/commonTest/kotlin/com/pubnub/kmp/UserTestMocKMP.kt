package com.pubnub.kmp

import com.pubnub.com.pubnub.kmp.Chat
import org.kodein.mock.Mock
import org.kodein.mock.tests.TestsWithMocks
import kotlin.test.Test

class UserTestMocKMP : TestsWithMocks() {
    @Mock
    lateinit var chat: Chat

    private val id = "testId"
    private val name = "testName"
    private val externalId = "testExternalId"
    private val profileUrl = "testProfileUrl"
    private val email = "testEmail"
    private val custom = "testCustom"
    private val status = "testStatus"
    private val type = "testType"
    private val updated = "testUpdated"
    val callback: (Result<User>) -> Unit = { result: Result<User> -> }

    private val objectUnderTest by withMocks {
        User(
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
        every { chat.deleteUser(isAny(), isAny(), isAny()) } returns Unit

        // when
        objectUnderTest.delete(softDelete = softDeleteTrue, callback)

        // then
        verify { chat.deleteUser(id, softDeleteTrue, callback) }
    }

    @Test
    fun canHardDeleteUser() {
        // given
        val softDeleteFalse = false
        every { chat.deleteUser(isAny(), isAny(), isAny()) } returns Unit

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
                id = isAny(),
                name = isAny(),
                externalId = isAny(),
                profileUrl = isAny(),
                email = isAny(),
                custom = isAny(),
                status = isAny(),
                type = isAny(),
                updated = isAny(),
                callback = isAny()
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

    override fun setUpMocks() {
        // this is known issue :| Code compile even if IDEA shows error.
        injectMocks(mocker)
    }
}