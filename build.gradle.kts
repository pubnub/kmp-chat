import com.pubnub.gradle.enableAnyIosTarget
import com.pubnub.gradle.tasks.GenerateVersionTask
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.benmanes.versions) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.gradle.nexus.publish)
    alias(libs.plugins.kotlinx.atomicfu)

    id("pubnub.shared")
    id("pubnub.dokka")
    id("pubnub.multiplatform")
    alias(libs.plugins.kotlinx.compatibility.validator)
}

nexusPublishing {
    repositories {
        sonatype()
    }
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
