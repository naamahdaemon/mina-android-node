buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        // Explicitly align AGP's built-in Kotlin compiler with the Compose plugin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
