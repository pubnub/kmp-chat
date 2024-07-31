import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.atomicfu") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("pubnub.shared")
    id("pubnub.dokka")
    id("pubnub.multiplatform")
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api("com.pubnub:pubnub-core-api:9.2-DEV")
                api("com.pubnub:pubnub-kotlin-api:9.2-DEV")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0-RC")
            }
        }
    }
}
