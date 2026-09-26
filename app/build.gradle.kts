plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sherlock.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sherlock.android"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.16.2-android.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
