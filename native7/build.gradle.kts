// AGP 9.x provides Kotlin compilation; do not also apply kotlin-android.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

