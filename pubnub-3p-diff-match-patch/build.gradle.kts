plugins {
    id("pubnub.base.multiplatform")
    alias(libs.plugins.vanniktech.maven.publish)
}

mavenPublishing {
    pom {
        name.set("Diff Match Patch KMP")
        description.set("A pure Kotlin port of the Diff Match Patch Java library.")
    }
}

kotlin {
    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}