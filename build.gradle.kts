import com.pubnub.gradle.enableAnyIosTarget
import com.pubnub.gradle.tasks.GenerateVersionTask
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension

plugins {
    kotlin("multiplatform") version "2.0.0" apply false
    kotlin("plugin.serialization") version "2.0.0" apply false
    kotlin("native.cocoapods") version "2.0.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0" apply false
    id("org.jetbrains.kotlin.plugin.atomicfu") version "2.0.0"
    id("com.vanniktech.maven.publish") version "0.29.0" apply false
    id("org.jetbrains.dokka") version "1.9.20" apply false

    id("pubnub.shared")
    id("pubnub.dokka")
    id("pubnub.multiplatform")
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.16.2"
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":pubnub-chat-api"))
                implementation(project(":pubnub-chat-impl"))
            }
        }

        if (enableAnyIosTarget) {
            val iosMain by getting {
                dependencies {
                    api(project(":pubnub-chat-impl"))
                }
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.pubnub:pubnub-core-impl:9.2-DEV")
                implementation("com.pubnub:pubnub-kotlin-impl:9.2-DEV")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.pubnub:pubnub-kotlin-test")
            }
        }
    }

    if (enableAnyIosTarget) {
        (this as ExtensionAware).extensions.configure<CocoapodsExtension> {
            summary = "Some description for a Kotlin/Native module"
            homepage = "Link to a Kotlin/Native module homepage"

            framework {
                baseName = "PubNubChat"
                export(project(":pubnub-chat-api"))
                export(project(":pubnub-chat-impl"))
            }
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

apiValidation {
    ignoredProjects += "pubnub-chat-impl"
}
