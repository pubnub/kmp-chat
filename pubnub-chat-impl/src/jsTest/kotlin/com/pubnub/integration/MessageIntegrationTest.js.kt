package com.pubnub.integration

import com.pubnub.kmp.Uploadable
import com.pubnub.kmp.UploadableImpl

actual fun generateFileContent(fileName: String): Uploadable {
    val fileObj: dynamic = js("({})")
    fileObj.data = "some text"
    fileObj.name = fileName
    fileObj.mimeType = "text/plain"
    return UploadableImpl(fileObj)
}

actual fun generateFileContentFromImage(): Uploadable {
    val base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO2Z2ioAAAAASUVORK5CYII="
    // Decode base64 to a Buffer in Node.js
    val buffer = js("Buffer.from(base64, 'base64')")
    return UploadableImpl(
        js("({ data: buffer, name: 'TestImage01.png', mimeType: 'image/png' })")
    )
}
