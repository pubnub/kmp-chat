package com.pubnub.integration

import com.pubnub.kmp.DataUploadContent
import com.pubnub.kmp.FileUploadContent
import com.pubnub.kmp.Uploadable
import platform.Foundation.NSData
import platform.Foundation.NSDataBase64DecodingIgnoreUnknownCharacters
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToURL

actual fun generateFileContent(fileName: String): Uploadable {
    return DataUploadContent(
        NSString.create(string = "some text").dataUsingEncoding(NSUTF8StringEncoding)!!,
        "text/plain"
    )
}

actual fun generateFileContentFromImage(): Uploadable {
    val fileName = "TestImage01.png"
    val base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO2Z2ioAAAAASUVORK5CYII="
    val data = NSData.create(base64, NSDataBase64DecodingIgnoreUnknownCharacters)
    val fileUrl = createTempFileWithData(data!!, fileName)
    return FileUploadContent(fileUrl!!)
}

private fun createTempFileWithData(data: NSData, fileName: String): NSURL? {
    val tempDir = NSTemporaryDirectory()
    val tempDirNSString = tempDir as NSString
    val filePath = tempDirNSString.stringByAppendingPathComponent(fileName)
    val url = NSURL.fileURLWithPath(filePath)
    data.writeToURL(url, atomically = true)
    return url
}
