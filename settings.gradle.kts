pluginManagement {
    includeBuild("pubnub-kotlin/build-logic/gradle-plugins")
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
//        mavenLocal()
        mavenCentral()
    }
}

includeBuild("pubnub-kotlin") {
    name = "pubnub"
}

include(":pubnub-chat-api")
include(":pubnub-chat-impl")
