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