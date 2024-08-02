import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.yarn

plugins {
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
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
    }
    jvm()

    listOf(
//        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
//        it.binaries {
//            framework {
//                baseName = "PubNubChat"
//                isStatic = true
//            }
//        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("com.pubnub:pubnub-core-api:9.2-DEV")
                api("com.pubnub:pubnub-kotlin-api:9.2-DEV")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.0-RC")
            }
        }

        val nonJvm by creating {
            dependsOn(commonMain)
        }
    }
}

yarn.yarnLockMismatchReport = YarnLockMismatchReport.WARNING
yarn.yarnLockAutoReplace = true
