// Android Gradle Plugin 9.x needs Gradle 9.x (pinned in gradle/wrapper/gradle-wrapper.properties).
// AGP 9 compiles Kotlin itself ("built-in Kotlin"), so there is no separate Kotlin plugin.
plugins {
    id("com.android.application") version "9.3.1" apply false
}
