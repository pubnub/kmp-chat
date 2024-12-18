@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.pubnub.gradle.enableAnyIosTarget
import com.pubnub.gradle.enableJsTarget
import com.pubnub.gradle.tasks.GenerateVersionTask
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.atomicfu)
    id("pubnub.ios-simulator-test")
    id("pubnub.shared")
    id("pubnub.dokka")
    id("pubnub.multiplatform")
    alias(libs.plugins.mokkery)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":pubnub-chat-api"))
                implementation(project(":pubnub-3p-diff-match-patch"))
                implementation(libs.pubnub.kotlin.api)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.atomicfu)
                implementation(libs.touchlab.kermit)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":pubnub-chat-test"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.pubnub.kotlin)
                implementation(kotlin("test-junit"))
            }
        }

        if (enableJsTarget) {
            val jsTest by getting {
                dependencies {
                    implementation(kotlin("test-js"))
                    implementation(npm("pubnub", "8.4.0"))
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

val generateVersion =
    tasks.register<GenerateVersionTask>("generateVersion") {
        fileName.set("ChatVersion")
        packageName.set("com.pubnub.chat.internal")
        constName.set("PUBNUB_CHAT_VERSION")
        version.set(providers.gradleProperty("VERSION_NAME"))
        outputDirectory.set(
            layout.buildDirectory.map {
                it.dir("generated/sources/generateVersion")
            },
        )
    }

kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateVersion)
