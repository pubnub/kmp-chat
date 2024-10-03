package com.pubnub.integration

import com.pubnub.kmp.Uploadable
import com.pubnub.kmp.UploadableImpl

actual fun generateFileContent(): Uploadable {
    return UploadableImpl(js("""
        {
            data: "some text",
            name: "name.txt",
            mimeType: "text/plain"
        }
    """))
}