import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    kotlin("multiplatform") version "2.0.0-RC3"
//    kotlin("native.cocoapods") version "2.0.0-RC3"
//    kotlin("multiplatform") version "1.9.22" //downgrade to 1.9.22 because mockmp uses Kotlin Symbol Processing (KSP) "com.google.devtools.ksp:symbol-processing-api:<new-version>"
//    kotlin("native.cocoapods") version "1.9.23"
//    id("org.kodein.mock.mockmp") version "1.17.0"
    id("dev.mokkery") version "2.0.0-RC1"
}

group = "com.pubnub"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(8)
    js {
        browser {

        }
//        nodejs()
        binaries.executable()
    }
    jvm()

//    listOf(
//        iosArm64(),
////        iosX64(),
//        iosSimulatorArm64(),
//    ).forEach {
//        it.binaries {
//            framework {
//                baseName = "PubNubChat"
//                isStatic = true
//            }
//        }
//    }

//        it.compilations.getByName("main") {
//            val myInterop by cinterops.creating {
//                // Def-file describing the native API.
//                // The default path is src/nativeInterop/cinterop/<interop-name>.def
//                defFile(project.file("def-file.def"))
//
//                // Package to place the Kotlin API generated.
////                packageName("objectivec.pubnub")
//
//                // Options to be passed to compiler by cinterop tool.
//                compilerOpts("-I/Users/wojciech.kalicinski/Library/Developer/Xcode/DerivedData/PubNub-gukbfwdrkubkmtgvokmytmdgrvzf/Build/Products/Debug-iphonesimulator/PubNub.framework/Headers")
//
//                // Directories to look for headers.
////                includeDirs.apply {
////                    // Directories for header search (an equivalent of the -I<path> compiler option).
////                    allHeaders("path1")
////
////                    // Additional directories to search headers listed in the 'headerFilter' def-file option.
////                    // -headerFilterAdditionalSearchPrefix command line option equivalent.
//////                    headerFilterOnly("path1", "path2")
////                }
//                // A shortcut for includeDirs.allHeaders.
////                includeDirs("include/directory", "another/directory")
//            }
//        }
//    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.pubnub:pubnub-core-api:9.2-DEV")
                implementation("com.pubnub:pubnub-kotlin-api:9.2-DEV")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
//                implementation("com.pubnub:pubnub-kotlin-test:9.2-DEV")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.pubnub:pubnub-kotlin:9.2-DEV")
                implementation(kotlin("test-junit"))
            }
        }

        val nonJvm by creating {
            dependsOn(commonMain)
        }

        val jsMain by getting {
            dependsOn(nonJvm)
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
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
////            headers = "PubNub/PubNub.h"
////            source = path(project.file("swift"))
////            version = "7.1.0"
//
//            moduleName = "PubNub"
//            extraOpts += listOf("-compiler-option", "-fmodules")
//        }
//
////        pod("PubNubSwift") {
//////            headers = "PubNub/PubNub.h"
////            source = git("https://github.com/pubnub/objective-c") {
////                branch = "feat/kmp"
////            }
//////            source = path(project.file("swift"))
////
//////            version = "7.1.0"
//////            version = "5.3.0"
////            moduleName = "PubNub"
////            extraOpts += listOf("-compiler-option", "-fmodules")
////        }
//    }
}
