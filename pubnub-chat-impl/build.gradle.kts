@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.pubnub.gradle.enableAnyIosTarget
import com.pubnub.gradle.enableJsTarget
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
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
    if (enableJsTarget) {
        js {
// keep this in here for ad-hoc testing
//            browser {
//                testTask {
//                    useMocha {
//                        timeout = "15s"
//                    }
//                }
//            }

            compilerOptions {
                target.set("es2015")
                moduleKind.set(JsModuleKind.MODULE_UMD)
            }
            binaries.library()
        }
    }

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
                implementation(libs.pubnub.kotlin.test)
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
