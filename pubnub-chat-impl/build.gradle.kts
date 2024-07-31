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
    id("pubnub.ios-simulator-test")
    id("pubnub.shared")
    id("pubnub.dokka")
    id("pubnub.multiplatform")
}

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

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }

    cocoapods {
        summary = "Some description for a Kotlin/Native module"
        homepage = "Link to a Kotlin/Native module homepage"
    }
}
