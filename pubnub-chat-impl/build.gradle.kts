import com.pubnub.gradle.enableAnyIosTarget
import com.pubnub.gradle.enableJsTarget
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension

plugins {
    kotlin("plugin.serialization") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.atomicfu") version "2.0.0"
    id("pubnub.ios-simulator-test")
    id("pubnub.shared")
    id("pubnub.dokka")
    id("pubnub.multiplatform")
    id("dev.mokkery") version "2.0.0"
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.pubnub:pubnub-core-api:9.2-DEV")
                implementation("com.pubnub:pubnub-kotlin-api:9.2-DEV")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0-RC")
                implementation("com.benasher44:uuid:0.8.4")
                implementation("org.jetbrains.kotlinx:atomicfu:0.24.0")
                implementation(project(":pubnub-chat-api"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.pubnub:pubnub-kotlin-test")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.pubnub:pubnub-kotlin:9.2-DEV")
                implementation(kotlin("test-junit"))
            }
        }

        if (enableJsTarget) {
            val jsTest by getting {
                dependencies {
                    implementation(kotlin("test-js"))
                }
            }
        }
    }

    if (enableAnyIosTarget) {
        (this as ExtensionAware).extensions.configure<CocoapodsExtension> {
            summary = "Some description for a Kotlin/Native module"
            homepage = "Link to a Kotlin/Native module homepage"
        }
    }
}
