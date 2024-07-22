import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    kotlin("native.cocoapods") version "2.0.0"
    id("dev.mokkery") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.atomicfu") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

group = "com.pubnub"
version = "1.0-SNAPSHOT"

ktlint {
    outputToConsole.set(true)
    verbose.set(true)
    additionalEditorconfig.set(
        mapOf(
            "ij_kotlin_imports_layout" to "*,java.**,javax.**,kotlin.**,^",
            "indent_size" to "4",
            "ktlint_standard_multiline-expression-wrapping" to "disabled",
            "ktlint_standard_string-template-indent" to "disabled",
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_standard_if-else-wrapping" to "disabled",
            "ktlint_standard_discouraged-comment-location" to "disabled",
            "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
            "ktlint_standard_trailing-comma-on-call-site" to "disabled",
            "ktlint_standard_function-signature" to "disabled",
            "ktlint_standard_filename" to "disabled",
            "ktlint_standard_function-naming" to "disabled",
        )
    )
}

kotlin {
    jvmToolchain(8)
    js {
        useEsModules()
//        browser {
//            testTask {
// //                useMocha {
// //                    timeout = "30s"
// //                }
//                useKarma {
//                    useChrome()
//                }
//            }
//        }
        nodejs {
            testTask {
                useMocha {
                    timeout = "20s"
                }
            }
        }
//        binaries.executable()
    }
    jvm()

    listOf(
        iosArm64(),
 //        iosX64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries {
            framework {
                baseName = "PubNubChat"
                isStatic = true
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
//                implementation("com.pubnub:pubnub-core-api:9.2-DEV")
//                implementation("com.pubnub:pubnub-kotlin-api:9.2-DEV")
//                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
//                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0-RC")
//                implementation("com.benasher44:uuid:0.8.4")
// //                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0-RC")
//                implementation("org.jetbrains.kotlinx:atomicfu:0.24.0")
                api(project(":pubnub-chat-api"))
                implementation(project(":pubnub-chat-impl"))
            }
        }

        val iosMain by getting {
            dependencies {
                api(project(":pubnub-chat-impl"))
            }
        }
//
//        val commonTest by getting {
//            dependencies {
//                implementation(kotlin("test"))
//                implementation("com.pubnub:pubnub-kotlin-test")
//            }
//        }
//
//        val jvmMain by getting {
//            dependencies {
//                implementation("com.pubnub:pubnub-kotlin:9.2-DEV")
//                implementation(kotlin("test-junit"))
//            }
//        }
//
//        val nonJvm by creating {
//            dependsOn(commonMain)
//        }
//
//        val jsMain by getting {
//            dependsOn(nonJvm)
//        }
//
//        val jsTest by getting {
//            dependencies {
//                implementation(kotlin("test-js"))
//            }
//        }
    }

    cocoapods {
        ios.deploymentTarget = "14"

        // Required properties
        // Specify the required Pod version here. Otherwise, the Gradle project version is used.
        version = "1.0"
        summary = "Some description for a Kotlin/Native module"
        homepage = "Link to a Kotlin/Native module homepage"

        // Maps custom Xcode configuration to NativeBuildType
        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE

//        podfile = project.file(project.file("Sample Chat app/Podfile"))

        framework {
            // Required properties
            // Framework name configuration. Use this property instead of deprecated 'frameworkName'

            // Optional properties
            // Specify the framework linking type. It's dynamic by default.
            isStatic = true
            export(project(":pubnub-chat-api"))
            export(project(":pubnub-chat-impl"))
            transitiveExport = true
        }

        pod("PubNubSwift") {
//                        source = git("https://github.com/pubnub/swift") {
//                            branch = "feat/kmp"
//                        }
//            headers = "PubNub/PubNub.h"
            source = path(rootProject.file("pubnub-kotlin/swift"))
//            version = "7.1.0"

            moduleName = "PubNub"
            extraOpts += listOf("-compiler-option", "-fmodules")
        }

    }

//    cocoapods {
//        ios.deploymentTarget = "14"
//
//        // Required properties
//        // Specify the required Pod version here. Otherwise, the Gradle project version is used.
//        version = "1.0"
//        summary = "Some description for a Kotlin/Native module"
//        homepage = "Link to a Kotlin/Native module homepage"
//
//        // Optional properties
//        // Configure the Pod name here instead of changing the Gradle project name
//        name = "PubNubChat"
//
//        // Maps custom Xcode configuration to NativeBuildType
//        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
//        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
//
//        podfile = project.file(project.file("Sample Chat app/Podfile"))
//
//        framework {
//            // Required properties
//            // Framework name configuration. Use this property instead of deprecated 'frameworkName'
//            baseName = "PubNubChat"
//
//            // Optional properties
//            // Specify the framework linking type. It's dynamic by default.
//            isStatic = true
//        }
//
//        pod("PubNubSwift") {
//            source = git("https://github.com/pubnub/swift") {
//                branch = "feat/kmp"
//            }
// //            headers = "PubNub/PubNub.h"
// //            source = path(project.file("swift"))
// //            version = "7.1.0"
//
//            moduleName = "PubNub"
//            extraOpts += listOf("-compiler-option", "-fmodules")
//        }
//
// //        pod("PubNubSwift") {
// ////            headers = "PubNub/PubNub.h"
// //            source = git("https://github.com/pubnub/objective-c") {
// //                branch = "feat/kmp"
// //            }
// ////            source = path(project.file("swift"))
// //
// ////            version = "7.1.0"
// ////            version = "5.3.0"
// //            moduleName = "PubNub"
// //            extraOpts += listOf("-compiler-option", "-fmodules")
// //        }
//    }
}

yarn.yarnLockMismatchReport = YarnLockMismatchReport.WARNING
yarn.yarnLockAutoReplace = true
