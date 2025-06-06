package com.pubnub.integration

import com.pubnub.kmp.Uploadable
import java.io.FileNotFoundException
import java.io.InputStream

actual fun generateFileContent(fileName: String): Uploadable {
    return "some text".byteInputStream()
}

actual fun generateFileContentFromImage(): Uploadable {
    val stream: InputStream = Thread.currentThread()
        .contextClassLoader
        .getResourceAsStream("TestImage01.png")
        ?: throw FileNotFoundException("Resource 'TestImage01.png' not found on classpath")
    return stream
}
