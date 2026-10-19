// Test-only apps for the emulator test (.github/scripts/e2e.sh). Never shipped.
//
// Two flavours of one tiny app, installed under the package names of YouTube and YouTube
// Music so AutoSkip watches them like the real apps. Each plays fake ads with a Skip button
// and records exactly what got pressed. They only ever run on a throwaway CI emulator: they
// can't be installed on a phone that has the real apps, because the signatures differ.
plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.autoskip.fake"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "test"
    }

    flavorDimensions += "app"
    productFlavors {
        create("youtube") {
            dimension = "app"
            applicationId = "com.google.android.youtube"
        }
        create("music") {
            dimension = "app"
            applicationId = "com.google.android.apps.youtube.music"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
