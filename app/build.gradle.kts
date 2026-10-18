plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.autoskip"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "io.github.autoskip"
        minSdk = 26          // Android 8.0
        targetSdk = 36       // Android 16
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // Kept readable: stack traces matter when YouTube changes its UI.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// No AndroidX or other libraries: everything used is part of Android itself.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
