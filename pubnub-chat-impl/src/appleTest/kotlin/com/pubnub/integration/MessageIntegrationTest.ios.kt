package com.pubnub.integration

import com.pubnub.kmp.DataUploadContent
import com.pubnub.kmp.Uploadable
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding

@OptIn(BetaInteropApi::class)
actual fun generateFileContent(): Uploadable {
    return DataUploadContent(
        NSString.create(string = "some text").dataUsingEncoding(NSUTF8StringEncoding)!!,
        "text/plain"
    )
}
