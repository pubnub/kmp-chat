plugins {
//    kotlin("multiplatform") version "2.0.0-RC1"
//    kotlin("native.cocoapods") version "2.0.0-RC1"
    kotlin("multiplatform") version "1.9.23"
    kotlin("native.cocoapods") version "1.9.23"
}

group = "com.pubnub"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
//    testImplementation(kotlin("test"))
}

//tasks.test {
//    useJUnitPlatform()
//}
kotlin {
    jvmToolchain(17)
    js {
        browser {

        }
//        nodejs()
        binaries.executable()
    }
    jvm()
    iosArm64 {
        binaries {
            framework {
                baseName = "PubNubChat"
            }
        }
    }

    sourceSets {
        sourceSets {
            val jvmMain by getting {
                dependencies {
                    implementation("com.pubnub:pubnub-kotlin-impl:9.1.0")
                    implementation("com.pubnub:pubnub-kotlin:9.1.0")
                }
            }
        }
    }

    cocoapods {
        ios.deploymentTarget = "14"

        pod("PubNub") {
            headers = "PubNub/PubNub.h"
//            source = git("https://github.com/pubnub/objective-c")
//            version = "7.1.0"
//            version = "5.3.0"
//            moduleName = "PubNub"
//            extraOpts += listOf("-compiler-option", "-fmodules")
        }
    }
}
