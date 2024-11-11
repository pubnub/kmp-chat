package com.pubnub.integration

import com.pubnub.chat.types.GetFilesResult
import com.pubnub.chat.types.InputFile
import com.pubnub.test.await
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChannelJvmIntegrationTest : BaseChatIntegrationTest() {
    @Test
    fun sendFileGetFileDeleteFile() = runTest {
        val fileName = "test.txt"
        val type = "text/plain"
        val data = "dfa"
        val source = ByteArrayInputStream(data.toByteArray())
        val file = InputFile(name = fileName, type = type, source = source)

        // send file
        channel01.sendText(text = "messageWithFile", files = listOf(file)).await()

        // get file
        val filesResult: GetFilesResult = channel01.getFiles().await()

        assertEquals(1, filesResult.files.size)
        val actualFile = filesResult.files.first()
        assertEquals(fileName, actualFile.name)
        assertNotNull(actualFile.id)
        assertNotNull(actualFile.url)

        // delete file
        val deleteFileResult = channel01.deleteFile(actualFile.id, actualFile.name).await()
        assertEquals(200, deleteFileResult.status)
    }
}
