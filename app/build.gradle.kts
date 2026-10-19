plugins {
    id("com.android.application")
}

// The permanent release key is never stored in this repository. GitHub Actions decodes it
// from the AUTOSKIP_KEYSTORE_BASE64 secret into a temporary file and points these variables
// at it. Builds without them (your own computer, pull requests from forks) fall back to the
// usual debug key, so they still install, just not over an official release.
val releaseKeystore: String? = providers.environmentVariable("AUTOSKIP_KEYSTORE").orNull
val releaseKeystorePassword: String? = providers.environmentVariable("AUTOSKIP_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias: String = providers.environmentVariable("AUTOSKIP_KEY_ALIAS").orElse("autoskip").get()

android {
    namespace = "io.github.autoskip"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "io.github.autoskip"
        minSdk = 26          // Android 8.0
        targetSdk = 36       // Android 16
        // For a new release, raise both, then publish a release tagged "v" + versionName (see README).
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            if (releaseKeystorePassword.isNullOrEmpty()) {
                throw GradleException("AUTOSKIP_KEYSTORE is set but AUTOSKIP_KEYSTORE_PASSWORD is not.")
            }
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "pkcs12"
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            // Kept readable: stack traces matter when YouTube changes its UI.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
