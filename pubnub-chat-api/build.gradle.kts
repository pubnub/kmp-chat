plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
    id("pubnub.shared")
    id("pubnub.dokka")
    id("pubnub.multiplatform")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.pubnub.kotlin.api)
                implementation(libs.kotlinx.serialization.core)
            }
        }
    }
}
