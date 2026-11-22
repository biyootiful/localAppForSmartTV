plugins {
    id("com.android.application")
}

android {
    namespace = "local.tv.streamshell"
    compileSdk = 36

    defaultConfig {
        applicationId = "local.tv.streamshell"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    // One persistent local key, so every rebuild installs over the previous one. The key is
    // not in git; without it the build falls back to Android's default debug key, and the app
    // must be uninstalled from the TV once before a build signed with a different key installs.
    val localKey = rootProject.file("local.keystore")
    if (localKey.exists()) {
        signingConfigs {
            create("local") {
                storeFile = localKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        buildTypes {
            debug {
                signingConfig = signingConfigs.getByName("local")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Only dependency: document-start script injection and user-agent client hints for WebView.
    implementation("androidx.webkit:webkit:1.18.0")
}
