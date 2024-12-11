import com.pubnub.gradle.enableAnyIosTarget
import com.pubnub.gradle.enableJsTarget
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
    alias(libs.plugins.npm.publish)
}

nexusPublishing {
    repositories {
        sonatype()
    }
}

npmPublish {
    packages {
        getByName("js") {
            scope = "pubnub"
            packageName = "chat"
            types.set("index.d.ts")
            packageJsonTemplateFile = project.layout.projectDirectory.file("js-chat/package_template.json")
        }
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
            val appleMain by getting {
                dependencies {
                    api(project(":pubnub-chat-impl"))
                }
            }
        }

        val jvmMain by getting {
            dependencies {
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":pubnub-chat-test"))
            }
        }
    }

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
                generateTypeScriptDefinitions()
            }
            binaries.library()
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

apiValidation {
    ignoredProjects += "pubnub-chat-impl"
}
