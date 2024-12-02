pluginManagement {
    includeBuild("build-logic/gradle-plugins")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}
rootProject.name = "pubnub-chat"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

includeBuild("build-logic/ktlint-custom-rules")

include("pubnub-chat-api")
include("pubnub-chat-impl")
include("pubnub-chat-test")
include("pubnub-3p-diff-match-patch")
